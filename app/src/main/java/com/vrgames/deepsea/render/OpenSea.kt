package com.vrgames.deepsea.render

import com.pico.spatial.core.ecs.Entity
import com.pico.spatial.core.ecs.resource.BlendingMode
import com.pico.spatial.core.ecs.resource.MaterialCullingMode
import com.pico.spatial.core.ecs.resource.MeshResource
import com.pico.spatial.core.ecs.resource.PhysicallyBasedMaterial
import com.pico.spatial.core.ecs.resource.TextureResource
import com.pico.spatial.core.ecs.resource.UnlitMaterial
import com.pico.spatial.core.math.EulerAngles
import com.pico.spatial.core.math.Vector3
import com.vrgames.deepsea.core.CabinMotion
import com.vrgames.deepsea.core.Vec3
import kotlin.math.cos
import kotlin.math.sin

/**
 * v0.5 (PICO 1118: 「海内的环境沉浸感差」): the open sea round the cabin in Full Space, beyond what the tank holds — shafts
 * of sunlight slanting down near the surface and fading with depth, a school of silver fish wheeling past the windows,
 * bubbles going up, the sea floor coming up below as the sub nears the bottom. All centred on the player's eyes.
 */
class OpenSea(sand: TextureResource?) {
    val root = Entity()
    private val shafts = ArrayList<Pair<Entity, UnlitMaterial>>()
    /** v0.5.2: each shaft's place in the sea (x across, z ahead), which the sub's sideways place shifts. */
    private val shaftAt = ArrayList<Pair<Double, Double>>()
    private val fish = ArrayList<Entity>()
    /** v0.5.2: each rock's place in the sea. */
    private class Rock(val e: Entity, val x: Double, val z: Double, val r: Double)
    private val rockAt = ArrayList<Rock>()
    private val bubbles = Sparks("bubbles", 160, MeshResource.createSphere(1f).shared(), pbr(color(0.85f, 0.95f, 1f, 0.55f), roughness = 0.1f, blending = BlendingMode.TRANSPARENT).shared())
    private val floorMat: PhysicallyBasedMaterial = PhysicallyBasedMaterial.create().apply {
        sand?.let { setBaseColorTexture(it); setEmissiveTexture(it) }
        setBaseColor(color(0.55f, 0.52f, 0.46f))
        setEmissiveColor(color(0.05f, 0.07f, 0.09f))
        setRoughness(1f)
    }
    private val floor = model(MeshResource.createPlane(60f, 60f, 0f), floorMat).apply { transform().eulerAngles = EulerAngles(-90f, 0f, 0f) }
    private val rocks = Entity()
    private var clock = 0.0

    init {
        root.addChild(floor)
        root.addChild(rocks)
        root.addChild(bubbles.entity)
        val rnd = java.util.Random(11)
        // Shafts of light: long thin cones from far overhead, pointing down. v0.5.2: in a band ahead of the windows,
        // [SHAFT_PERIOD] m long, that slides past as the sub moves sideways and comes round again.
        for (k in 0 until 9) {
            val x = -SHAFT_PERIOD / 2 + SHAFT_PERIOD * (k + rnd.nextDouble() * 0.7) / 9
            val z = -(4.0 + rnd.nextDouble() * 9)
            shaftAt += x to z
            val m = UnlitMaterial.create(BlendingMode.TRANSPARENT).apply {
                setBaseColor(color(0.75f, 0.92f, 1f, 1f)); setOpacity(0f); setDepthWrite(false); setCullingMode(MaterialCullingMode.NONE)
            }
            val e = model(MeshResource.createCone(1f, 1f).shared(), m).apply {
                setPos(Vector3(x.toFloat(), 4f, z.toFloat()))
                transform().setScaleVector(Vector3((1.2 + rnd.nextDouble()).toFloat(), 22f, (1.2 + rnd.nextDouble()).toFloat()))
                transform().eulerAngles = EulerAngles((6 * rnd.nextGaussian()).toFloat(), 0f, (6 * rnd.nextGaussian()).toFloat())
            }
            root.addChild(e)
            shafts += e to m
        }
        // A school of small silver fish.
        val body = MeshResource.createCapsule(0.16f, 0.025f).shared()
        val silver = pbr(color(0.72f, 0.78f, 0.84f), metallic = 0.7f, roughness = 0.25f).shared()
        for (k in 0 until 42) {
            val f = Entity()
            f.addChild(model(body, silver).apply { transform().eulerAngles = EulerAngles(0f, 0f, 90f) })
            root.addChild(f)
            fish += f
        }
        // Rocks on the floor, near and far.
        val stone = pbr(color(0.24f, 0.26f, 0.28f), roughness = 0.95f).shared()
        val ball = MeshResource.createSphere(1f).shared()
        for (k in 0 until 26) {
            val a = rnd.nextDouble() * 2 * Math.PI
            val r = 3 + rnd.nextDouble() * 22
            val sx = 0.4 + rnd.nextDouble() * 1.6
            val sy = 0.3 + rnd.nextDouble() * 0.9
            val sz = 0.4 + rnd.nextDouble() * 1.4
            val rock = model(ball, stone).apply {
                setPos(Vector3((r * sin(a)).toFloat(), 0f, (-r * cos(a)).toFloat()))
                transform().setScaleVector(Vector3(sx.toFloat(), sy.toFloat(), sz.toFloat()))
            }
            rocks.addChild(rock)
            rockAt += Rock(rock, r * sin(a), -r * cos(a), maxOf(sx, sz))
        }
    }

