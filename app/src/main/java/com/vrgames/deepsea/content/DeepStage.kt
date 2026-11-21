package com.vrgames.deepsea.content

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import com.pico.spatial.core.ecs.DirectionalLightComponent
import com.pico.spatial.core.ecs.Entity
import com.pico.spatial.core.ecs.LoadType
import com.pico.spatial.core.ecs.TransformComponent
import com.pico.spatial.core.ecs.event.SceneEvents
import com.pico.spatial.core.ecs.resource.MaterialCullingMode
import com.pico.spatial.core.ecs.resource.TextureCreateOption
import com.pico.spatial.core.ecs.resource.TextureEncoding
import com.pico.spatial.core.ecs.resource.TextureResource
import com.pico.spatial.core.ecs.resource.UnlitMaterial
import com.pico.spatial.core.lifecycle.Cancellable
import com.pico.spatial.core.math.EulerAngles
import com.pico.spatial.core.math.Quat
import com.pico.spatial.core.math.Vector3
import com.pico.spatial.tracking.DataProvider
import com.pico.spatial.tracking.hmd.HMDTrackingProvider
import com.pico.spatial.ui.foundation.content.SpatialView
import com.pico.spatial.ui.foundation.content.SpatialViewAttachments
import com.pico.spatial.ui.foundation.content.toColor4
import com.pico.spatial.ui.foundation.gesture.TargetEntity
import com.pico.spatial.ui.foundation.gesture.detectSpatialTapGesture
import com.pico.spatial.ui.foundation.gesture.detectSpatialDragGesture
import com.pico.spatial.ui.platform.LocalPhysicalLengthConverter
import com.pico.spatial.ui.platform.containers.LocalSpatialNavigator
import com.vrgames.deepsea.ALBUM_ID
import com.vrgames.deepsea.GUIDE_ID
import com.vrgames.deepsea.LEVELS_ID
import com.vrgames.deepsea.core.CabinLayout
import com.vrgames.deepsea.core.CabinMotion
import com.vrgames.deepsea.core.Dive
import com.vrgames.deepsea.core.EyeHeight
import com.vrgames.deepsea.game.AutoPilot
import com.vrgames.deepsea.game.Game
import com.vrgames.deepsea.render.Music
import com.vrgames.deepsea.render.Sound
import com.vrgames.deepsea.render.Cockpit
import com.vrgames.deepsea.render.Motor
import com.vrgames.deepsea.render.OpenSea
import com.vrgames.deepsea.render.SeaView
import com.vrgames.deepsea.render.color
import com.vrgames.deepsea.render.model
import com.vrgames.deepsea.render.setPos
import com.vrgames.deepsea.render.setScale
import com.vrgames.deepsea.render.shared
import com.vrgames.deepsea.render.transform
import com.vrgames.deepsea.render.unlit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

private const val TAG = "DeepStage"

/**
 * v0.5 (PICO 1118): the deep, in Full Space — the cabin of the sub with the sea all round (the tank's sea grown large, and
 * the open sea beyond it: light shafts, a fish school, bubbles, the floor). Opened from the lab in Shared Space (the 潜航
 * 控制台's 潜入深海, or the sub pulled out of the tank); the hatch handle overhead pulled down (or 浮出水面) closes it.
 *
 * v0.5.2 (device 2026-11-19): the cabin has its own tank on the right (the tank window cannot move, so it folds away); the
 * console's controls lie flat on its deck, labelled; the sea moves past as the sub does (specks, shafts, rocks, schools,
 * the motor's hum, the sea drawing in as it speeds up); Home in the cabin ends the app; the eye height is measured once.
 */
