package com.vrgames.deepsea.render

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import com.pico.spatial.core.ecs.Entity
import com.pico.spatial.core.ecs.ModelEntity
import com.pico.spatial.core.ecs.resource.BlendingMode
import com.pico.spatial.core.ecs.resource.MaterialCullingMode
import com.pico.spatial.core.ecs.resource.MeshResource
import com.pico.spatial.core.ecs.resource.TextureResource
import com.pico.spatial.core.ecs.resource.UnlitMaterial
import com.pico.spatial.core.math.EulerAngles
import com.pico.spatial.core.math.Vector3
import com.vrgames.deepsea.core.CabinDecor
import com.vrgames.deepsea.core.CabinLayout
import com.vrgames.deepsea.core.Vec3
import kotlin.math.cos
import kotlin.math.sin

/**
 * v0.7.0 (the user, 2026-11-25: the cabin did not look like a whole submersible, least of all the pilot's station): the
 * dressing of a real manned sphere round the eyes — quilted insulation on the hull (render.Cockpit takes [liningTexture]),
 * ribs and rings, cable runs with clamps, pipes and a valve, ceiling work lights, grab rails, the pilot's seat, side desks
 * with joystick, throttles, arm master and radio, guarded switches, knobs, a live sonar, oxygen bottles, the CO2 scrubber,
 * the extinguisher and the first-aid kit, and plates naming them.
 *
 * Nothing here takes a pinch (no collider, no hover), nothing glows orange: only the real controls do (core.CabinDecor).
 * Where things go: core.CabinDecor (tested: out of the windows, the tank, the console copy and the face).
 */
class CabinDressing(private val root: Entity, private val models: Map<String, Entity>) {
    private val steel = pbr(color(0.36f, 0.39f, 0.43f), metallic = 0.85f, roughness = 0.35f, emissive = color(0.03f, 0.033f, 0.038f)).shared()
    private val darkSteel = pbr(color(0.13f, 0.15f, 0.18f), metallic = 0.6f, roughness = 0.45f, emissive = color(0.02f, 0.024f, 0.03f)).shared()
    private val copper = pbr(color(0.62f, 0.38f, 0.24f), metallic = 0.9f, roughness = 0.3f, emissive = color(0.05f, 0.03f, 0.02f)).shared()
    private val rubber = pbr(color(0.05f, 0.05f, 0.055f), metallic = 0f, roughness = 0.85f, emissive = color(0.008f, 0.008f, 0.01f)).shared()
    private val cableGrey = pbr(color(0.28f, 0.3f, 0.32f), metallic = 0f, roughness = 0.7f, emissive = color(0.02f, 0.02f, 0.024f)).shared()
    private val cableBlue = pbr(color(0.12f, 0.22f, 0.4f), metallic = 0f, roughness = 0.65f, emissive = color(0.01f, 0.02f, 0.04f)).shared()
    private val guardRed = pbr(color(0.85f, 0.12f, 0.1f, 0.55f), metallic = 0f, roughness = 0.2f, emissive = color(0.08f, 0.01f, 0.01f), blending = BlendingMode.TRANSPARENT).shared()
    private val lampWhite = glow(color(0.85f, 0.93f, 1f, 0.9f)).shared()
    private val sonarGreen = glow(color(0.2f, 1f, 0.55f, 0.75f)).shared()
    private val sonarDim = glow(color(0.1f, 0.5f, 0.28f, 0.35f)).shared()
    private val sonarFace = unlit(color(0.01f, 0.06f, 0.035f)).shared()
    /** Indicator dots: green, cyan, white, red — never amber or orange (the real controls' colour). */
    private val dots = listOf(color(0.3f, 1f, 0.45f), color(0.35f, 0.85f, 1f), color(0.9f, 0.95f, 1f), color(1f, 0.25f, 0.2f)).map { glow(it).shared() }
    private val blink = ArrayList<Pair<Entity, Int>>()
    private val sweep = Entity()
    private val blips = ArrayList<Pair<Entity, Double>>()
    private var clock = 0.0
    /** The two side desks (declared before init: property initializers run in order). */
    private val wingRoots = HashMap<Boolean, Entity>()

