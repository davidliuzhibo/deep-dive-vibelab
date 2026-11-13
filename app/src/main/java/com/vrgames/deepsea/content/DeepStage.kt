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
import com.pico.spatial.ui.foundation.gesture.detectSpatialPointerEvent
import com.pico.spatial.ui.platform.LocalPhysicalLengthConverter
import com.pico.spatial.ui.platform.containers.LocalSpatialNavigator
import com.vrgames.deepsea.ALBUM_ID
import com.vrgames.deepsea.GUIDE_ID
import com.vrgames.deepsea.LEVELS_ID
import com.vrgames.deepsea.game.AutoPilot
import com.vrgames.deepsea.game.Game
import com.vrgames.deepsea.render.Music
import com.vrgames.deepsea.render.Sound
import com.vrgames.deepsea.render.Cockpit
import com.vrgames.deepsea.render.SeaView
import com.vrgames.deepsea.render.color
import com.vrgames.deepsea.render.model
import com.vrgames.deepsea.render.setPos
import com.vrgames.deepsea.render.setScale
import com.vrgames.deepsea.render.shared
import com.vrgames.deepsea.render.transform
import com.vrgames.deepsea.render.unlit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

private const val TAG = "DeepStage"

/** The glass tank in front of the seated player, in the ship's lab. A pinch on the water steers the sub, a pinch on a creature photographs it. */
@Composable
fun DeepStage() {
    val context = LocalContext.current
    val converter = LocalPhysicalLengthConverter.current
    val navigator = LocalSpatialNavigator.current
    val main = remember { Handler(Looper.getMainLooper()) }
    val head = remember { HMDTrackingProvider() }
    val subscription = remember { arrayOfNulls<Cancellable>(1) }
    var view by remember { mutableStateOf<SeaView?>(null) }
    val seat = remember { Seat() }
    val cockpitRef = remember { arrayOfNulls<Cockpit>(1) }
    val panels = remember { HashMap<String, Entity>() }
    val skies = remember { arrayOfNulls<TextureResource>(1) }
    val appVersion = remember { runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: "" }

    val toggleWindow: (String) -> Unit = { id ->
        val shown = Game.window
        if (shown != null) Game.closeWindow(shown)
        if (shown != id) {
            Game.windowShown(id)
            navigator.openWindowContainer(id)
        }
    }

    DisposableEffect(Unit) {
        Game.init(context)
        Game.closeWindow = { id ->
            navigator.closeWindowContainer(id)
            Game.windowGone(id)
        }
        val hd = runCatching { head.start() }
        Log.i(TAG, "head ${hd.getOrNull() ?: hd.exceptionOrNull()?.message}")
        onDispose {
            runCatching { head.stop() }
            subscription[0]?.cancel()
            Game.closeWindow(LEVELS_ID)
            Game.closeWindow(GUIDE_ID)
            Game.closeWindow(ALBUM_ID)
            Game.closeWindow = {}
        }
    }
    val lifecycle = androidx.compose.ui.platform.LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val music = Music(context).also {
            it.muted = !Game.musicOn
            Game.music = it
        }
        val observer = androidx.lifecycle.LifecycleEventObserver { _, e ->
            if (e == androidx.lifecycle.Lifecycle.Event.ON_RESUME) music.resume() else if (e == androidx.lifecycle.Lifecycle.Event.ON_PAUSE) music.pause()
            // Home pressed mid-narration: the voice stops with the game (device test 2026-11-12). ON_STOP, as a window of
            // the game's own opening also pauses the stage.
            if (e == androidx.lifecycle.Lifecycle.Event.ON_STOP) Game.stopNarration()
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            music.release()
            Game.music = null
        }
    }
    // The land of the level.
    LaunchedEffect(Game.playVersion, view) {
        val v = view ?: return@LaunchedEffect
        val p = Game.play ?: return@LaunchedEffect
        synchronized(Game.lock) { v.build(p); v.receiveAll() }
    }
    LaunchedEffect(Game.trophies, view) { view?.trophies(Game.trophies) }
    LaunchedEffect(Game.faceRequest) { if (Game.faceRequest > 0) seat.again() }
    LaunchedEffect(Game.noteVersion) {
        val n = Game.note ?: return@LaunchedEffect
        kotlinx.coroutines.delay(if (n.length > 30) 7000 else 3500)
        if (Game.note == n) Game.note = null
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

    SideEffect {
        fun show(id: String, on: Boolean) {
            panels[id]?.let { if (it.enabled != on) it.enabled = on }
            // The riveted frame too, by itself: on the emulator a hidden HUD's frame came back in the cabin (2026-11-12).
            PANEL_FRAMES[id]?.let { frames[it] }?.let { if (it.enabled != on) it.enabled = on }
        }
        val ph = Game.phase
        show(PANEL_CARD, ph == Game.Phase.CARD)
        show(PANEL_INTRO, ph == Game.Phase.INTRO)
        show(PANEL_RESULT, ph == Game.Phase.RESULT)
        show(PANEL_SHOW, ph == Game.Phase.SHOW)
        // In the cabin only the console's screen shows (device test 2026-11-12: panels in the cabin were covered by the sea).
        val cabin = Game.cockpit && ph == Game.Phase.PLAYING
        show(PANEL_HUD, ph == Game.Phase.PLAYING && !cabin)
        show(PANEL_NOTE, Game.note != null && !cabin)
        show(PANEL_MEETING, ph == Game.Phase.PLAYING && Game.meeting != null && !cabin)
        show(PANEL_PHOTOS, ph == Game.Phase.PLAYING && !cabin)
        show(PANEL_CABIN, cabin)
    }

    SpatialView(
        Modifier.pointerInput(Unit) {
            detectSpatialPointerEvent(context, TargetEntity.any()) { infos ->
                val v = view
                if (v != null) for (i in infos) {
                    if (!i.isDownEvent()) continue
                    // The lamp switch on the cockpit's console.
                    val ck = cockpitRef[0]
                    if (ck != null && Game.cockpit && isUnder(i.targetedEntity, ck.lampButton)) {
                        main.post { Game.lamp() }
                        continue
                    }
                    val t = v.target(i.targetedEntity) ?: continue
                    main.post { Game.tap(t) }
                }
                false
            }
        },
        attachments = {
            AttachmentPanel(PANEL_CARD) { CardPanel(converter) }
            AttachmentPanel(PANEL_INTRO) { IntroPanel(converter) }
            AttachmentPanel(PANEL_HUD) { HudPanel(converter, toggleWindow) }
            AttachmentPanel(PANEL_RESULT) { ResultPanel(converter, toggleWindow) }
            AttachmentPanel(PANEL_SHOW) { ShowPanel(converter) }
            AttachmentPanel(PANEL_NOTE) { NotePanel(converter) }
            AttachmentPanel(PANEL_MEETING) { MeetingPanel(converter) }
            AttachmentPanel(PANEL_PHOTOS) { PhotosPanel(converter) }
            AttachmentPanel(PANEL_CABIN) { CabinPanel(converter) }
            AttachmentPanel(PANEL_MENU) {
                MenuPanel(converter, toggleWindow, faceMe = { Game.faceRequest++ }, quit = { context.findActivity()?.finishAndRemoveTask() }, version = appVersion)
            }
        },
        update = { _, attachments ->
            view?.let {
                attach(seat, attachments, panels)
                val ph = Game.phase
                panels[PANEL_CARD]?.enabled = ph == Game.Phase.CARD
                panels[PANEL_INTRO]?.enabled = ph == Game.Phase.INTRO
                panels[PANEL_RESULT]?.enabled = ph == Game.Phase.RESULT
                panels[PANEL_SHOW]?.enabled = ph == Game.Phase.SHOW
                panels[PANEL_HUD]?.enabled = ph == Game.Phase.PLAYING
                panels[PANEL_NOTE]?.enabled = Game.note != null
                panels[PANEL_MEETING]?.enabled = ph == Game.Phase.PLAYING && Game.meeting != null
                panels[PANEL_PHOTOS]?.enabled = ph == Game.Phase.PLAYING
            }
        },
        initial = { content, attachments ->
            content.addEntity(Entity().apply {
                components.set(DirectionalLightComponent(Color(0xFFFFE9D0).toColor4(), 1000f, false))
                components[TransformComponent::class.java]?.setEulerAngles(EulerAngles(-65f, 15f, 0f))
            })
            val tex = HashMap<String, TextureResource>()
            var ibl: TextureResource? = null
            withContext(Dispatchers.IO) {
                for (name in TEXTURES) {
                    runCatching { TextureResource.load("textures/$name.jpg", LoadType.FROM_ASSETS, TextureCreateOption()).shared() }
                        .onSuccess { tex[name] = it }
                        .onFailure { Log.i(TAG, "texture $name: ${it.message}") }
                }
                skies[0] = runCatching { TextureResource.load("textures/sky_lab.jpg", LoadType.FROM_ASSETS, TextureCreateOption()).shared() }.getOrNull()
                ibl = runCatching { TextureResource.load("textures/ibl_lab.ktx", LoadType.FROM_ASSETS, TextureCreateOption().apply { textureEncoding = TextureEncoding.LINEAR }).shared() }.getOrNull()
            }
            for (name in PANEL_FRAMES.values) runCatching { Entity.loadSuspend("asset://panels/$name.glb") }.onSuccess { frames[name] = it }
            val models = HashMap<String, Entity>()
            for (name in MODELS) {
                runCatching { Entity.loadSuspend("asset://models/$name.glb") }
                    .onSuccess { models[name] = it }
                    .onFailure { Log.i(TAG, "model $name: ${it.message}") }
            }
            // The panorama all around, centered on the player's eyes.
            val s = models["sky"]?.clone(Entity.CloneOptions(true, true))
                ?: model(com.pico.spatial.core.ecs.resource.MeshResource.createSphere(1f), unlit(color(0.1f, 0.08f, 0.14f)).apply { setCullingMode(MaterialCullingMode.FRONT) })
            s.setScale(if (models["sky"] != null) 9f / 12f else 9f)
            s.setPos(Vector3(0f, 1.2f, 0f))
            skies[0]?.let { t -> setSky(s, UnlitMaterial.create().apply { setBaseColorTexture(t) }) }
            val world = Entity()
            world.addChild(s)
            content.addEntity(world)
            val v = SeaView(models, tex)
            seat.world = world
            seat.machine = v.root
            seat.sky = s
            world.addChild(v.root)
            val ck = Cockpit().also { it.root.enabled = false }
            world.addChild(ck.root)
            seat.cockpit = ck
            cockpitRef[0] = ck
            attach(seat, attachments, panels)
            ibl?.let { v.useIbl(it) }
            Game.view = v
            Game.sound = Sound(v.sea).also { it.load() }
            view = v
            Game.play?.let { v.build(it) } ?: run { Game.intro(com.vrgames.deepsea.core.Stages.ALL[0]); Game.toCard(); Game.play?.let { v.build(it) } }
            AutoPilot.ready()
            subscription[0] = content.subscribe(SceneEvents.Update::class.java) { e ->
                val dt = e.deltaTime.toDouble().coerceIn(0.0, 0.1)
                val pose = runCatching { if (head.state == DataProvider.State.STARTED) head.latestData?.hmdPose else null }.getOrNull()
                seat.frame(pose?.position, pose?.rotation, dt)
                main.post { Game.tick(dt) }
                synchronized(Game.lock) {
                    val p = Game.play
                    if (p != null) v.sync(p, dt)
                    // The cockpit: the sea, grown large, is moved so the sub is where the player sits.
                    seat.cockpitFrame(Game.cockpit, p, v, pose?.position?.y, dt)
                    // The console screen follows the cabin's height (the eyes), on the console top.
                    if (Game.cockpit) panels[PANEL_CABIN]?.setPos(Vector3(0f, seat.eye - 0.46f, -0.52f))
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
private val MODELS = listOf("sky", "sub", "angler", "jelly", "squid", "turtle", "manta", "lantern", "wreck", "vent", "tube", "whale", "coral", "chest", "trophy")

/** The thick riveted frames behind the panels (tools/panel3d), loaded by the stage. */
private val PANEL_FRAMES = mapOf(PANEL_CARD to "panel_card", PANEL_INTRO to "panel_intro", PANEL_RESULT to "panel_result", PANEL_SHOW to "panel_show", PANEL_HUD to "panel_hud", PANEL_MEETING to "panel_meeting")
private val frames = HashMap<String, Entity>()

private fun attach(seat: Seat, a: SpatialViewAttachments, panels: HashMap<String, Entity>) {
    val world = seat.world ?: return
    fun put(id: String, pos: Vector3, pitch: Float, yaw: Float = 0f) {
        val e = a.entity(id) ?: return
        if (panels[id] === e) return
        panels[id] = e
        world.addChild(e)
        // The device test found the cards and the tank too close and too low: everything 18 cm farther and higher.
        e.setPos(Vector3(pos.x, pos.y + LIFT, pos.z - FARTHER))
        PANEL_FRAMES[id]?.let { name -> frames[name]?.let { f -> f.setPos(Vector3(0f, 0f, -0.006f)); e.addChild(f) } }
        e.transform().eulerAngles = EulerAngles(pitch, yaw, 0f)
    }
    // Cards in front of the glass, the panel with the buttons at the left, turned toward the player.
    put(PANEL_CARD, Vector3(0f, 1.02f, -0.6f), -8f)
    put(PANEL_INTRO, Vector3(0f, 1.02f, -0.6f), -8f)
    put(PANEL_RESULT, Vector3(0f, 1.02f, -0.6f), -8f)
    put(PANEL_SHOW, Vector3(-0.72f, 1.2f, -0.72f), -6f, 42f)
    put(PANEL_HUD, Vector3(-0.8f, 1.02f, -0.72f), -10f, 42f)
    put(PANEL_NOTE, Vector3(0f, 0.86f, -0.6f), -10f)
    put(PANEL_MEETING, Vector3(0.74f, 1.1f, -0.72f), -8f, -42f)
    put(PANEL_MENU, Vector3(-0.74f, 0.62f, -0.56f), -30f, 38f)
    // The cabin's screen, on the console under the front window, tipped up toward the player.
    put(PANEL_CABIN, Vector3(0f, 0.74f, -0.52f), -55f)
    // The photo screen low at the right of the tank.
    put(PANEL_PHOTOS, Vector3(0.7f, 0.66f, -0.6f), -24f, -40f)
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
    private var inCockpit = false
    private var logClock = 0.0
    private var viewAt: com.vrgames.deepsea.core.P? = null
    private var eyeY = 1.2f
    val eye get() = eyeY
    private val facing = com.vrgames.deepsea.core.Facing()
    private var waited = 0.0
    private var shown = false

    fun again() = facing.again()

    /**
     * The view of the frame: in the lab, the tank stands where it always does; in the cockpit, the
     * sea is [COCKPIT_SCALE] times larger and moved each frame so the sub's place in it is
     * [COCKPIT_VIEW] meters ahead of the eyes (the sea's creatures keep to one plane, which is then
     * a little way out of the windows), and the hull, the console and the sea all round come up.
     */
    fun cockpitFrame(on: Boolean, p: com.vrgames.deepsea.core.Dive?, v: SeaView, headY: Float?, dt: Double) {
        if (headY != null && headY > 0.5f) eyeY = headY
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
        // Straight ahead is the followed animal (the sub keeps a little off to one side of it, at photo range), moved
        // to smoothly so the view does not jump when it changes.
        val want = p.follow?.at ?: p.sub.at
        val c = viewAt?.let { it + (want - it) * (dt * 1.5).coerceAtMost(1.0) } ?: want
        viewAt = c
        v.cabinCentre = c
        val at = c
        v.cabinScale = s
        v.cabinView = COCKPIT_VIEW
        machine?.transform()?.setScaleVector(Vector3(s))
        machine?.setPos(Vector3(-s * at.x.toFloat(), eyeY - s * (SeaView.BASE + at.y.toFloat()), -COCKPIT_VIEW))
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
        ck.frame(dt, depth, p.sub.lamp, p.sub.speed * s)
    }

    /** The scene is placed once and then stays, whatever the head does. It stays hidden until then. */
    fun frame(eye: Vector3?, rot: Quat?, dt: Double) {
        if (!shown) {
            waited += dt
            if (facing.placed || waited >= com.vrgames.deepsea.core.Facing.GIVE_UP_S) shown = true
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
            shown = true
            world?.enabled = true
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
const val COCKPIT_VIEW = 4.0f

private fun isUnder(e: Entity?, of: Entity): Boolean {
    var cur = e
    while (cur != null) {
        if (cur === of) return true
        cur = cur.getParent()
    }
    return false
}

// The 2026-11-08 device test: the cards and the tank 18 cm farther and higher (the tank through its taller plinth).
private const val FARTHER = 0.18f
private const val LIFT = 0.18f