@Composable
fun DeepStage() {
    ClosesWithQuit(com.vrgames.deepsea.DIVE_ID, stage = true)
    val context = LocalContext.current
    val converter = LocalPhysicalLengthConverter.current
    val navigator = LocalSpatialNavigator.current
    val main = remember { Handler(Looper.getMainLooper()) }
    val head = remember { HMDTrackingProvider() }
    val subscription = remember { arrayOfNulls<Cancellable>(1) }
    var view by remember { mutableStateOf<SeaView?>(null) }
    val seat = remember { Seat() }
    val cockpitRef = remember { arrayOfNulls<Cockpit>(1) }
    val openSea = remember { arrayOfNulls<OpenSea>(1) }
    val tankRef = remember { arrayOfNulls<SeaView>(1) }
    val motor = remember { arrayOfNulls<Motor>(1) }
    val motion = remember { CabinMotion() }
    val panels = remember { HashMap<String, Entity>() }
    val hatchPull = remember { doubleArrayOf(-1.0) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val appVersion = remember { runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: "" }
    // The console's copy in the cabin opens 关卡 / 图鉴 / 说明 the way the console does.
    val toggleWindow: (String) -> Unit = { id ->
        val shown = Game.window
        if (shown != null) Game.closeWindow(shown)
        if (shown != id) { Game.windowShown(id); Game.cabinWindowAskAt = SystemClock.uptimeMillis(); runCatching { navigator.openWindowContainer(id) } }
    }
    // v0.5.1 (device 2026-11-19: 浮出水面 sometimes did nothing): the buttons and the hatch call this directly (Game.surfaceNow),
    // not through a Compose effect; it asks the console and the tank back first (a restore is taken best before the stage
    // closes). v0.5.2: [Game.leavingCabin] marks this close as meant until the stage is gone (any other end quits).
    val surfacing = remember { booleanArrayOf(false) }
    val surface: () -> Unit = {
        if (!surfacing[0]) {
            surfacing[0] = true
            Game.leavingCabin = true
            Game.cabinConsoleCopy = false
            Game.restoreConsole()
            if (Game.tankOpen && !Game.tankClosedForCabin) Game.restoreTank()
            scope.launch {
                val r = runCatching { navigator.closeStage() }
                Log.i(TAG, "back outside: ${r.exceptionOrNull()?.message ?: "stage closing"}")
                // Refused (not just cut short by the cabin going away): the cabin stays, so a later stop is Home again.
                if (r.isFailure && r.exceptionOrNull() !is kotlinx.coroutines.CancellationException) Game.leavingCabin = false
                kotlinx.coroutines.delay(1500)
                surfacing[0] = false
            }
        }
    }

    DisposableEffect(Unit) {
        Game.init(context)
        Game.diving = true
        Game.cockpit = true
        Game.surfaceNow = surface
        Game.windowOpeners[com.vrgames.deepsea.DIVE_ID] = { id -> Game.cabinWindowAskAt = SystemClock.uptimeMillis(); navigator.openWindowContainer(id) }
        Game.debugReplace = { z -> seat.remeasure(z) }
        Game.version++
        val hd = runCatching { head.start() }
        Log.i(TAG, "dive: head ${hd.getOrNull() ?: hd.exceptionOrNull()?.message}")
        onDispose {
            runCatching { head.stop() }
            subscription[0]?.cancel()
            Game.windowOpeners.remove(com.vrgames.deepsea.DIVE_ID)
            Game.diving = false
            Game.cockpit = false
            if (Game.surfaceNow === surface) Game.surfaceNow = null
            if (Game.cabinTank === tankRef[0]) Game.cabinTank = null
            Game.debugReplace = { Log.i(TAG, "replace: not in the cabin") }
            Game.version++
            // v0.5.2 (device 2026-11-19: Home in the cabin left the tank open): gone without being asked to come up (not
            // 回到舱外视角, the hatch or a level's end), the system closed the cabin: the whole app ends.
            val meant = Game.leavingCabin
            Game.leavingCabin = false
            if (!meant && !Game.quitting) Game.quitApp("cabin closed without surfacing") else Game.afterDive()
            Log.i(TAG, "surfaced (${if (meant) "asked" else "not asked"})")
        }
    }
    val lifecycle = androidx.compose.ui.platform.LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val music = Music(context).also { it.muted = !Game.musicOn }
        val hum = Motor(context).also { motor[0] = it }
        // v0.5.2 (device 2026-11-19: Home in the cabin only stopped the voice, the tank stayed open): stopped and not back
        // within [HOME_WAIT_MS], not surfacing and not quitting, the cabin was left with Home: every window closes.
        val stopped = booleanArrayOf(false)
        val homeCheck = Runnable {
            if (!stopped[0] || Game.leavingCabin || Game.quitting) return@Runnable
            if (SystemClock.uptimeMillis() - Game.cabinWindowAskAt < WINDOW_GRACE_MS) {
                Log.i(TAG, "cabin stopped just after it asked for a window: not taken for Home")
                return@Runnable
            }
            Log.i(TAG, "home in the cabin: still stopped after $HOME_WAIT_MS ms, quitting")
            Game.quitApp("home in cabin")
        }
        val onStop = {
            stopped[0] = true
            main.removeCallbacks(homeCheck)
            if (!Game.leavingCabin && !Game.quitting) {
                Log.i(TAG, "cabin stopped (Home?): checking in $HOME_WAIT_MS ms")
                main.postDelayed(homeCheck, HOME_WAIT_MS)
            }
        }
        Game.debugHome = { Log.i(TAG, "test home: as if Home were pressed in the cabin"); onStop() }
        val observer = androidx.lifecycle.LifecycleEventObserver { _, e ->
            when (e) {
                androidx.lifecycle.Lifecycle.Event.ON_RESUME -> { music.resume(); hum.resume() }
                androidx.lifecycle.Lifecycle.Event.ON_PAUSE -> { music.pause(); hum.pause() }
                androidx.lifecycle.Lifecycle.Event.ON_START -> { if (stopped[0]) Log.i(TAG, "cabin started again"); stopped[0] = false; main.removeCallbacks(homeCheck) }
                androidx.lifecycle.Lifecycle.Event.ON_STOP -> { Game.stopNarration(); onStop() }
                else -> {}
            }
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            main.removeCallbacks(homeCheck)
            Game.debugHome = { Log.i(TAG, "home: not in the cabin") }
            music.release()
            hum.release()
            if (motor[0] === hum) motor[0] = null
        }
    }
    LaunchedEffect(Game.playVersion, view) {
        val v = view ?: return@LaunchedEffect
        val p = Game.play ?: return@LaunchedEffect
        synchronized(Game.lock) {
            v.build(p); v.receiveAll(); v.cockpitMode(true)
            tankRef[0]?.let { it.build(p); it.receiveAll() }
            motion.reset()
        }
    }
    // Asked to come up (the button, the hatch), or the dive is over (its card shows in the lab's console).
    OnBump(Game.surfaceRequest) { surface() }
    LaunchedEffect(Game.phase) {
        if (Game.phase == Game.Phase.PLAYING) return@LaunchedEffect
        // The level is over; a creature still being introduced is heard out in the cabin first (at most 15 s).
        var waited = 0
        while (Game.speaking != null && waited < 15_000) { kotlinx.coroutines.delay(250); waited += 250 }
        if (waited > 0) Log.i(TAG, "level over: back outside after the words ($waited ms)")
        surface()
    }
    LaunchedEffect(Game.noteVersion) {
        val n = Game.note ?: return@LaunchedEffect
        kotlinx.coroutines.delay(if (n.length > 30) 7000 else 3500)
        if (Game.note == n) Game.note = null
    }

    SpatialView(
        Modifier.pointerInput(Unit) {
            // v0.5.2: a pinch on the cabin's tank, as on the tank window: on the water the sub goes there, on an animal it
            // goes and photographs it.
            // Only the tank's own pinch boxes: the panels' buttons keep their pinches.
            detectSpatialTapGesture(context, TargetEntity.any { e -> tankRef[0]?.target(e) != null }) { value ->
                val t = tankRef[0]?.target(value.targetEntity) ?: return@detectSpatialTapGesture
                main.post { Log.i(TAG, "cabin tank pinched: $t"); Game.tap(t) }
            }
        }.pointerInput(1) {
            // The hatch handle overhead: pinched and pulled down 7 cm, the sub surfaces.
            fun m(px: Float) = with(density) { converter.dpToLength(px.toDp(), com.pico.spatial.ui.platform.LengthUnit.Meters) }.toDouble()
            detectSpatialDragGesture(
                context = context,
                // The hatch and the cabin's tank only (v0.5.2: not the panels on the deck, whose buttons keep their pinches).
                targetedToEntity = TargetEntity.any { e -> cockpitRef[0]?.let { isUnder(e, it.hatch) } == true || tankRef[0]?.target(e) != null },
                onDragStart = { _, value ->
                    val ck = cockpitRef[0]
                    hatchPull[0] = if (ck != null && isUnder(value.targetEntity, ck.hatch)) 0.0 else -1.0
                    // A pinch on the cabin's tank that moved a little is still a pinch (as in the tank window).
                    if (hatchPull[0] < 0) tankRef[0]?.target(value.targetEntity)?.let { t -> main.post { Log.i(TAG, "cabin tank pinched (drag): $t"); Game.tap(t) } }
                },
                onDragEnd = { hatchPull[0] = -1.0; cockpitRef[0]?.pullHatch(0.0) },
                onDragCancel = { hatchPull[0] = -1.0; cockpitRef[0]?.pullHatch(0.0) },
            ) { value ->
                if (hatchPull[0] < 0) return@detectSpatialDragGesture
                // v0.5.1 (device 2026-11-19: the handle sank as the hand went UP, and pulling down did nothing): the drag's
                // y grows downward (as in every other game here), so a pull down is +y. Only a pull down moves the handle;
                // lifting the hand never does.
                hatchPull[0] = (hatchPull[0] + m(value.dragAmount.y)).coerceAtLeast(0.0)
                cockpitRef[0]?.pullHatch(hatchPull[0])
                if (hatchPull[0] > HATCH_PULL_M) { hatchPull[0] = -1.0; Log.i(TAG, "hatch pulled down"); main.post { Game.requestSurface() } }
            }
        },
        attachments = {
            AttachmentPanel(PANEL_CABIN) { CabinPanel(converter) }
            AttachmentPanel(PANEL_CABIN_CONSOLE) { CabinConsoleKey(converter) }
            AttachmentPanel(PANEL_CABIN_LAMP) { CabinLampKey(converter) }
            AttachmentPanel(PANEL_CABIN_COPY) { CabinConsoleCopy(converter, toggleWindow, appVersion) }
        },
        update = { _, attachments -> view?.let { attach(seat, attachments, panels) } },
        initial = { content, attachments ->
            content.addEntity(Entity().apply {
                components.set(DirectionalLightComponent(Color(0xFFFFE9D0).toColor4(), 1000f, false))
                components[TransformComponent::class.java]?.setEulerAngles(EulerAngles(-65f, 15f, 0f))
            })
            val tex = HashMap<String, TextureResource>()
            var ibl: TextureResource? = null
            withContext(Dispatchers.IO) {
                for (name in TEXTURES) {
                    runCatching { TextureResource.load("textures/$name.jpg", LoadType.FROM_ASSETS, TextureCreateOption()).shared() }.onSuccess { tex[name] = it }
                }
                ibl = runCatching { TextureResource.load("textures/ibl_lab.ktx", LoadType.FROM_ASSETS, TextureCreateOption().apply { textureEncoding = TextureEncoding.LINEAR }).shared() }.getOrNull()
            }
            val models = HashMap<String, Entity>()
            for (name in SEA_MODELS) runCatching { Entity.loadSuspend("asset://models/$name.glb") }.onSuccess { models[name] = it }
            val world = Entity()
            content.addEntity(world)
            val v = SeaView(models, tex)
            seat.world = world
            seat.machine = v.root
            world.addChild(v.root)
            val ck = Cockpit().also { it.root.enabled = false }
            world.addChild(ck.root)
            seat.cockpit = ck
            cockpitRef[0] = ck
            val os = OpenSea(tex["sand"])
            world.addChild(os.root)
            openSea[0] = os
            // v0.5.2: the cabin's own tank on the right (core.CabinLayout), the same dive and the same sub as the tank window.
            val tank = SeaView(models, tex)
            tank.windowMode()
            ibl?.let { tank.useIbl(it) }
            tank.root.setScale(CabinLayout.TANK_SCALE.toFloat())
            tank.root.transform().eulerAngles = EulerAngles(0f, CabinLayout.tankViewYaw.toFloat(), 0f)
            world.addChild(tank.root)
            seat.tank = tank.root
            tankRef[0] = tank
            Game.cabinTank = tank
            Log.i(TAG, "cabin tank: ${CabinLayout.describeTank()}")
            Log.i(TAG, "cabin panels: " + CabinLayout.PANELS.joinToString("; ") { pl -> val q = CabinLayout.panelCentre(pl); "${pl.id} at ${"%.3f".format(q.x)},${"%.3f".format(q.y)},${"%.3f".format(q.z)} lowest corner ${"%.1f".format(CabinLayout.panelCorners(pl).minOf { CabinLayout.aboveDeck(it) } * 1000)} mm over the deck" })
            attach(seat, attachments, panels)
            v.sound = Sound(v.sea).also { it.load() }
            Game.view = v
            Game.sound = v.sound
            view = v
            Game.play?.let { synchronized(Game.lock) { v.build(it); v.receiveAll(); v.cockpitMode(true); tank.build(it); tank.receiveAll() } }
            var logClock = 0.0
            var pulledShown: Boolean? = null
            subscription[0] = content.subscribe(SceneEvents.Update::class.java) { e ->
                val dt = e.deltaTime.toDouble().coerceIn(0.0, 0.1)
                val pose = runCatching { if (head.state == DataProvider.State.STARTED) head.latestData?.hmdPose else null }.getOrNull()
                seat.frame(pose?.position, pose?.rotation, dt)
                // Test hook: a downward pull of the hatch handle, in steps, through the same rule as a hand's drag.
                Game.debugHatch?.let { total ->
                    Game.debugHatch = null
                    var pulled = 0.0
                    for (k in 1..5) { pulled = (pulled + total / 5).coerceAtLeast(0.0); cockpitRef[0]?.pullHatch(pulled) }
                    Log.i(TAG, "test hatch pulled ${"%.2f".format(pulled)} m")
                    if (pulled > HATCH_PULL_M) main.post { Game.requestSurface() }
                }
                main.post { Game.tick(dt) }
                synchronized(Game.lock) {
                    val p = Game.play
                    if (p != null) { v.sync(p, dt); tank.sync(p, dt) }
                    seat.cockpitFrame(true, p, v, motion, dt)
                    seat.place(panels)
                    if (p != null) {
                        val c = seat.centre ?: p.sub.at
                        val depth = 1.0 - p.sub.at.y / com.vrgames.deepsea.core.Sea.H
                        os.frame(dt, seat.eye, depth, COCKPIT_SCALE * c.y, motion.speed, CabinMotion.SCALE * c.x, CabinMotion.SCALE * (c.y - Dive.START.y))
                        motor[0]?.speed(p.sub.speed / Dive.MAX_SPEED)
                        val speedText = "航速 ${"%.1f".format(motion.knots())} 节 ${motion.arrow()}".trim()
                        if (speedText != Game.cabinSpeed) main.post { Game.cabinSpeed = speedText }
                        // Logs for the emulator check: the specks against the sub, the sea drawing in and out.
                        if (pulledShown != motion.pulling) { pulledShown = motion.pulling; Log.i(TAG, if (motion.pulling) "sea drawing in: speeding up (now ${"%.2f".format(motion.distance)} m)" else "sea going back out (now ${"%.2f".format(motion.distance)} m)") }
                        val before = logClock
                        logClock += dt
                        if (before.toInt() / 2 != logClock.toInt() / 2 && p.sub.speed > 0.002) {
                            val m = ck.motes
                            Log.i(TAG, "motes ${m.count}: ${"%.2f".format(m.speed)} m/s past the windows = ${"%.1f".format(m.speed / p.sub.speed)}x the sub's ${"%.3f".format(p.sub.speed)} m/s; sea at ${"%.2f".format(motion.distance)} m; ${Game.cabinSpeed}; school at x ${"%.1f".format(os.schoolX)}")
                        }
                    }
                }
            }
        },
    )
}