    init {
        rings()
        ribs()
        cables()
        pipes()
        lights()
        rails()
        wings()
        props()
        plates()
    }

    // ---- The hull's frame ----

    private fun onHull(yaw: Double, elev: Double, r: Double): Vec3 {
        val a = Math.toRadians(yaw); val e = Math.toRadians(elev)
        return Vec3(r * cos(e) * sin(a), r * sin(e), -r * cos(e) * cos(a))
    }

    /** A rod from [a] to [b] ([mesh] is a unit cylinder or box along y) of thickness [t]. */
    private fun rod(a: Vec3, b: Vec3, t: Float, mat: com.pico.spatial.core.ecs.resource.Material, box: Boolean = false): Entity {
        val d = b - a
        val len = d.length().toFloat()
        return model(if (box) BOX else CYL, mat).apply {
            setPos(((a + b) * 0.5).toVector3())
            transform().setQuaternion(quatFromY(d))
            transform().setScaleVector(Vector3(t, len, t))
        }.also { root.addChild(it) }
    }

    private fun rings() {
        for (e in CabinDecor.RING_ELEVATIONS) {
            val r = (1.46 * cos(Math.toRadians(e))).toFloat()
            root.addChild(model(MeshResource.createTorus(r + 0.018f, r - 0.018f), steel).apply { setPos(Vector3(0f, (1.46 * sin(Math.toRadians(e))).toFloat(), 0f)) })
        }
    }

    private fun ribs() {
        for (yaw in CabinDecor.RIB_YAWS) {
            var prev = onHull(yaw, 52.0, 1.47)
            var e = 46.0
            while (e >= -54.0) {
                val q = onHull(yaw, e, 1.47)
                rod(prev, q, 0.045f, darkSteel, box = true)
                prev = q
                e -= 6.0
            }
        }
    }

    private fun cables() {
        val mats = listOf(rubber, cableGrey, cableBlue, rubber)
        for (side in listOf(-1.0, 1.0)) {
            for ((k, m) in mats.withIndex()) {
                val elev = CabinDecor.CABLE_ELEVATION - 2.2 * k
                val r = 1.455 - 0.012 * k
                var yaw = CabinDecor.CABLE_YAW_FROM
                var prev = onHull(side * yaw, elev, r)
                while (yaw < CabinDecor.CABLE_YAW_TO) {
                    yaw += 6.0
                    val q = onHull(side * yaw, elev + 0.6 * sin(yaw * 0.3), r)
                    rod(prev, q, if (k == 0) 0.026f else 0.016f, m)
                    prev = q
                }
            }
            // Clamps holding the bundle to the hull.
            var yaw = CabinDecor.CABLE_YAW_FROM + 6
            while (yaw < CabinDecor.CABLE_YAW_TO) {
                rod(onHull(side * yaw, CabinDecor.CABLE_ELEVATION + 1.6, 1.45), onHull(side * yaw, CabinDecor.CABLE_ELEVATION - 8.6, 1.45), 0.03f, steel, box = true)
                yaw += 18.0
            }
        }
    }

    private fun pipes() {
        for (side in listOf(-1.0, 1.0)) for ((k, m) in listOf(steel, copper).withIndex()) {
            val elev = 58.0 + 3.5 * k
            var yaw = 62.0
            var prev = onHull(side * yaw, elev, 1.43)
            while (yaw < 176.0) {
                yaw += 8.0
                val q = onHull(side * yaw, elev, 1.43)
                rod(prev, q, 0.028f, m)
                prev = q
            }
        }
    }

