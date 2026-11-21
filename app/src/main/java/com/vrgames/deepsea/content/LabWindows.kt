package com.vrgames.deepsea.content

import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pico.spatial.core.ecs.DirectionalLightComponent
import com.pico.spatial.core.ecs.Entity
import com.pico.spatial.core.ecs.LoadType
import com.pico.spatial.core.ecs.TransformComponent
import com.pico.spatial.core.ecs.ViewCoordinateSpace
import com.pico.spatial.core.ecs.event.SceneEvents
import com.pico.spatial.core.ecs.resource.TextureCreateOption
import com.pico.spatial.core.ecs.resource.TextureResource
import com.pico.spatial.core.lifecycle.Cancellable
import com.pico.spatial.core.math.EulerAngles
import com.pico.spatial.core.math.Vector3
import com.pico.spatial.ui.design.Text
import com.pico.spatial.ui.foundation.content.SpatialView
import com.pico.spatial.ui.foundation.content.toColor4
import com.pico.spatial.ui.foundation.gesture.TargetEntity
import com.pico.spatial.ui.foundation.gesture.detectSpatialDragGesture
import com.pico.spatial.ui.foundation.gesture.detectSpatialTapGesture
import com.pico.spatial.ui.platform.LengthUnit
import com.pico.spatial.ui.platform.LocalPhysicalLengthConverter
import com.pico.spatial.ui.platform.containers.LocalSpatialNavigator
import com.vrgames.deepsea.ALBUM_ID
import com.vrgames.deepsea.DIVE_ID
import com.vrgames.deepsea.GUIDE_ID
import com.vrgames.deepsea.LEVELS_ID
import com.vrgames.deepsea.TANK_ID
import com.vrgames.deepsea.game.AutoPilot
import com.vrgames.deepsea.game.Game
import com.vrgames.deepsea.render.Music
import com.vrgames.deepsea.render.SeaView
import com.vrgames.deepsea.render.Sound
import com.vrgames.deepsea.render.setPos
import com.vrgames.deepsea.render.setScale
import com.vrgames.deepsea.render.shared
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.min

private const val TAG = "DeepLab"

/** Runs [action] each time [count] goes up while this window is up — not for the value it had when it opened. */
@Composable
fun OnBump(count: Int, action: suspend () -> Unit) {
    val start = remember { count }
    LaunchedEffect(count) { if (count != start) action() }
}

/**
 * v0.5: the 潜航控制台 — the app's main window in Shared Space. The level's card, the dive's gauges, the photos and the
 * notes (the panels the stage used to hang round the tank), the way into the deep. It opens the tank beside itself.
 */
