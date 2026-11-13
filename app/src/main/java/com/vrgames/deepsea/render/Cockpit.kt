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
    private val lampOn = pbr(color(1f, 0.85f, 0.4f), roughness = 0.3f, emissive = color(1f, 0.75f, 0.3f)).shared()
    private val lampOff = pbr(color(0.25f, 0.22f, 0.18f), roughness = 0.5f, emissive = color(0.05f, 0.04f, 0.02f)).shared()
    private val dialMat = pbr(color(0.12f, 0.2f, 0.26f), roughness = 0.3f, emissive = color(0.08f, 0.3f, 0.42f)).shared()

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
    private val snow = Sparks("cockpit_snow", 180, sphere, glow(color(0.85f, 0.9f, 1f, 0.55f)).shared()).also { root.addChild(it.entity) }

    /** The lamp switch on the console (pinch it). */
    val lampButton: Entity
    private val lampCap: Entity
    private var lampShown: Boolean? = null
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
        // The pilot's console under the big front window: a desk across the cabin, its top tipped up toward the player,
        // with the screen panel (an attachment the stage puts on it) in the middle, the lamp switch at the right and two
        // gauges at the left. Side pillars and a floor make it read as the inside of a sub, not a ball.
        val console = Entity().apply { setPos(Vector3(0f, -0.5f, -0.6f)); transform().eulerAngles = EulerAngles(30f, 0f, 0f) }
        console.addChild(ModelEntity(MeshResource.createBox(Vector3(1.2f, 0.05f, 0.42f), 0.02f), consoleMat))
        console.addChild(ModelEntity(MeshResource.createBox(Vector3(1.24f, 0.03f, 0.03f), 0.01f), rimMat).apply { setPos(Vector3(0f, 0.03f, 0.21f)) })
        // createCylinder takes height, then radius: two small gauge faces.
        for (k in 0 until 2) console.addChild(ModelEntity(MeshResource.createCylinder(0.012f, 0.032f), dialMat).apply { setPos(Vector3(-0.46f + k * 0.1f, 0.03f, 0.0f)) })
        lampCap = ModelEntity(MeshResource.createCylinder(0.03f, 0.05f), lampOff).apply { setPos(Vector3(0.46f, 0.035f, 0.02f)) }
        lampButton = Entity().apply {
            addChild(lampCap)
            components.set(CollisionComponent(listOf(ShapeResource.createBox(Vector3(0.16f, 0.1f, 0.16f)).offsetByTranslation(Vector3(0.46f, 0.04f, 0.02f))), PhysicsMaterialResource()))
            components.set(InteractableComponent())
            components.set(HoverEffectComponent())
        }
        console.addChild(lampButton)
        desk.addChild(console)
        // The desk's front panel down to the floor, and the floor.
        desk.addChild(ModelEntity(MeshResource.createBox(Vector3(1.2f, 0.7f, 0.04f), 0.01f), consoleMat).apply { setPos(Vector3(0f, -0.95f, -0.78f)) })
        desk.addChild(ModelEntity(MeshResource.createBox(Vector3(2.2f, 0.04f, 2.2f), 0.01f), hullMat).apply { setPos(Vector3(0f, -1.2f, -0.2f)) })
    }

    /**
     * Once a frame while the cockpit is shown: [depth] from 0 at the surface to 1 at the sea floor,
     * [lamp] on or off, [moving] the sub's speed (the snow streams past faster).
     */
    fun frame(dt: Double, depth: Double, lamp: Boolean, moving: Double) {
        clock += dt
        val dbg = debug
        if (dbg != debugDone) {
            debugDone = dbg
            val opts = dbg.orEmpty().split(";").mapNotNull { it.split("=").takeIf { kv -> kv.size == 2 }?.let { kv -> kv[0].trim() to kv[1].trim() } }.toMap()
            val hide = opts["hide"].orEmpty().split(",").map { it.trim() }.toSet()
            hull.enabled = "hull" !in hide; rims.enabled = "rims" !in hide; desk.enabled = "desk" !in hide
            backdrop.enabled = "backdrop" !in hide; snow.entity.enabled = "snow" !in hide
            backdrop.setScale(opts["bd"]?.toFloatOrNull() ?: BACKDROP)
            android.util.Log.i("DeepCabin", "debug $dbg")
        }
        val c = deepColor(depth)
        water.setBaseColor(color(c[0], c[1], c[2]))
        val sun = (1 - depth / 0.32).coerceIn(0.0, 1.0).toFloat()
        sunDisc.enabled = sun > 0.01f
        sunMat.setBaseColor(color(sun * 0.9f, sun * 0.95f, sun, sun))
        if ("sun" in debugDone.orEmpty()) sunDisc.enabled = false
        if (lampShown != lamp) {
            lampShown = lamp
            setMaterials(lampCap, if (lamp) lampOn else lampOff)
        }
        // More snow the deeper, as the sea outside fills with falling specks.
        val rate = 4 + 18 * depth
        if ((clock * rate).toInt() != ((clock - dt) * rate).toInt()) {
            val r = java.util.Random()
            val at = Vec3((r.nextDouble() - 0.5) * 6, (r.nextDouble() - 0.3) * 4, -1.8 - r.nextDouble() * 5)
            snow.emit(at, 1, 0.01, 7.0, (0.006 + 0.01 * r.nextDouble()).toFloat(), Vec3(0.0, -0.05 - 0.1 * moving, 0.08 * moving))
        }
        snow.update(dt, 0.05)
    }

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
            return MeshResource.createWithMeshModel(MeshModel(pos, idx, nor, tan, uv), BoundingBox(Vector3(-r, -r, -r), Vector3(r, r, r)), "cockpit_hull_v3").shared()
        }

        private fun quad(s: Float): MeshResource {
            val h = s / 2
            return MeshResource.createWithMeshModel(
                MeshModel(
                    listOf(Vector3(-h, -h, 0f), Vector3(h, -h, 0f), Vector3(h, h, 0f), Vector3(-h, h, 0f)), listOf(0, 1, 2, 0, 2, 3),
                    List(4) { Vector3(0f, 0f, 1f) }, List(4) { Vector4(1f, 0f, 0f, 1f) }, listOf(Vector2(0f, 0f), Vector2(1f, 0f), Vector2(1f, 1f), Vector2(0f, 1f)),
                ),
                BoundingBox(Vector3(-h, -h, -0.001f), Vector3(h, h, 0.001f)), "cockpit_sun",
            ).shared()
        }

        /** Vertical gradient for the sea round the sub: light from above, dark below. */
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