    private fun lights() {
        for (l in CabinDecor.LIGHTS) {
            val at = l.at
            val e = Entity().apply {
                setPos(at.toVector3())
                // Facing the eyes from above and ahead.
                transform().eulerAngles = EulerAngles(-58f, (-l.yaw).toFloat(), 0f)
            }
            e.addChild(model(MeshResource.createBox(Vector3(l.size.toFloat(), 0.03f, 0.09f), 0.01f), darkSteel))
            e.addChild(model(MeshResource.createBox(Vector3(l.size.toFloat() - 0.03f, 0.006f, 0.065f), 0.004f), lampWhite).apply { setPos(Vector3(0f, -0.016f, 0f)) })
            root.addChild(e)
        }
    }

    private fun rails() {
        for (side in listOf(-1f, 1f)) {
            val a = Vec3(side * 0.34, 1.0, -0.42); val b = Vec3(side * 0.34, 1.0, 0.3)
            rod(a, b, 0.024f, steel)
            for (z in listOf(-0.4, 0.28)) rod(Vec3(side * 0.34, 1.0, z), Vec3(side * 0.42, 1.18, z), 0.018f, darkSteel)
        }
    }

    // ---- The side desks ----

    private fun wings() {
        for (side in listOf(-1.0, 1.0)) {
            val yaw = side * CabinDecor.WING_YAW
            val a = Math.toRadians(yaw)
            val d = CabinDecor.WING_DIST
            val w = Entity().apply {
                setPos(Vector3((d * sin(a)).toFloat(), CabinDecor.WING_Y.toFloat(), (-d * cos(a)).toFloat()))
                transform().eulerAngles = EulerAngles(CabinDecor.WING_TILT.toFloat(), (-yaw).toFloat(), 0f)
            }
            root.addChild(w)
            wingRoots[side > 0] = w
            w.addChild(model(MeshResource.createBox(Vector3(CabinDecor.WING_W.toFloat(), 0.04f, CabinDecor.WING_D.toFloat()), 0.012f), darkSteel))
            w.addChild(model(MeshResource.createBox(Vector3(CabinDecor.WING_W.toFloat() + 0.02f, 0.022f, 0.022f), 0.006f), steel).apply { setPos(Vector3(0f, 0.022f, CabinDecor.WING_D.toFloat() / 2)) })
            // Its front plate down toward the floor.
            root.addChild(model(MeshResource.createBox(Vector3(CabinDecor.WING_W.toFloat(), 0.55f, 0.03f), 0.01f), darkSteel).apply {
                setPos(Vector3((0.9 * sin(a)).toFloat(), -0.86f, (-0.9 * cos(a)).toFloat()))
                transform().eulerAngles = EulerAngles(0f, (-yaw).toFloat(), 0f)
            })
            // Switches along the near edge: small black toggles, the two outer ones under red guard covers (ballast and
            // drop weights on the left; on the right, thrusters).
            for (k in 0 until 6) {
                val x = (-0.17 + 0.068 * k).toFloat()
                w.addChild(model(BOX, rubber).apply { setPos(Vector3(x, 0.04f, 0.115f)); transform().setScaleVector(Vector3(0.012f, 0.035f, 0.012f)); transform().eulerAngles = EulerAngles(if (k % 2 == 0) 18f else -18f, 0f, 0f) })
                w.addChild(model(BOX, steel).apply { setPos(Vector3(x, 0.024f, 0.115f)); transform().setScaleVector(Vector3(0.03f, 0.008f, 0.03f)) })
                if (k == 0 || k == 5) w.addChild(model(BOX, guardRed).apply { setPos(Vector3(x, 0.045f, 0.115f)); transform().setScaleVector(Vector3(0.036f, 0.04f, 0.036f)) })
                val dot = model(MeshResource.createSphere(0.005f), dots[(k + if (side > 0) 1 else 0) % dots.size]).apply { setPos(Vector3(x, 0.024f, 0.138f)) }
                w.addChild(dot); blink += dot to k + (if (side > 0) 7 else 0)
            }
            // Knobs at the far edge (the right desk; the left one has the sonar there).
            if (side > 0) for (k in 0 until 3) w.addChild(model(CYL, rubber).apply { setPos(Vector3((-0.02 + 0.06 * k).toFloat(), 0.035f, 0.07f)); transform().setScaleVector(Vector3(0.026f, 0.03f, 0.026f)) })
        }
    }