private fun setSky(e: Entity, m: com.pico.spatial.core.ecs.resource.Material) {
    e.components[com.pico.spatial.core.ecs.ModelComponent::class.java]?.materials?.let { mats -> for (i in 0 until mats.size) mats[i] = m }
    e.getChildren().forEach { setSky(it, m) }
}

private val TEXTURES = listOf("sand")


/** v0.5.2: the cabin's panels, put in the world once; [Seat.place] keeps them where core.CabinLayout says. */
private fun attach(seat: Seat, a: SpatialViewAttachments, panels: HashMap<String, Entity>) {
    val world = seat.world ?: return
    for (id in listOf(PANEL_CABIN, PANEL_CABIN_CONSOLE, PANEL_CABIN_LAMP, PANEL_CABIN_COPY)) {
        val e = a.entity(id) ?: continue
        if (panels[id] === e) continue
        panels[id] = e
        world.addChild(e)
        // Flat on the deck, with its tilt (the old card stood upright-ish and half of it sank into the console); the
        // console's copy stands upright at the left, facing the eyes.
        e.transform().eulerAngles = if (id == PANEL_CABIN_COPY) EulerAngles(0f, CabinLayout.COPY_YAW.toFloat(), 0f) else EulerAngles(CabinLayout.PANEL_TILT.toFloat(), 0f, 0f)
        if (id == PANEL_CABIN_COPY) e.enabled = false
    }
    seat.place(panels)
}