@Composable
fun ConsoleWindow() {
    ClosesWithQuit(com.vrgames.deepsea.CONSOLE_ID)
    val context = LocalContext.current
    val navigator = LocalSpatialNavigator.current
    val c = LocalPhysicalLengthConverter.current
    val scope = rememberCoroutineScope()
    val appVersion = remember { runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: "" }
    val toggleWindow: (String) -> Unit = { id ->
        val shown = Game.window
        if (shown != null) Game.closeWindow(shown)
        if (shown != id) { Game.windowShown(id); runCatching { navigator.openWindowContainer(id) } }
    }
    DisposableEffect(Unit) {
        Game.init(context)
        Game.closeWindow = { id -> runCatching { navigator.closeWindowContainer(id) }; Game.windowGone(id) }
        if (Game.play == null) { Game.intro(com.vrgames.deepsea.core.Stages.ALL[0]); Game.toCard() }
        Game.consoleOpen = true
        Game.consoleMinimized = false
        Game.minimizeConsole = { runCatching { navigator.minimizeWindowContainer() }.let { Log.i(TAG, "minimize console: ${it.getOrNull() ?: it.exceptionOrNull()?.message}"); (it.getOrNull() == true).also { ok -> if (ok) Game.consoleMinimized = true } } }
        Game.restoreConsole = { runCatching { navigator.restoreWindowContainer() }.let { Log.i(TAG, "restore console: ${it.getOrNull() ?: it.exceptionOrNull()?.message}"); (it.getOrNull() == true).also { ok -> if (ok) Game.consoleMinimized = false } } }
        Game.windowOpeners[com.vrgames.deepsea.CONSOLE_ID] = { id -> navigator.openWindowContainer(id) }
        onDispose {
            Game.closeWindow(LEVELS_ID); Game.closeWindow(GUIDE_ID); Game.closeWindow(ALBUM_ID)
            Game.consoleOpen = false
            Game.consoleMinimized = false
            Game.windowOpeners.remove(com.vrgames.deepsea.CONSOLE_ID)
            Game.minimizeConsole = { false }; Game.restoreConsole = { false }
            // v0.5.1 (device 2026-11-19: no way to leave; each window had to be closed by hand): the console closed with its
            // own close button ends the app, every window with it.
            if (!Game.quitting && !Game.diving) Game.quitApp("console closed")
        }
    }
    val lifecycle = androidx.compose.ui.platform.LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val music = Music(context).also { it.muted = !Game.musicOn; Game.music = it }
        val observer = androidx.lifecycle.LifecycleEventObserver { _, e ->
            if (e == androidx.lifecycle.Lifecycle.Event.ON_RESUME) music.resume() else if (e == androidx.lifecycle.Lifecycle.Event.ON_PAUSE) music.pause()
            if (e == androidx.lifecycle.Lifecycle.Event.ON_STOP && !Game.diving) Game.stopNarration()
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer); music.release(); if (Game.music === music) Game.music = null }
    }
    // Into the cabin (Full Space). v0.5.1 (device 2026-11-19: the console covered the view in the cabin): once the cabin
    // is open the console folds away (the cabin's own console has 「打开控制台」); it comes back outside.
    // v0.5.2 (device 2026-11-19: in the cabin the tank must be on the right; the window cannot be moved): the tank window
    // folds away too — the cabin has its own tank on the right — and comes back outside (closed and opened again if it
    // will not fold).
    OnBump(Game.diveRequest) {
        val r = runCatching { navigator.openStage(DIVE_ID, com.pico.spatial.ui.platform.containers.StageStyle.Full, null, com.pico.spatial.ui.platform.ability.UpperLimbRenderMode.values().first()) }
        Log.i(TAG, "into the cabin: ${r.getOrNull() ?: r.exceptionOrNull()?.message}")
        // Wait for the cabin to be up (it can take seconds), then fold the console; try a few times.
        var waited = 0L
        var tries = 0
        var folded = false
        while (waited < 12_000L && tries < 6) {
            kotlinx.coroutines.delay(400L); waited += 400L
            if (!Game.diving) continue
            if (Game.minimizeConsole()) { folded = true; break }
            tries++
        }
        if (!Game.diving) Log.i(TAG, "cabin not up after ${waited} ms: console and tank left as they are")
        else {
            if (!folded) Log.i(TAG, "console would not minimize after $tries tries (the cabin's 收起控制台 can try again)")
            Game.foldTank()
        }
    }
    LaunchedEffect(Game.debugOpen) {
        when (Game.debugOpen ?: return@LaunchedEffect) {
            "levels" -> if (Game.window != LEVELS_ID) toggleWindow(LEVELS_ID)
            "guide" -> if (Game.window != GUIDE_ID) toggleWindow(GUIDE_ID)
            "album" -> if (Game.window != ALBUM_ID) toggleWindow(ALBUM_ID)
            "menu" -> Game.menuOpen = true
            "close" -> Game.window?.let { Game.closeWindow(it) }
        }
        Game.debugOpen = null
    }
    LaunchedEffect(Game.noteVersion) {
        val n = Game.note ?: return@LaunchedEffect
        kotlinx.coroutines.delay(if (n.length > 30) 7000 else 3500)
        if (Game.note == n) Game.note = null
    }
    @Suppress("UNUSED_VARIABLE") val v = Game.version
    Column(
        Modifier.fillMaxSize().background(Color(0xF20A1A26)).padding(18.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp), horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        QuitRow()
        Text("水缸就在正前方。捏水里一处，潜水器开过去；捏一只动物，靠近拍照。捏住潜水器往自己这边一拉，就进入舱内视角（全沉浸）。", fontSize = 16.sp, color = Color(0xFFB9D7E2))
        when (Game.phase) {
            Game.Phase.CARD -> CardPanel(c)
            Game.Phase.INTRO -> IntroPanel(c)
            Game.Phase.RESULT -> ResultPanel(c, toggleWindow)
            Game.Phase.SHOW -> ShowPanel(c)
            Game.Phase.PLAYING -> {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    HudPanel(c, toggleWindow)
                    if (Game.meeting != null) MeetingPanel(c)
                }
                PhotosPanel(c)
            }
        }
        if (Game.note != null && !Game.diving) NotePanel(c)
        // In the cabin the tank window may be closed on purpose (v0.5.2): it opens again outside by itself.
        if (!Game.tankOpen && !Game.diving) CallToAction(c, "打开水缸") { runCatching { navigator.openWindowContainer(TANK_ID) } }
        Game.viewing?.let { PhotoViewer(c, it) }
        MenuPanel(c, toggleWindow, faceMe = {}, quit = { Game.quitAll(); (context as? android.app.Activity)?.finishAndRemoveTask() }, version = appVersion)
    }
}