    /** The sonar on the left desk: a round green screen, range rings, a sweep turning, echoes lit as it passes. */
    private fun sonar(w: Entity, o: CabinDecor.OnWing) {
        val holder = Entity().apply { setPos(Vector3(o.x.toFloat(), o.y.toFloat(), o.z.toFloat())) }
        w.addChild(holder)
        val r = (o.size / 2).toFloat()
        holder.addChild(model(MeshResource.createCylinder(0.012f, r + 0.012f), darkSteel))
        holder.addChild(model(MeshResource.createCylinder(0.002f, r), sonarFace).apply { setPos(Vector3(0f, 0.007f, 0f)) })
        for (k in 1..3) holder.addChild(model(MeshResource.createTorus(r * k / 3.2f + 0.0015f, r * k / 3.2f - 0.0015f), sonarDim).apply { setPos(Vector3(0f, 0.009f, 0f)) })
        sweep.setPos(Vector3(0f, 0.01f, 0f))
        sweep.addChild(model(BOX, sonarGreen).apply { setPos(Vector3(0f, 0f, -r / 2)); transform().setScaleVector(Vector3(0.003f, 0.002f, r)) })
        holder.addChild(sweep)
        val rnd = java.util.Random(23)
        repeat(9) {
            val ang = rnd.nextDouble() * 2 * Math.PI
            val rr = r * (0.25 + 0.7 * rnd.nextDouble())
            val b = model(MeshResource.createSphere(1f), sonarGreen).apply { setPos(Vector3((rr * sin(ang)).toFloat(), 0.011f, (-rr * cos(ang)).toFloat())); setScale(0.0001f) }
            holder.addChild(b)
            blips += b to ang
        }
    }

    // ---- The props (Seed3D) and the plates ----

    private fun props() {
        for (o in CabinDecor.ON_WINGS) {
            val w = wingRoots[o.right] ?: continue
            if (o.name == "sonar") { sonar(w, o); continue }
            val m = models[o.name]?.clone(Entity.CloneOptions(true, true)) ?: continue
            m.setScale(o.size.toFloat())
            m.setPos(Vector3(o.x.toFloat(), o.y.toFloat(), o.z.toFloat()))
            w.addChild(m)
        }
        for (p in CabinDecor.PROPS) {
            val m = models[p.name]?.clone(Entity.CloneOptions(true, true)) ?: continue
            m.setScale(p.size.toFloat())
            m.setPos(p.at.toVector3())
            // Facing the eyes (the models face +z).
            m.transform().eulerAngles = EulerAngles(0f, (-p.yaw).toFloat() + (if (p.name == "seat") 180f else 0f), 0f)
            root.addChild(m)
        }
    }

    private fun plates() {
        for (p in CabinDecor.PLATES) {
            val b = plateBitmap(p.name)
            val w = p.size.toFloat()
            val h = w * b.height / b.width
            val mat = UnlitMaterial.create().apply { setBaseColorTexture(TextureResource.create(b)) }
            root.addChild(model(MeshResource.createPlane(w, h, 0f), mat).apply {
                setPos(p.at.toVector3())
                transform().eulerAngles = EulerAngles(0f, (-p.yaw).toFloat(), 0f)
            })
        }
    }

