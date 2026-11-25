package com.vrgames.deepsea.render

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import com.pico.spatial.core.ecs.BoundingBox
import com.pico.spatial.core.ecs.CollisionComponent
import com.pico.spatial.core.ecs.Entity
import com.pico.spatial.core.ecs.HoverEffectComponent
import com.pico.spatial.core.ecs.InteractableComponent
import com.pico.spatial.core.ecs.ModelEntity
import com.pico.spatial.core.ecs.resource.BlendingMode
import com.pico.spatial.core.ecs.resource.MaterialCullingMode
import com.pico.spatial.core.ecs.resource.MeshModel
import com.pico.spatial.core.ecs.resource.MeshResource
import com.pico.spatial.core.ecs.resource.PhysicsMaterialResource
import com.pico.spatial.core.ecs.resource.ShapeResource
import com.pico.spatial.core.ecs.resource.TextureResource
import com.pico.spatial.core.ecs.resource.UnlitMaterial
import com.pico.spatial.core.math.EulerAngles
import com.pico.spatial.core.math.Vector2
import com.pico.spatial.core.math.Vector3
import com.pico.spatial.core.math.Vector4
import com.vrgames.deepsea.core.CabinDecor
import com.vrgames.deepsea.core.CabinLayout
import com.vrgames.deepsea.core.Vec3
import kotlin.math.acos
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * The second view (2026-11-09 review, the user's request of 2026-11-12): the player sits inside the
 * little sub. A hull all round with four round windows (ahead, left, right, overhead), a console
 * with the lamp switch, and outside the sea itself, its colour going from sunlit blue to black as
 * the sub goes down, light from the surface fading, marine snow drifting by. Everything here is in
 * the world frame centred on the player's eyes ([root] is put there); the sea is the tank's, made
 * [com.vrgames.deepsea.content.COCKPIT_SCALE] times larger by the stage.
 */
class Cockpit {
    val root = Entity()
    private val sphere = MeshResource.createSphere(1f).shared()

    private val hullMat = pbr(color(0.16f, 0.19f, 0.23f), metallic = 0.55f, roughness = 0.45f, emissive = color(0.035f, 0.045f, 0.06f)).apply { setCullingMode(MaterialCullingMode.NONE) }.shared()
    // Window rims: dark gunmetal, thin (device test 2026-11-12: big gold rings round the windows read as strange gold shapes).
    private val rimMat = pbr(color(0.22f, 0.24f, 0.27f), metallic = 0.85f, roughness = 0.35f, emissive = color(0.02f, 0.025f, 0.03f)).shared()
    private val consoleMat = pbr(color(0.1f, 0.12f, 0.15f), metallic = 0.6f, roughness = 0.4f, emissive = color(0.02f, 0.025f, 0.03f)).shared()

    // Declared before the entities that use it (property initializers run in order).
    private val sunMat = UnlitMaterial.create(BlendingMode.ADD).apply { setBaseColorTexture(TextureResource.create(radial())) }.shared()

    /** The sea all round, tinted by depth (a gradient: brighter overhead). */
    private val water = UnlitMaterial.create().apply {
        setBaseColorTexture(TextureResource.create(gradient()))
        setCullingMode(MaterialCullingMode.FRONT)
    }.shared()
    private val backdrop = ModelEntity(sphere, water).apply { setScale(BACKDROP) }.also { root.addChild(it) }
    /** The sun through the surface, overhead: strong near the top, gone by the twilight zone. */
    private val sunDisc = softDisc().also { d -> root.addChild(d) }
    /** v0.5.2: specks fixed in the sea, streaming past the windows as the sub moves (they replace the drifting snow). */
    val motes = Motes(sphere, glow(color(0.85f, 0.9f, 1f, 0.55f)).shared()).also { root.addChild(it.entity) }

    // ---- v0.6.0 (PICO 1124: 「全沉浸模式下环境氛围感和沉浸感不够」) ----
    /** Plankton flashing blue-green in the dark: nothing in the sunlit water, more the deeper. */
    private val glimmerMat = UnlitMaterial.create(BlendingMode.ADD).apply { setBaseColor(color(0f, 0f, 0f, 0f)) }.shared()
    private val glimmers = Motes(sphere, glimmerMat, "cabin_glow", 220, 1.4, 9.0, 6.0, 3.2, 0.007, 0.018, twinkle = true).also { root.addChild(it.entity) }
    /** A far layer of bigger, dimmer specks (9–20 m out), so the water has depth beyond the near snow. */
    private val farMat = UnlitMaterial.create(BlendingMode.ADD).apply { setBaseColor(color(0.2f, 0.24f, 0.28f, 1f)) }.shared()
    private val farMotes = Motes(sphere, farMat, "cabin_far", 160, 9.0, 20.0, 14.0, 7.0, 0.03, 0.065).also { root.addChild(it.entity) }
    /**
     * The surface from below in the sunlit zone: a shimmering net of light overhead (two layers turning against each other),
     * far enough ahead (centre 11 m out, 6.5 m up: under 30° above level) to be seen through the top of the front window.
     */
    private val rippleMat = UnlitMaterial.create(BlendingMode.ADD).apply {
        setBaseColorTexture(TextureResource.create(caustics()).shared()); setCullingMode(MaterialCullingMode.NONE); setDepthWrite(false)
    }.shared()
    private val ripples = listOf(0, 1).map { k ->
        ModelEntity(MeshResource.createPlane(26f, 26f, 0f).shared(), rippleMat).apply {
            setPos(Vector3(0f, 6.5f + 0.4f * k, -11f))
            transform().eulerAngles = EulerAngles(90f, 30f * k, 0f)
        }.also { root.addChild(it) }
    }
    /** The lamp lights what is out there: a spot from under the front window, ahead and a little down. */
    private val lampLight = Entity().apply {
        setPos(Vector3(0f, -0.42f, -1.62f))
        transform().eulerAngles = EulerAngles(-7f, 0f, 0f)
        runCatching { components.set(com.pico.spatial.core.ecs.SpotLightComponent(color(0.85f, 0.93f, 1f), 0f, 18f, 13f, 26f, false)) }
            .onFailure { android.util.Log.w("DeepCabin", "lamp light: ${it.message}") }
    }.also { root.addChild(it) }
    /** Night lighting in the deep: a dim red lamp in the cabin roof. */
    private val redLamp = Entity().apply {
        setPos(Vector3(0f, 0.55f, -0.25f))
        runCatching { components.set(com.pico.spatial.core.ecs.PointLightComponent(color(1f, 0.18f, 0.12f), 0f, 2.6f)) }
            .onFailure { android.util.Log.w("DeepCabin", "red lamp: ${it.message}") }
    }.also { root.addChild(it) }
    /** Instrument lights along the top of the desk's front plate, blinking each at its own pace. */
    // v0.7.0: no amber among them (only the real controls glow orange, core.CabinDecor).
    private val ledMats = listOf(color(0.3f, 1f, 0.45f), color(0.9f, 0.95f, 1f), color(0.35f, 0.75f, 1f), color(1f, 0.3f, 0.25f)).map { glow(it).shared() }
    private val leds = ArrayList<Pair<Entity, Int>>()
    private var night = -1f

    /**
     * v0.5.2 (device 2026-11-19: the lamp showed nothing in the cabin): two beams from under the front window out into the
     * sea while the lamp is on.
     */
    private val beamMat = glow(color(0.8f, 0.9f, 1f, 0.12f)).apply { setCullingMode(MaterialCullingMode.NONE); setDepthWrite(false) }.shared()
    private val beams = Entity().also { root.addChild(it) }
    private var beamsOn: Boolean? = null

    /** v0.7.0: the real controls' orange, solid and glowing (core.CabinDecor.ORANGE). */
    // Unlit, so the cabin's lights cannot wash it toward cream or gold (emulator 2026-11-25: the lit one read pale gold); the
    // colour in linear terms for the panels' #FF8C1F.
    private val orangeMat = unlit(color(1f, 0.27f, 0.015f)).shared()
    private val orangeGlowMat = glow(color(CabinDecor.ORANGE[0], CabinDecor.ORANGE[1], CabinDecor.ORANGE[2], 0.35f)).apply { setCullingMode(MaterialCullingMode.FRONT) }.shared()
    private val tankFrame = Entity().also { root.addChild(it) }
    private var dressing: CabinDressing? = null

    /** v0.5: the hatch handle pinched overhead. */
    val hatch: Entity
    private var clock = 0.0

    private val hull = ModelEntity(hullMesh(), hullMat)
    private val rims = Entity()
    private val desk = Entity()

    /** Test hook (debug builds): "bd=<backdrop scale>;hide=hull,rims,desk,backdrop,sun,snow". */
    @Volatile var debug: String? = null
    private var debugDone: String? = null

    init {
        root.addChild(hull)
        root.addChild(rims)
        root.addChild(desk)
        for (w in WINDOWS) {
            val d = w.dir.normalized()
            // createTorus takes the ring's outer and inner radii (0.4 build passed a tube radius: each rim became a
            // fat donut that filled its window, the gray screen of the emulator run of 2026-11-12).
            val rr = (RH * sin(w.half)).toFloat()
            val ring = ModelEntity(MeshResource.createTorus(rr + 0.012f, rr - 0.012f), rimMat)
            val at = Vector3((d.x * RH * cos(w.half)).toFloat(), (d.y * RH * cos(w.half)).toFloat(), (d.z * RH * cos(w.half)).toFloat())
            ring.setPos(at)
            ring.transform().setQuaternion(quatFromY(d))
            rims.addChild(ring)
        }
        // v0.6.0: bolts round every window, a glass pane in each with a faint sheen, and water droplets on the front one.
        val bolt = MeshResource.createSphere(1f).shared()
        val boltMat = pbr(color(0.32f, 0.34f, 0.37f), metallic = 0.9f, roughness = 0.3f, emissive = color(0.02f, 0.022f, 0.026f)).shared()
        val glassMat = pbr(color(0.75f, 0.88f, 1f, 0.05f), metallic = 0f, roughness = 0.04f, emissive = color(0f, 0f, 0f), blending = BlendingMode.TRANSPARENT).apply { setCullingMode(MaterialCullingMode.NONE) }.shared()
        val dropMat = pbr(color(0.85f, 0.93f, 1f, 0.32f), metallic = 0f, roughness = 0.03f, emissive = color(0.03f, 0.04f, 0.05f), blending = BlendingMode.TRANSPARENT).shared()
        val rnd = java.util.Random(61)
        for ((wi, w) in WINDOWS.withIndex()) {
            val d = w.dir.normalized()
            val u = (if (kotlin.math.abs(d.y) < 0.9) Vec3(0.0, 1.0, 0.0) else Vec3(1.0, 0.0, 0.0)).cross(d).normalized()
            val v = d.cross(u)
            val rr = RH * sin(w.half)
            val centre = d * (RH * cos(w.half))
            val nb = if (wi == 0) 24 else 12
            for (k in 0 until nb) {
                val a = 2 * Math.PI * k / nb
                val q = (centre + u * ((rr + 0.035) * cos(a)) + v * ((rr + 0.035) * sin(a))).normalized() * (RH - 0.012)
                rims.addChild(ModelEntity(bolt, boltMat).apply { setPos(q.toVector3()); setScale(0.009f) })
            }
            rims.addChild(ModelEntity(MeshResource.createCylinder(0.002f, rr.toFloat()), glassMat).apply {
                setPos((centre * 0.985).toVector3()); transform().setQuaternion(quatFromY(d))
            })
            if (wi == 0) for (k in 0 until 46) {
                val a = rnd.nextDouble() * 2 * Math.PI
                val r = rr * 0.92 * sqrt(rnd.nextDouble())
                val q = centre * 0.982 + u * (r * cos(a)) + v * (r * sin(a))
                val sz = (0.0018 + 0.0035 * rnd.nextDouble() * rnd.nextDouble()).toFloat()
                rims.addChild(ModelEntity(bolt, dropMat).apply {
                    setPos(q.toVector3()); transform().setQuaternion(quatFromY(d))
                    // Flattened on the glass, a little longer downward where it runs.
                    transform().setScaleVector(Vector3(sz, sz * 0.35f, sz * (1.2f + rnd.nextFloat())))
                })
            }
        }
        // The pilot's console under the big front window: a desk across the cabin, its top tipped up toward the player.
        // v0.5.2: 12 cm farther out (core.CabinLayout), and its controls are the panels the stage lays flat on its deck
        // (left 打开控制台, middle the screen and 回到舱外视角, right 探照灯); the two blue dials and the unlabelled yellow
        // lamp disc are gone.
        val console = Entity().apply {
            setPos(Vector3(0f, CabinLayout.CONSOLE_Y.toFloat(), CabinLayout.CONSOLE_Z.toFloat()))
            transform().eulerAngles = EulerAngles(CabinLayout.CONSOLE_TILT.toFloat(), 0f, 0f)
        }
        console.addChild(ModelEntity(MeshResource.createBox(Vector3(CabinLayout.DECK_W.toFloat(), CabinLayout.DECK_T.toFloat(), CabinLayout.DECK_D.toFloat()), 0.02f), consoleMat))
        console.addChild(ModelEntity(MeshResource.createBox(Vector3(1.24f, 0.03f, 0.03f), 0.01f), rimMat).apply { setPos(Vector3(0f, 0.03f, 0.21f)) })
        // The lamp's two beams: from just outside the front window, low, out ahead (a cone's tip is its +y end).
        for (side in listOf(-1f, 1f)) {
            val len = 5f
            beams.addChild(ModelEntity(MeshResource.createCone(1f, 1f).shared(), beamMat).apply {
                setPos(Vector3(side * 0.32f, -0.42f, -1.58f - len / 2))
                transform().eulerAngles = EulerAngles(90f, 0f, 0f)
                transform().setScaleVector(Vector3(0.85f, len, 0.85f))
            })
        }
        beams.enabled = false
        // v0.5: the hatch handle overhead, a little ahead: pinched and pulled down, the sub surfaces.
        // v0.7.0: a real control, so orange with a breathing glow round the grip, and its name (core.CabinDecor).
        hatch = Entity().apply {
            setPos(Vector3(0f, 0.38f, -0.18f))
            addChild(ModelEntity(MeshResource.createBox(Vector3(0.22f, 0.025f, 0.03f), 0.008f), orangeMat))
            addChild(ModelEntity(MeshResource.createBox(Vector3(0.02f, 0.08f, 0.02f), 0.005f), orangeMat).apply { setPos(Vector3(-0.09f, 0.045f, 0f)) })
            addChild(ModelEntity(MeshResource.createBox(Vector3(0.02f, 0.08f, 0.02f), 0.005f), orangeMat).apply { setPos(Vector3(0.09f, 0.045f, 0f)) })
            addChild(ModelEntity(MeshResource.createBox(Vector3(0.25f, 0.04f, 0.05f), 0.012f), orangeGlowMat).apply { setPos(Vector3(0f, 0f, 0f)) })
            addChild(label("上浮拉手：捏住往下拉").apply { setPos(Vector3(0f, -0.035f, 0.025f)); transform().eulerAngles = EulerAngles(-40f, 0f, 0f) })
            components.set(CollisionComponent(listOf(ShapeResource.createBox(Vector3(0.3f, 0.14f, 0.14f))), PhysicsMaterialResource()))
            components.set(InteractableComponent())
            components.set(HoverEffectComponent())
        }
        root.addChild(hatch)
        // v0.7.0: the small tank on the right is a real control too: an orange frame round its glass and its name.
        val g = CabinLayout.tankGlassCorners()
        val out = (CabinLayout.tankCentre() * -1.0).normalized() * 0.012
        for (k in 0 until 4) {
            val a = g[k] + out; val b = g[(k + 1) % 4] + out
            val d = b - a
            tankFrame.addChild(ModelEntity(MeshResource.createBox(Vector3(1f, 1f, 1f), 0f), orangeMat).apply {
                setPos(((a + b) * 0.5).toVector3()); transform().setQuaternion(quatFromY(d)); transform().setScaleVector(Vector3(0.008f, d.length().toFloat() + 0.008f, 0.008f))
            })
        }
        val top = (g.maxByOrNull { it.y }!! + g.sortedByDescending { it.y }[1]) * 0.5
        tankFrame.addChild(label("小水缸：捏水里一处，潜水器开过去").apply {
            setPos((top + Vec3(0.0, 0.035, 0.0) + out).toVector3()); transform().eulerAngles = EulerAngles(0f, CabinLayout.tankViewYaw.toFloat(), 0f)
        })
        desk.addChild(console)
        // The desk's front panel down to the floor, and the floor.
        desk.addChild(ModelEntity(MeshResource.createBox(Vector3(1.2f, 0.7f, 0.04f), 0.01f), consoleMat).apply { setPos(Vector3(0f, -0.95f, CabinLayout.FRONT_PLATE_Z.toFloat())) })
        desk.addChild(ModelEntity(MeshResource.createBox(Vector3(2.2f, 0.04f, 2.2f), 0.01f), hullMat).apply { setPos(Vector3(0f, -1.2f, -0.2f)) })
        // v0.6.0: instrument lights in a row along the top of the front plate (below the deck's panels, never under them).
        val ledMesh = MeshResource.createBox(Vector3(0.018f, 0.01f, 0.006f), 0.002f).shared()
        for (k in 0 until 14) {
            val e = ModelEntity(ledMesh, ledMats[(k * 7 + k / 3) % ledMats.size]).apply {
                setPos(Vector3(-0.46f + 0.92f * k / 13, -0.66f, (CabinLayout.FRONT_PLATE_Z + 0.024).toFloat()))
            }
            desk.addChild(e)
            leds += e to k
        }
    }

    /** v0.7.0: the cabin dressed as a real sphere (render.CabinDressing), once its models are loaded; the hull gets its lining. */
    fun dress(models: Map<String, Entity>) {
        if (dressing != null) return
        dressing = CabinDressing(root, models)
        hullMat.setBaseColorTexture(CabinDressing.liningTexture().also { hullMat.setEmissiveTexture(it) })
        hullMat.setBaseColor(color(1f, 1f, 1f))
        hullMat.setMetallic(0.15f)
        hullMat.setRoughness(0.75f)
        night = -1f
    }

    /** A real control's name: cream on dark with an orange edge (only real controls have one). */
    private fun label(text: String): Entity {
        val px = 64
        val ink = android.graphics.Paint().apply { isAntiAlias = true; color = 0xFFFFF1DC.toInt(); textSize = px * 0.6f; typeface = android.graphics.Typeface.DEFAULT_BOLD; textAlign = android.graphics.Paint.Align.CENTER }
        val w = (ink.measureText(text) + px).toInt()
        val b = Bitmap.createBitmap(w, px, Bitmap.Config.ARGB_8888)
        Canvas(b).apply {
            val r = android.graphics.RectF(2f, 2f, w - 2f, px - 2f)
            drawRoundRect(r, px / 2.2f, px / 2.2f, Paint().apply { isAntiAlias = true; color = 0xEE1A1410.toInt() })
            drawRoundRect(r, px / 2.2f, px / 2.2f, Paint().apply { isAntiAlias = true; style = Paint.Style.STROKE; strokeWidth = 4f; color = 0xFFFF8C1F.toInt() })
            drawText(text, w / 2f, px * 0.7f, ink)
        }
        val h = 0.026f
        val mat = UnlitMaterial.create(BlendingMode.MASKED).apply { setBaseColorTexture(TextureResource.create(b)); setOpacityThreshold(0.3f) }
        return ModelEntity(MeshResource.createPlane(h * w / px, h, 0f), mat)
    }

    /** The handle follows the pull ([metres] down, up to 12 cm). */
    fun pullHatch(metres: Double) { hatch.setPos(Vector3(0f, (0.38 - metres.coerceIn(0.0, 0.12)).toFloat(), -0.18f)) }

    /**
     * Once a frame while the cockpit is shown: [depth] from 0 at the surface to 1 at the sea floor, [lamp] on or off,
     * ([dx], [dy]) how far the sea moved past the windows this frame (metres; the opposite of the sub's move).
     */
    fun frame(dt: Double, depth: Double, lamp: Boolean, dx: Double, dy: Double) {
        clock += dt
        dressing?.frame(dt)
        // The real controls breathe (the glow round the hatch handle).
        val br = (0.25 + 0.15 * sin(clock * 3.0)).toFloat()
        orangeGlowMat.setBaseColor(color(CabinDecor.ORANGE[0] * br, CabinDecor.ORANGE[1] * br, CabinDecor.ORANGE[2] * br, br))
        val dbg = debug
        if (dbg != debugDone) {
            debugDone = dbg
            val opts = dbg.orEmpty().split(";").mapNotNull { it.split("=").takeIf { kv -> kv.size == 2 }?.let { kv -> kv[0].trim() to kv[1].trim() } }.toMap()
            val hide = opts["hide"].orEmpty().split(",").map { it.trim() }.toSet()
            hull.enabled = "hull" !in hide; rims.enabled = "rims" !in hide; desk.enabled = "desk" !in hide
            backdrop.enabled = "backdrop" !in hide; motes.entity.enabled = "snow" !in hide && "motes" !in hide
            backdrop.setScale(opts["bd"]?.toFloatOrNull() ?: BACKDROP)
            android.util.Log.i("DeepCabin", "debug $dbg")
        }
        val c = deepColor(depth)
        water.setBaseColor(color(c[0], c[1], c[2]))
        val sun = (1 - depth / 0.32).coerceIn(0.0, 1.0).toFloat()
        sunDisc.enabled = sun > 0.01f
        sunMat.setBaseColor(color(sun * 0.9f, sun * 0.95f, sun, sun))
        if ("sun" in debugDone.orEmpty()) sunDisc.enabled = false
        if (beamsOn != lamp) { beamsOn = lamp; beams.enabled = lamp }
        // The beams show more the darker the sea (in sunlight they are hardly needed).
        if (lamp) { val a = (0.10 + 0.10 * depth.coerceIn(0.0, 1.0)).toFloat(); beamMat.setBaseColor(color(0.8f * a, 0.9f * a, a, a)) }
        motes.frame(dx, dy, dt)
        // v0.6.0: the deep's own light, and the cabin's.
        val dark = smooth((depth - 0.28) / 0.35)
        glimmerMat.setBaseColor(color(0.12f * dark, 0.75f * dark, 0.7f * dark, dark))
        glimmers.entity.enabled = dark > 0.02f
        if (glimmers.entity.enabled) glimmers.frame(dx, dy, dt)
        val far = (1 - 0.9 * depth.coerceIn(0.0, 1.0)).toFloat()
        farMat.setBaseColor(color(0.2f * far, 0.25f * far, 0.3f * far, far))
        farMotes.frame(dx, dy, dt)
        val shimmer = smooth((0.3 - depth) / 0.3)
        for ((k, r) in ripples.withIndex()) {
            r.enabled = shimmer > 0.01f
            if (!r.enabled) continue
            r.transform().eulerAngles = EulerAngles(90f, (30 * k + clock * (if (k == 0) 2.2 else -1.7)).toFloat(), 0f)
            r.setPos(Vector3((0.6 * sin(clock * 0.21 + k)).toFloat(), 6.5f + 0.4f * k, (-11 + 0.6 * cos(clock * 0.17 + 2 * k)).toFloat()))
        }
        rippleMat.setBaseColor(color(0.35f * shimmer, 0.5f * shimmer, 0.55f * shimmer, shimmer))
        lampLight.components[com.pico.spatial.core.ecs.SpotLightComponent::class.java]?.intensity = if (lamp) (2500 + 5500 * depth.coerceIn(0.0, 1.0)).toFloat() else 0f
        val n = smooth((depth - 0.45) / 0.25)
        if (kotlin.math.abs(n - night) > 0.01f) {
            night = n
            // v0.7.0: the lining's own texture is the emission, so it shows in the dark; reddish at night.
            val k = if (dressing != null) 7f else 1f
            hullMat.setEmissiveColor(color(k * (0.035f + 0.045f * n), k * (0.045f - 0.03f * n), k * (0.06f - 0.048f * n)))
            consoleMat.setEmissiveColor(color(0.02f + 0.03f * n, 0.025f - 0.015f * n, 0.03f - 0.022f * n))
            redLamp.components[com.pico.spatial.core.ecs.PointLightComponent::class.java]?.intensity = 60f * n
        }
        for ((e, k) in leds) {
            val on = sin(clock * (0.6 + 0.37 * (k % 5)) + k * 1.9) > -0.3 || k % 4 == 0
            if (e.enabled != on) e.enabled = on
        }
    }

    private fun smooth(x: Double): Float { val t = x.coerceIn(0.0, 1.0); return (t * t * (3 - 2 * t)).toFloat() }

    private fun softDisc(): Entity = Entity().apply {
        val q = quad(9f)
        addChild(ModelEntity(q, sunMat).apply { setPos(Vector3(0f, 7f, -2f)); transform().eulerAngles = EulerAngles(90f, 0f, 0f) })
    }

    private fun setMaterials(e: Entity, m: com.pico.spatial.core.ecs.resource.Material) {
        e.components[com.pico.spatial.core.ecs.ModelComponent::class.java]?.materials?.let { mats -> for (i in 0 until mats.size) mats[i] = m }
        e.getChildren().forEach { setMaterials(it, m) }
    }

    private class Window(val dir: Vec3, val half: Double)

    companion object {
        /** The hull's radius about the eyes (meters). */
        const val RH = 1.5
        /** The sea all round: its radius (meters). */
        const val BACKDROP = 28f
        // One big front viewport to watch the animal through, and two small side ports.
        private val WINDOWS = listOf(
            Window(Vec3(0.0, -0.02, -1.0), Math.toRadians(44.0)),
            Window(Vec3(-0.9, 0.0, -0.45), Math.toRadians(18.0)),
            Window(Vec3(0.9, 0.0, -0.45), Math.toRadians(18.0)),
        )

        /** The sea's colour by depth (0 surface, 1 floor): sunlit blue, twilight, midnight, abyss. */
        fun deepColor(depth: Double): FloatArray {
            val stops = listOf(0.0 to floatArrayOf(0.2f, 0.62f, 0.8f), 0.25 to floatArrayOf(0.06f, 0.3f, 0.52f), 0.5 to floatArrayOf(0.015f, 0.07f, 0.17f), 0.75 to floatArrayOf(0.004f, 0.016f, 0.045f), 1.0 to floatArrayOf(0.002f, 0.006f, 0.014f))
            val d = depth.coerceIn(0.0, 1.0)
            val i = stops.indexOfLast { it.first <= d }.coerceAtMost(stops.size - 2)
            val (a, ca) = stops[i]
            val (b, cb) = stops[i + 1]
            val t = ((d - a) / (b - a)).toFloat()
            return FloatArray(3) { ca[it] + (cb[it] - ca[it]) * t }
        }

        /** A sphere about the eyes with round holes for the windows, its faces seen from inside. */
        private fun hullMesh(): MeshResource {
            val nu = 128
            val nv = 64
            val pos = ArrayList<Vector3>(); val nor = ArrayList<Vector3>(); val tan = ArrayList<Vector4>(); val uv = ArrayList<Vector2>()
            val idx = ArrayList<Int>()
            fun dir(i: Int, j: Int): Vec3 {
                val th = Math.PI * j / nv
                val ph = 2 * Math.PI * i / nu
                return Vec3(sin(th) * cos(ph), cos(th), sin(th) * sin(ph))
            }
            // Corners that fall in a window are moved out onto its edge circle, so each hole is round and the rim covers
            // its edge exactly (cutting whole quads left a stepped edge with the sea showing through the steps).
            fun snap(c: Vec3): Pair<Vec3, Boolean> {
                for (w in WINDOWS) {
                    val a = w.dir.normalized()
                    val k = c.dot(a)
                    if (acos(k.coerceIn(-1.0, 1.0)) >= w.half) continue
                    val side = c - a * k
                    if (side.length() < 1e-9) return c to true
                    return (a * cos(w.half) + side.normalized() * sin(w.half)) to true
                }
                return c to false
            }
            for (j in 0 until nv) for (i in 0 until nu) {
                val corners = listOf(i to j, i + 1 to j, i + 1 to j + 1, i to j + 1).map { (a, b) -> snap(dir(a, b)) }
                if (corners.all { it.second }) continue
                val base = pos.size
                for ((k, c) in corners.withIndex()) {
                    val d = c.first
                    val (a, b) = listOf(i to j, i + 1 to j, i + 1 to j + 1, i to j + 1)[k]
                    pos += Vector3((d.x * RH).toFloat(), (d.y * RH).toFloat(), (d.z * RH).toFloat())
                    nor += Vector3(-d.x.toFloat(), -d.y.toFloat(), -d.z.toFloat())
                    tan += Vector4(1f, 0f, 0f, 1f)
                    uv += Vector2(a.toFloat() / nu, b.toFloat() / nv)
                }
                idx += listOf(base, base + 2, base + 1, base, base + 3, base + 2)
            }
            val r = RH.toFloat()
            return MeshResource.createWithMeshModel(MeshModel(pos, idx, nor, tan, uv), BoundingBox(Vector3(0f, 0f, 0f), Vector3(r, r, r)), "cockpit_hull_v3").shared()
        }

        private fun quad(s: Float): MeshResource {
            val h = s / 2
            return MeshResource.createWithMeshModel(
                MeshModel(
                    listOf(Vector3(-h, -h, 0f), Vector3(h, -h, 0f), Vector3(h, h, 0f), Vector3(-h, h, 0f)), listOf(0, 1, 2, 0, 2, 3),
                    List(4) { Vector3(0f, 0f, 1f) }, List(4) { Vector4(1f, 0f, 0f, 1f) }, listOf(Vector2(0f, 0f), Vector2(1f, 0f), Vector2(1f, 1f), Vector2(0f, 1f)),
                ),
                // BoundingBox(a, b) is centre and half size (v0.5; corners made it cull when looked at from some sides).
                BoundingBox(Vector3(0f, 0f, 0f), Vector3(h, h, 0.001f)), "cockpit_sun",
            ).shared()
        }

        /** Vertical gradient for the sea round the sub: light from above, dark below. */
        /** v0.6.0: a net of bright wavering lines, like sunlight through ripples seen from below (seamless, 256²). */
        private fun caustics(): Bitmap {
            val n = 256
            val b = Bitmap.createBitmap(n, n, Bitmap.Config.ARGB_8888)
            val tau = 2 * Math.PI / n
            for (y in 0 until n) for (x in 0 until n) {
                var w = 0.0
                for ((fx, fy, ph) in listOf(Triple(3, 1, 0.3), Triple(-2, 3, 1.1), Triple(1, -4, 2.0), Triple(5, 2, 0.7), Triple(-4, -3, 2.6))) w += sin(tau * (fx * x + fy * y) + ph + 0.8 * sin(tau * (fy * x - fx * y)))
                val line = kotlin.math.exp(-(w * w) * 2.2)
                // Fade toward the edges of the plane, so its square never shows.
                val ex = sin(Math.PI * x / n); val ey = sin(Math.PI * y / n)
                val a = (line * ex * ey * 255).toInt().coerceIn(0, 255)
                b.setPixel(x, y, android.graphics.Color.argb(255, a, a, a))
            }
            return b
        }

        private fun gradient(): Bitmap {
            val b = Bitmap.createBitmap(16, 256, Bitmap.Config.ARGB_8888)
            Canvas(b).drawRect(0f, 0f, 16f, 256f, Paint().apply {
                shader = LinearGradient(0f, 0f, 0f, 256f, intArrayOf(0xFFFFFFFF.toInt(), 0xFFB8C4CC.toInt(), 0xFF5E6870.toInt(), 0xFF2A3036.toInt()), floatArrayOf(0f, 0.35f, 0.65f, 1f), Shader.TileMode.CLAMP)
            })
            return b
        }

        private fun radial(): Bitmap {
            val n = 256
            val b = Bitmap.createBitmap(n, n, Bitmap.Config.ARGB_8888)
            val m = n / 2f
            Canvas(b).drawRect(0f, 0f, n.toFloat(), n.toFloat(), Paint().apply {
                shader = RadialGradient(m, m, m, intArrayOf(0xFFFFFFFF.toInt(), 0xFF6FA8C8.toInt(), 0xFF000000.toInt()), floatArrayOf(0f, 0.4f, 1f), Shader.TileMode.CLAMP)
            })
            return b
        }
    }
}