    /** v0.5.2: two schools, each fixed at a place in the sea (x, y about the sub's start, z ahead), wheeling about it. */
    private val schools = listOf(Triple(-3.0, 0.5, -7.0), Triple(5.0, -1.5, -9.5))
    private var lastSide = Double.NaN
    /** For the log: where the nearer school is now, about the eyes. */
    var schoolX = 0.0; private set

    /**
     * Each frame: [eyeY] the eyes' height, [depth] 0 at the surface .. 1 at the bottom, [floorBelow] metres from the eyes
     * down to the sea floor (as large as the sea is shown), [moving] how fast the sub goes (m/s at this scale), [sideX] and
     * [upY] the sub's place in the sea as large as it is shown (v0.5.2: what is fixed in the sea is shifted by it, so the
     * sub passes it).
     */
    fun frame(dt: Double, eyeY: Float, depth: Double, floorBelow: Double, moving: Double, sideX: Double, upY: Double) {
        clock += dt
        if (sideX != lastSide) {
            lastSide = sideX
            for ((k, s) in shafts.withIndex()) {
                val (x, z) = shaftAt[k]
                s.first.setPos(Vector3(CabinMotion.wrap(x - sideX, SHAFT_PERIOD).toFloat(), 4f, z.toFloat()))
            }
            for (k in rockAt) {
                val x = CabinMotion.wrap(k.x - sideX, ROCK_PERIOD)
                k.e.setPos(Vector3(x.toFloat(), 0f, k.z.toFloat()))
                // Slid in under the cabin, a rock would come up through its floor: not shown there.
                val clear = kotlin.math.hypot(x, k.z) > k.r + 1.6
                if (k.e.enabled != clear) k.e.enabled = clear
            }
        }
        // Light from the surface: strong in the sunlit zone, gone by the twilight.
        val sun = (1 - depth / 0.35).coerceIn(0.0, 1.0)
        for ((k, s) in shafts.withIndex()) {
            val e = s.first
            e.enabled = sun > 0.01
            if (!e.enabled) continue
            s.second.setOpacity((0.07 * sun * (0.75 + 0.25 * sin(clock * 0.4 + k))).toFloat())
            e.transform().eulerAngles = EulerAngles((4 * sin(clock * 0.13 + k)).toFloat(), 0f, (4 * cos(clock * 0.11 + k * 1.7)).toFloat())
        }
        // v0.5.2 (device 2026-11-19: the school went round the cabin whatever the sub did): two schools, each wheeling
        // about its own place in the sea, which the sub's moves shift past the windows (coming round again every
        // [SCHOOL_PERIOD_X] m across and [SCHOOL_PERIOD_Y] m up and down, fading out near the ends); thinner in the dark.
        val school = (1 - depth / 0.7).coerceIn(0.0, 1.0)
        val per = fish.size / schools.size
        for ((k, f) in fish.withIndex()) {
            val sc = schools[(k / per).coerceAtMost(schools.size - 1)]
            val j = k % per
            val on = j < (per * school).toInt()
            val cx = CabinMotion.wrap(sc.first - sideX, SCHOOL_PERIOD_X)
            val cy = CabinMotion.wrap(sc.second - upY, SCHOOL_PERIOD_Y)
            if (k == 0) schoolX = cx
            val fade = (minOf(SCHOOL_PERIOD_X / 2 - kotlin.math.abs(cx), SCHOOL_PERIOD_Y / 2 - kotlin.math.abs(cy)) / 1.5).coerceIn(0.0, 1.0)
            val shown = on && fade > 0.05
            if (f.enabled != shown) f.enabled = shown
            if (!shown) continue
            val ph = clock * 0.35 + j * 0.15
            val r = 1.5 + 0.5 * sin(j * 1.3)
            val x = cx + r * cos(ph)
            val z = sc.third + r * sin(ph)
            val y = eyeY + cy + 0.6 * sin(j * 0.7) + 0.25 * sin(ph * 2)
            f.setPos(Vector3(x.toFloat(), y.toFloat(), z.toFloat()))
            f.transform().eulerAngles = EulerAngles(0f, Math.toDegrees(-ph).toFloat() + 180f, (8 * sin(clock * 6 + k)).toFloat())
            f.transform().setScaleVector(Vector3(fade.toFloat()))
        }
        // Bubbles past the windows, more when the sub moves.
        if ((clock * (3 + 6 * moving)).toInt() != ((clock - dt) * (3 + 6 * moving)).toInt()) {
            val r = java.util.Random()
            bubbles.emit(Vec3((r.nextDouble() - 0.5) * 4, eyeY - 1.5, -1.8 - r.nextDouble() * 3), 1, 0.02, 6.0, (0.008 + 0.02 * r.nextDouble()).toFloat(), Vec3(0.0, 0.35, 0.0))
        }
        bubbles.update(dt, 0.02)
        // The floor and its rocks, as far below as the bottom is.
        val y = (eyeY - floorBelow).toFloat()
        // v0.5.2: the floor slides under the sub as it moves sideways (it is 60 m wide; the sub's whole way across the
        // sea is under 10 m at this size, so its edge never shows).
        floor.setPos(Vector3((-sideX).toFloat(), y, -6f))
        rocks.setPos(Vector3(0f, y, 0f))
        val near = floorBelow < 14
        if (floor.enabled != near) { floor.enabled = near; rocks.enabled = near }
    }

    companion object {
        /** v0.5.2: the light shafts come round again every this many metres sideways, the rocks every [ROCK_PERIOD]. */
        const val SHAFT_PERIOD = 18.0
        const val ROCK_PERIOD = 50.0
        const val SCHOOL_PERIOD_X = 24.0
        const val SCHOOL_PERIOD_Y = 9.0
    }
}