/**
 * Where the player sits: the world turned once to the mean heading of the first second of tracking
 * (and on 「摆到我正前方」), the machine [AHEAD] meters in front.
 */
private class Seat {
    var world: Entity? = null
    var machine: Entity? = null
    var sky: Entity? = null
    var cockpit: Cockpit? = null
    /** v0.5.2: the cabin's own tank's view root. */
    var tank: Entity? = null
    private var inCockpit = false
    private var logClock = 0.0
    private var viewAt: com.vrgames.deepsea.core.P? = null
    /** The point of the tank's sea the cabin is at now (smoothed), or null outside. */
    val centre get() = viewAt
    /**
     * v0.5.2 (device 2026-11-19: the cabin's height followed the head every frame): the eye height measured once, the same
     * way in every game (core.EyeHeight: steady, valid head heights; a seated guess if none come, corrected once). The
     * cabin is built about the eyes, so the height used is the eye's own (offset 0).
     */
    private var eyes = EyeHeight()
    private var eyeY = EyeHeight.FALLBACK
    val eye get() = eyeY
    /** Test hook ("replace"): the head reads all zeros until then, as a headset may right after tracking starts. */
    private var zeroUntil = 0L
    private val facing = com.vrgames.deepsea.core.Facing()
    private var waited = 0.0
    private var shown = false
    private var copyShown = false