/**
 * v0.5: the glass tank in a volume of its own in Shared Space, beside the console: the same sea as ever, pinched the same
 * way. The sub pinched and pulled toward oneself (8 cm or more) dives — into the deep all round, in Full Space.
 */
@Composable
fun TankWindow() {
    ClosesWithQuit(com.vrgames.deepsea.TANK_ID)
    val context = LocalContext.current
    val converter = LocalPhysicalLengthConverter.current
    val navigator = LocalSpatialNavigator.current
    val main = remember { Handler(Looper.getMainLooper()) }
    var view by remember { mutableStateOf<SeaView?>(null) }
    val sub = remember { arrayOfNulls<Cancellable>(1) }
    val pulling = remember { booleanArrayOf(false) }
    val pulled = remember { doubleArrayOf(0.0) }
    DisposableEffect(Unit) {
        Game.init(context)
        Game.tankOpen = true
        Game.tankMinimized = false
        // Open again (after it was closed for the cabin): it is back where it belongs.
        if (Game.tankClosedForCabin) { Game.tankClosedForCabin = false; Log.i(TAG, "tank open again after the cabin") }
        // v0.5.2: folded away in the cabin like the console, back outside (Game.foldTank, Game.afterDive).
        Game.minimizeTank = { runCatching { navigator.minimizeWindowContainer() }.let { Log.i(TAG, "minimize tank: ${it.getOrNull() ?: it.exceptionOrNull()?.message}"); it.getOrNull() == true } }
        Game.restoreTank = { runCatching { navigator.restoreWindowContainer() }.let { Log.i(TAG, "restore tank: ${it.getOrNull() ?: it.exceptionOrNull()?.message}"); it.getOrNull() == true } }
        Game.closeTankForCabin = { Log.i(TAG, "close tank for the cabin: ${runCatching { navigator.closeWindowContainer() }.exceptionOrNull()?.message ?: "ok"}") }
        Game.windowOpeners[com.vrgames.deepsea.TANK_ID] = { id -> navigator.openWindowContainer(id) }
        onDispose {
            sub[0]?.cancel(); Game.tankOpen = false; Game.tankMinimized = false; if (Game.view === view) Game.view = null
            Game.windowOpeners.remove(com.vrgames.deepsea.TANK_ID)
            Game.minimizeTank = { false }; Game.restoreTank = { false }; Game.closeTankForCabin = {}
            // The tank closed with its own close button: the app ends, every window with it (device 2026-11-19). Not when
            // it was closed for the cabin (v0.5.2: it would not minimize; it opens again outside).
            if (!Game.quitting && !Game.diving && !Game.tankClosedForCabin) Game.quitApp("tank closed")
        }
    }
    LaunchedEffect(Game.playVersion, view) {
        val v = view ?: return@LaunchedEffect
        val p = Game.play ?: return@LaunchedEffect
        synchronized(Game.lock) { v.build(p); v.receiveAll() }
    }
    // Back from the deep: this view is the one the game shows things in again.
    LaunchedEffect(Game.diving, view) { val v = view ?: return@LaunchedEffect; if (!Game.diving) { Game.view = v; Game.sound = v.sound } }
    SpatialView(
        Modifier.fillMaxSize().pointerInput(1) {
            detectSpatialTapGesture(context, TargetEntity.any()) { value ->
                val v = view ?: return@detectSpatialTapGesture
                if (v.isSub(value.targetEntity)) { main.post { Game.say("捏住潜水器，往自己这边一拉，就进入舱内视角（全沉浸）。"); Game.version++ }; return@detectSpatialTapGesture }
                val t = v.target(value.targetEntity) ?: return@detectSpatialTapGesture
                main.post { Game.tap(t) }
            }
        }.pointerInput(2) {
            fun m(px: Float) = with(density) { converter.dpToLength(px.toDp(), LengthUnit.Meters) }.toDouble()
            detectSpatialDragGesture(
                context = context,
                targetedToEntity = TargetEntity.any(),
                onDragStart = { _, value ->
                    val v = view
                    pulling[0] = v != null && v.isSub(value.targetEntity)
                    pulled[0] = 0.0
                    if (!pulling[0] && v != null) v.target(value.targetEntity)?.let { t -> main.post { Game.tap(t) } }
                },
                onDragEnd = { pulling[0] = false },
                onDragCancel = { pulling[0] = false },
            ) { value ->
                if (!pulling[0]) return@detectSpatialDragGesture
                // Toward the player: +z out of the window.
                pulled[0] += m(value.dragAmount.z)
                if (pulled[0] > PULL_M) {
                    pulling[0] = false
                    Log.i(TAG, "sub pulled out: into the cabin")
                    main.post { Game.toggleCockpit() }
                }
            }
        },
    ) { content, _ ->
        content.addEntity(Entity().apply {
            components.set(DirectionalLightComponent(Color(0xFFFFE9D0).toColor4(), 1000f, false))
            components[TransformComponent::class.java]?.setEulerAngles(EulerAngles(-65f, 15f, 0f))
        })
        val tex = HashMap<String, TextureResource>()
        var ibl: TextureResource? = null
        withContext(Dispatchers.IO) {
            runCatching { TextureResource.load("textures/sand.jpg", LoadType.FROM_ASSETS, TextureCreateOption()).shared() }.onSuccess { tex["sand"] = it }
            ibl = runCatching { TextureResource.load("textures/ibl_lab.ktx", LoadType.FROM_ASSETS, TextureCreateOption().apply { textureEncoding = com.pico.spatial.core.ecs.resource.TextureEncoding.LINEAR }).shared() }.getOrNull()
        }
        val models = HashMap<String, Entity>()
        for (name in SEA_MODELS) runCatching { Entity.loadSuspend("asset://models/$name.glb") }.onSuccess { models[name] = it }
        val v = SeaView(models, tex)
        v.windowMode()
        ibl?.let { v.useIbl(it) }
        content.addEntity(v.root)
        // The tank (0.92 wide, 1.04 high, 0.3 deep) as large as the volume allows, standing on its floor.
        val corner = content.convertPosition(Vector3.ZERO, ViewCoordinateSpace.Local, content.localSpatialCoordinateSpace)
        val hw = abs(corner.x); val hh = abs(corner.y); val hd = abs(corner.z)
        val s = min(if (hw > 0.05f) hw * 2 / 1.0f else 0.7f, min(if (hh > 0.05f) hh * 2 / 1.12f else 0.7f, if (hd > 0.05f) hd * 2 / 0.36f else 0.7f)) * 0.96f
        v.root.setScale(s)
        v.root.setPos(Vector3(0f, -(if (hh > 0.05f) hh else 0.4f) - SeaView.BASE * s + 0.01f, 0f))
        v.sound = Sound(v.sea).also { it.load() }
        // v0.5.2: in the cabin the game shows its flashes in the cabin's views, not in this (folded) window.
        if (!Game.diving) { Game.view = v; Game.sound = v.sound }
        view = v
        Game.play?.let { synchronized(Game.lock) { v.build(it); v.receiveAll() } }
        AutoPilot.ready()
        Log.i(TAG, "tank in its volume at scale $s")
        sub[0] = content.subscribe(SceneEvents.Update::class.java) { e ->
            val dt = e.deltaTime.toDouble().coerceIn(0.0, 0.1)
            // The deep ticks the game while it is open (this window may be paused then).
            if (!Game.diving) main.post { Game.tick(dt) }
            synchronized(Game.lock) { Game.play?.let { v.sync(it, dt) } }
        }
    }
}

