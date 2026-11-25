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
class OpenSea(sand: TextureResource?, private val models: Map<String, Entity> = emptyMap()) {
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
    /** v0.6.0: the sea's own materials, their glow-without-light turned down in the deep so the lamp is what shows them. */
    private var silverMat: PhysicallyBasedMaterial? = null
    private var stoneMat: PhysicallyBasedMaterial? = null
    private var dimShown = -1.0

    /**
     * v0.6.0 (PICO 1124: 「全沉浸模式下环境氛围感和沉浸感不够」): now and then a big animal of the zone the sub is in swims
     * past the front window, life size, 4–7 m out: a manta or a leatherback in the sunlit zone, deep-sea jellies drifting
     * or a school of lanternfish in the twilight, a giant squid in the midnight zone, an anglerfish with its lure lit in the
     * abyss. Each crosses in about 16 s; the next comes [VISIT_GAP] s after.
     */
    private class Visitor(val id: String, val root: Entity, val count: Int, val size: Float, val glows: Boolean, val dur: Double)
    private val visitors = HashMap<String, Visitor>()
    private var visiting: Visitor? = null
    private var visitT = 0.0
    private var visitDir = 1.0
    private var visitZ = -5.0
    private var visitY = 0.0
    private var nextVisit = 12.0
    /** The id of the animal now passing (for the log and the sounds), or null. */
    var passing: String? = null; private set

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
        val silver = pbr(color(0.72f, 0.78f, 0.84f), metallic = 0.7f, roughness = 0.25f).shared().also { silverMat = it }
        for (k in 0 until 42) {
            val f = Entity()
            f.addChild(model(body, silver).apply { transform().eulerAngles = EulerAngles(0f, 0f, 90f) })
            root.addChild(f)
            fish += f
        }
        // Rocks on the floor, near and far.
        val stone = pbr(color(0.24f, 0.26f, 0.28f), roughness = 0.95f).shared().also { stoneMat = it }
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
        buildVisitors()
    }

    private fun buildVisitors() {
        val glowMat = glow(color(0.35f, 0.95f, 0.85f, 0.9f)).shared()
        val lureMat = glow(color(1f, 0.85f, 0.45f, 1f)).shared()
        val spec = listOf(
            // id, how many, size (m, longest side), glows, seconds to cross
            Triple("manta", 1, 3.6f) to (false to 17.0), Triple("turtle", 1, 1.7f) to (false to 15.0),
            Triple("jelly", 6, 0.7f) to (true to 22.0), Triple("lantern", 18, 0.22f) to (true to 12.0),
            Triple("squid", 1, 6.5f) to (false to 18.0), Triple("angler", 1, 1.1f) to (true to 20.0),
        )
        for ((t, g) in spec) {
            val (id, count, size) = t
            val m = models[id] ?: continue
            val r = Entity()
            for (k in 0 until count) {
                // holder: where it is and which way it goes; banker: tipped to show its back, and the wingbeat; body: the model
                // turned to face +x (render.Looks).
                val holder = Entity()
                val banker = Entity()
                val body = m.clone(Entity.CloneOptions(true, true)) ?: continue
                body.setScale(size)
                body.transform().eulerAngles = EulerAngles(0f, Looks.yaw(id), 0f)
                banker.addChild(body)
                holder.addChild(banker)
                if (g.first) banker.addChild(model(MeshResource.createSphere(1f).shared(), if (id == "angler") lureMat else glowMat).apply {
                    setScale(if (id == "angler") 0.05f else size * 0.16f)
                    setPos(Vector3(if (id == "angler") size * 0.55f else 0f, size * (if (id == "jelly") 0.55f else 0.3f), 0f))
                })
                r.addChild(holder)
            }
            r.enabled = false
            root.addChild(r)
            visitors[id] = Visitor(id, r, count, size, g.first, g.second)
        }
    }

    private fun visitorsFor(depth: Double): List<String> = when {
        depth < 0.25 -> listOf("manta", "turtle")
        depth < 0.5 -> listOf("jelly", "lantern")
        depth < 0.75 -> listOf("squid")
        else -> listOf("angler")
    }

    /** Debug: the next visitor now ([id] or the zone's own). */
    fun callVisitor(id: String?) {
        visiting?.root?.enabled = false
        visiting = null; passing = null
        nextVisit = 0.0; forced = id
    }
    private var forced: String? = null

    private fun visit(dt: Double, eyeY: Float, depth: Double) {
        val v = visiting
        if (v == null) {
            nextVisit -= dt
            if (nextVisit > 0) return
            val pick = forced ?: visitorsFor(depth).filter { visitors.containsKey(it) }.randomOrNull() ?: return
            forced = null
            val nv = visitors[pick] ?: return
            visiting = nv; visitT = 0.0
            visitDir = if (Math.random() < 0.5) 1.0 else -1.0
            visitZ = -(4.0 + 3.0 * Math.random()) - nv.size * 0.3
            visitY = -0.4 + 1.2 * Math.random()
            nv.root.enabled = true
            passing = pick
            android.util.Log.i("DeepCabin", "visitor $pick passing, dir $visitDir, ${-visitZ} m out, ${nv.count} of them, ${nv.size} m long")
            return
        }
        visitT += dt
        val u = visitT / v.dur
        if (u >= 1) {
            v.root.enabled = false
            visiting = null; passing = null
            nextVisit = VISIT_GAP * (0.8 + 0.4 * Math.random())
            return
        }
        // Across from one side to the other, easing in and out, a slow rise and fall on the way.
        val span = 9.0 + v.size
        val x = visitDir * (-span + 2 * span * u)
        val kids = v.root.getChildren()
        for ((k, h) in kids.withIndex()) {
            val off = if (v.count == 1) Vec3.ZERO else Vec3(1.1 * sin(k * 2.3) * v.size * 3, 0.9 * sin(k * 1.7) * v.size * 2.5 + 0.15 * sin(clock * 0.8 + k), 0.8 * cos(k * 1.1) * v.size * 3)
            val bob = 0.25 * sin(clock * 0.6 + k) * (if (v.id == "jelly") 2.0 else 1.0)
            h.setPos(Vector3((x + off.x).toFloat(), (eyeY + visitY + off.y + bob).toFloat(), (visitZ + off.z).toFloat()))
            // Facing the way it goes; tipped so its back shows toward the cabin (the holder's x turns with it, so the sign
            // follows the way), the manta's wings beating as a slow roll.
            val flap = if (v.id == "manta") 10 * sin(clock * 1.6) else if (v.id == "jelly") 0.0 else 3 * sin(clock * 3 + k)
            h.transform().eulerAngles = EulerAngles(0f, if (visitDir > 0) 0f else 180f, 0f)
            h.getChildren().firstOrNull()?.transform()?.eulerAngles = EulerAngles(((Looks.bank(v.id) * 0.5 + flap) * visitDir).toFloat(), 0f, 0f)
            // Fade in and out at the ends of the crossing.
            val f = (minOf(u, 1 - u) * 8).coerceIn(0.0, 1.0)
            h.transform().setScaleVector(Vector3((0.2 + 0.8 * f).toFloat()))
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
        visit(dt, eyeY, depth)
        // v0.6.0: in the deep, the fish, the rocks and the floor show by the lamp, not by a glow of their own.
        val dim = (1 - 0.85 * ((depth - 0.2) / 0.5).coerceIn(0.0, 1.0))
        if (kotlin.math.abs(dim - dimShown) > 0.02) {
            dimShown = dim
            val a = (0.45 * dim).toFloat()
            silverMat?.setEmissiveColor(color(0.72f * a, 0.78f * a, 0.84f * a))
            stoneMat?.setEmissiveColor(color(0.24f * a, 0.26f * a, 0.28f * a))
            floorMat.setEmissiveColor(color(0.05f * dim.toFloat(), 0.07f * dim.toFloat(), 0.09f * dim.toFloat()))
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
        /** v0.6.0: seconds between two big animals passing the windows (about). */
        const val VISIT_GAP = 30.0
    }
}