    fun again() = facing.again()

    /** Test hook ("replace"): measure the eyes again, the head reading zeros for [zeroS] seconds. */
    fun remeasure(zeroS: Double) {
        eyes = EyeHeight()
        zeroUntil = SystemClock.uptimeMillis() + (zeroS * 1000).toLong()
        Log.i(TAG, "eyes: measuring again, head reads zeros for $zeroS s")
    }

    /** The panels on the deck, the console's copy and the cabin's tank, about the eyes (core.CabinLayout). */
    fun place(panels: Map<String, Entity>) {
        for (pl in CabinLayout.PANELS) panels[pl.id]?.let { e -> val q = CabinLayout.panelCentre(pl); e.setPos(Vector3(q.x.toFloat(), (eyeY + q.y).toFloat(), q.z.toFloat())) }
        panels[PANEL_CABIN_COPY]?.let { e ->
            val q = CabinLayout.copyCentre()
            e.setPos(Vector3(q.x.toFloat(), (eyeY + q.y).toFloat(), q.z.toFloat()))
            val want = Game.cabinConsoleCopy
            if (copyShown != want) { copyShown = want; e.enabled = want }
        }
        tank?.let { val r = CabinLayout.tankRoot(); it.setPos(Vector3(r.x.toFloat(), (eyeY + r.y).toFloat(), r.z.toFloat())) }
    }