    private fun plateBitmap(text: String): Bitmap {
        val px = 64
        val ink = Paint().apply { isAntiAlias = true; color = 0xFFE8E2D0.toInt(); textSize = px * 0.62f; typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER }
        val w = (ink.measureText(text) + px * 0.9f).toInt()
        val b = Bitmap.createBitmap(w, px, Bitmap.Config.ARGB_8888)
        Canvas(b).apply {
            drawColor(0xFF20262C.toInt())
            drawRect(RectF(3f, 3f, w - 3f, px - 3f), Paint().apply { style = Paint.Style.STROKE; strokeWidth = 3f; color = 0xFF7D8690.toInt() })
            for ((x, y) in listOf(9f to 9f, w - 9f to 9f, 9f to px - 9f, w - 9f to px - 9f)) drawCircle(x, y, 3f, Paint().apply { color = 0xFF9AA3AD.toInt(); isAntiAlias = true })
            drawText(text, w / 2f, px * 0.7f, ink)
        }
        return b
    }

    /** Each frame: the sweep turns, an echo lights as it passes and fades; the switches' dots blink, each at its pace. */
    fun frame(dt: Double) {
        clock += dt
        val ang = (clock * 1.6) % (2 * Math.PI)
        sweep.transform().eulerAngles = EulerAngles(0f, Math.toDegrees(-ang).toFloat(), 0f)
        for ((b, a) in blips) {
            var since = ang - a
            while (since < 0) since += 2 * Math.PI
            val k = (1 - since / 2.2).coerceIn(0.0, 1.0)
            b.setScale((0.0015 + 0.0045 * k).toFloat())
        }
        for ((e, k) in blink) {
            val on = sin(clock * (0.7 + 0.29 * (k % 5)) + k * 2.3) > -0.4
            if (e.enabled != on) e.enabled = on
        }
    }

    companion object {
        private val CYL = MeshResource.createCylinder(1f, 0.5f).shared()
        private val BOX = MeshResource.createBox(Vector3(1f, 1f, 1f), 0f).shared()

        /** The hull's quilted insulation: padded panels between metal battens (u round the sphere, v top to bottom). */
        fun liningTexture(): TextureResource {
            val w = 2048; val h = 1024
            val b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            val c = Canvas(b)
            c.drawColor(0xFF15191F.toInt())
            val cols = 48; val rows = 18
            val cw = w / cols.toFloat(); val rh = h / rows.toFloat()
            val pad = Paint().apply { isAntiAlias = true }
            val stitch = Paint().apply { isAntiAlias = true; color = 0x33000000; strokeWidth = 2f }
            for (j in 0 until rows) for (i in 0 until cols) {
                val l = i * cw + 3; val t = j * rh + 3; val r = (i + 1) * cw - 3; val btm = (j + 1) * rh - 3
                val tone = 0xFF404852.toInt() + ((i * 7 + j * 3) % 3) * 0x00030303
                pad.shader = LinearGradient(l, t, l, btm, tone, 0xFF2C333B.toInt(), Shader.TileMode.CLAMP)
                c.drawRoundRect(RectF(l, t, r, btm), 9f, 9f, pad)
                c.drawLine((l + r) / 2, t + 6, (l + r) / 2, btm - 6, stitch)
                c.drawLine(l + 6, (t + btm) / 2, r - 6, (t + btm) / 2, stitch)
            }
            // Metal battens every eighth column and round the third and fourteenth rows.
            val bar = Paint().apply { color = 0xFF5E6670.toInt() }
            for (i in 0..cols step 8) c.drawRect(i * cw - 5, 0f, i * cw + 5, h.toFloat(), bar)
            for (j in listOf(3, 14)) c.drawRect(0f, j * rh - 4, w.toFloat(), j * rh + 4, bar)
            val rivet = Paint().apply { isAntiAlias = true; color = 0xFF8A939C.toInt() }
            for (i in 0..cols step 8) for (j in 0 until rows) c.drawCircle(i * cw, j * rh + rh / 2, 3.5f, rivet)
            return TextureResource.create(b).shared()
        }

        /** The models this adds to the stage's list. */
        val MODELS = CabinDecor.PROPS.map { it.name } + CabinDecor.ON_WINGS.map { it.name }.filter { it != "sonar" }
    }
}