/** A pull of the sub toward oneself this long dives (metres). */
private const val PULL_M = 0.08
val SEA_MODELS = listOf("sub", "angler", "jelly", "squid", "turtle", "manta", "lantern", "wreck", "vent", "tube", "whale", "coral", "chest", "trophy")


/**
 * v0.5.1: the app's first window, in front of the player. It opens the tank (which takes its place, straight ahead) and the
 * console to the tank's left, then closes itself (device 2026-11-19: the tank belongs in the middle; the system always puts
 * an app's first window there, so the first window is this one and not the console). Opened again from the launcher, it
 * brings back whatever is missing.
 */
@Composable
fun StartWindow() {
    ClosesWithQuit(com.vrgames.deepsea.START_ID)
    val context = LocalContext.current
    val navigator = LocalSpatialNavigator.current
    DisposableEffect(Unit) { Game.init(context); onDispose { } }
    LaunchedEffect(Unit) {
        Game.reopened()
        kotlinx.coroutines.delay(400)
        if (!Game.tankOpen) Log.i(TAG, "start: open tank ${runCatching { navigator.openWindowContainer(TANK_ID) }.exceptionOrNull()?.message ?: "ok"}")
        kotlinx.coroutines.delay(1200)
        if (!Game.consoleOpen) Log.i(TAG, "start: open console ${runCatching { navigator.openWindowContainer(com.vrgames.deepsea.CONSOLE_ID) }.exceptionOrNull()?.message ?: "ok"}")
        kotlinx.coroutines.delay(1500)
        Log.i(TAG, "start: close the start window ${runCatching { navigator.closeWindowContainer() }.let { it.getOrNull() ?: it.exceptionOrNull()?.message }}")
    }
    Column(Modifier.fillMaxSize().background(Color(0xF20A1A26)).padding(24.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Text("深海潜航", fontSize = 40.sp, color = Color(0xFFFFD27A))
        Text("正在打开水缸和潜航控制台……", fontSize = 20.sp, color = Color(0xFFB9D7E2))
    }
}

/** The console's top row: the title and a 退出 that asks once, in place (no dialog). */
@Composable
private fun QuitRow() {
    var asking by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text("深海潜航 · 潜航控制台", fontSize = 28.sp, color = Color(0xFFFFD27A))
        androidx.compose.foundation.layout.Spacer(Modifier.weight(1f))
        if (!asking) {
            QuitButton("退出深海潜航", Color(0xFF7A1E14)) { asking = true }
        } else {
            Text("关掉所有窗口？", fontSize = 18.sp, color = Color(0xFFFFE3B0))
            androidx.compose.foundation.layout.Spacer(Modifier.width(8.dp))
            QuitButton("确定退出", Color(0xFFB3361A)) { Game.quitApp("退出") }
            androidx.compose.foundation.layout.Spacer(Modifier.width(8.dp))
            QuitButton("取消", Color(0xFF24485C)) { asking = false }
        }
    }
}