    /**
     * The view of the frame: in the lab, the tank stands where it always does; in the cockpit, the
     * sea is [COCKPIT_SCALE] times larger and moved each frame so the sub's place in it is
     * [CabinMotion.distance] meters ahead of the eyes (the sea's creatures keep to one plane, which is then
     * a little way out of the windows), and the hull, the console and the sea all round come up.
     */
    fun cockpitFrame(on: Boolean, p: com.vrgames.deepsea.core.Dive?, v: SeaView, motion: CabinMotion, dt: Double) {
        val ck = cockpit ?: return
        if (on != inCockpit) {
            inCockpit = on
            ck.root.enabled = on
            sky?.enabled = !on
            v.cockpitMode(on)
            if (!on) {
                machine?.setPos(Vector3(0f, 0f, -AHEAD - FARTHER))
                machine?.transform()?.setScaleVector(Vector3(1f))
            }
        }
        if (!on || p == null) { viewAt = null; v.cabinCentre = null; return }
        val s = COCKPIT_SCALE
        // v0.5.1 (device 2026-11-19: in the cabin the sub seemed to stand still while it swam after the manta in the tank):
        // the view is the sub's own place, so the sea outside moves exactly as the sub moves in the tank, same way, same sea.
        val want = p.sub.at
        val c = viewAt?.let { it + (want - it) * (dt * 4.0).coerceAtMost(1.0) } ?: want
        viewAt = c
        v.cabinCentre = c
        val at = c
        // v0.5.2: the moves past the windows, and the sea drawn in while the sub speeds up.
        motion.step(c, p.sub.speed, dt)
        v.cabinScale = s
        v.cabinView = motion.distance.toFloat()
        machine?.transform()?.setScaleVector(Vector3(s))
        machine?.setPos(Vector3(-s * at.x.toFloat(), eyeY - s * (SeaView.BASE + at.y.toFloat()), -motion.distance.toFloat()))
        ck.root.setPos(Vector3(0f, eyeY, 0f))
        ck.debug = Game.debugCabin
        // Test hook: "yaw=<deg>" or "pitch=<deg>" turns the whole scene about the eyes, as a turn of the head would.
        Game.debugCabin?.let { d ->
            val o = d.split(";").mapNotNull { it.split("=").takeIf { kv -> kv.size == 2 }?.let { kv -> kv[0].trim() to (kv[1].trim().toFloatOrNull() ?: 0f) } }.toMap()
            val yaw = o["yaw"] ?: 0f
            val pitch = o["pitch"] ?: 0f
            if (yaw != 0f || pitch != 0f) world?.let { w ->
                val pr = Math.toRadians(pitch.toDouble())
                w.setPos(Vector3(0f, (eyeY - eyeY * kotlin.math.cos(pr)).toFloat(), (-eyeY * kotlin.math.sin(pr)).toFloat()))
                w.transform().eulerAngles = EulerAngles(pitch, yaw, 0f)
            }
        }
        if (Game.debugCabin != null && (logClock + dt).toInt() != logClock.toInt()) Log.i(TAG, "cabin eye $eyeY sub ${at.x},${at.y} machine ${machine?.transform()?.position} depth ${1.0 - at.y / com.vrgames.deepsea.core.Sea.H}")
        logClock += dt
        val depth = 1.0 - p.sub.at.y / com.vrgames.deepsea.core.Sea.H
        ck.frame(dt, depth, p.sub.lamp, motion.dx, motion.dy)
    }

    /** The scene is placed once and then stays, whatever the head does. It stays hidden until then (and the eyes measured). */
    fun frame(eyeRaw: Vector3?, rot: Quat?, dt: Double) {
        val eye = if (SystemClock.uptimeMillis() < zeroUntil) Vector3(0f, 0f, 0f) else eyeRaw
        if (eyes.feed(eye?.x, eye?.y, eye?.z, dt)) {
            eyeY = eyes.value!!
            Log.i(TAG, "eye height ${"%.3f".format(eyeY)} m (${if (eyes.guessed) "not measured yet: a seated guess" else "measured"})")
        }
        if (!shown) {
            waited += dt
            if ((facing.placed && eyes.value != null) || waited >= com.vrgames.deepsea.core.Facing.GIVE_UP_S) shown = true
            world?.let { if (it.enabled != shown) it.enabled = shown }
        }
        val d = Game.debugView?.split(",")?.mapNotNull { it.trim().toFloatOrNull() }
        if (d != null && d.size >= 6) {
            machine?.setPos(Vector3(d[0], d[1], d[2]))
            machine?.transform()?.eulerAngles = EulerAngles(d[5], d[3], 0f)
            machine?.transform()?.setScaleVector(Vector3(d[4]))
            return
        }
        if (rot == null || eye == null) return
        val f = rot.rotateVector(Vector3(0f, 0f, -1f))
        // Looking nearly straight up or down says nothing about the heading.
        val head = if (hypot(f.x, f.z) < 0.2f) null else com.vrgames.deepsea.core.Facing.heading(f.x.toDouble(), f.z.toDouble())
        if (facing.update(head, dt)) {
            world?.let {
                it.setPos(Vector3(eye.x, 0f, eye.z))
                it.transform().eulerAngles = EulerAngles(0f, facing.yaw.toFloat(), 0f)
            }
            machine?.setPos(Vector3(0f, 0f, -AHEAD - FARTHER))
            machine?.transform()?.eulerAngles = EulerAngles(0f, 0f, 0f)
            machine?.transform()?.setScaleVector(Vector3(1f))
            Log.i(TAG, "facing placed at ${facing.yaw}, eye $eye")
        }
    }