@Composable
private fun QuitButton(text: String, fill: Color, onClick: () -> Unit) {
    val shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp)
    Text(
        text,
        Modifier.clip(shape).background(fill).border(2.dp, Color(0xFFFFD27A), shape)
            .clickable { onClick() }.padding(horizontal = 16.dp, vertical = 8.dp),
        fontSize = 18.sp, color = Color(0xFFFFF4D6),
    )
}

/** A photo from the 照片屏, large: the picture, what it is, its story, its voice again (device 2026-11-19: could not be viewed). */
@Composable
private fun PhotoViewer(c: com.pico.spatial.ui.platform.PhysicalLengthConverter, id: String) {
    val context = LocalContext.current
    val sp = runCatching { com.vrgames.deepsea.core.Sea.species(id) }.getOrNull() ?: return
    val pic = remember(id) { runCatching { context.assets.open("thumbs/$id.png").use { android.graphics.BitmapFactory.decodeStream(it)?.asImageBitmap() } }.getOrNull() }
    val shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp)
    Column(
        Modifier.fillMaxWidth().clip(shape).background(Color(0xFF10293A)).border(2.dp, Color(0xFFFFD27A), shape).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp), horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("照片：${sp.name}", fontSize = 26.sp, color = Color(0xFFFFD27A))
        pic?.let { androidx.compose.foundation.Image(it, sp.name, Modifier.width(420.dp).height(420.dp).clip(androidx.compose.foundation.shape.RoundedCornerShape(12.dp))) }
        Text(sp.fact, fontSize = 18.sp, color = Color(0xFFE6F2F7))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            QuitButton("再听一遍", Color(0xFF24485C)) { Game.speak(id) }
            QuitButton("关闭照片", Color(0xFF7A1E14)) { Game.viewing = null }
        }
    }
}

/** v0.5.1: registers how this window (or the cabin, [stage]) closes itself, so that 退出 can close each one (Game.quitApp). */
@Composable
internal fun ClosesWithQuit(id: String, stage: Boolean = false) {
    val context = LocalContext.current
    val navigator = LocalSpatialNavigator.current
    DisposableEffect(id) {
        var c: android.content.Context? = context
        while (c is android.content.ContextWrapper && c !is android.app.Activity) c = c.baseContext
        val own = c as? android.app.Activity
        Game.windowClosers[id] = {
            if (stage) kotlinx.coroutines.MainScope().launch { runCatching { navigator.closeStage() } }
            else runCatching { navigator.closeWindowContainer(id) }
            if (!stage) own?.let { runCatching { it.finishAndRemoveTask() } }
        }
        onDispose { Game.windowClosers.remove(id) }
    }
}