    companion object {
        // The tank's middle stands 0.85 m out (plus [FARTHER]), its glass about 0.88 m from the eyes.
        const val AHEAD = 0.85f
    }
}

private fun android.content.Context.findActivity(): android.app.Activity? {
    var c: android.content.Context? = this
    while (c is android.content.ContextWrapper) {
        if (c is android.app.Activity) return c
        c = c.baseContext
    }
    return null
}

/** The cockpit view: the sea this many times larger than in the tank, its plane this far ahead of the eyes. */
const val COCKPIT_SCALE = 12f
// 4 m: even the largest thing, grown 12 times, stays outside the 1.5 m cabin (device test 2026-11-12: a turtle swam through).
// v0.5.2: drawn in to 3.5 m while the sub speeds up (core.CabinMotion), the things still kept outside by SeaView.intoCabin.
const val COCKPIT_VIEW = CabinMotion.FAR.toFloat()

/** v0.5.2: stopped this long without starting again (and not surfacing), the cabin was left with Home. */
private const val HOME_WAIT_MS = 1200L
/** A stop just after the cabin asked a window to show is not taken for Home. */
private const val WINDOW_GRACE_MS = 2500L

private fun isUnder(e: Entity?, of: Entity): Boolean {
    var cur = e
    while (cur != null) {
        if (cur === of) return true
        cur = cur.getParent()
    }
    return false
}

/** A pull of the hatch handle this far down goes back outside (metres). */
private const val HATCH_PULL_M = 0.07

// The 2026-11-08 device test: the cards and the tank 18 cm farther and higher (the tank through its taller plinth).
private const val FARTHER = 0.18f
private const val LIFT = 0.18f
