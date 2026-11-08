package com.vrgames.deepsea.render

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.graphics.Typeface
import com.pico.spatial.core.ecs.BoundingBox
import com.pico.spatial.core.ecs.CollisionComponent
import com.pico.spatial.core.ecs.Entity
import com.pico.spatial.core.ecs.EnvironmentLightingSettingsComponent
import com.pico.spatial.core.ecs.HoverEffectComponent
import com.pico.spatial.core.ecs.ImageBasedLightComponent
import com.pico.spatial.core.ecs.ImageBasedLightReceiverComponent
import com.pico.spatial.core.ecs.ImageBasedLightSource
import com.pico.spatial.core.ecs.InteractableComponent
import com.pico.spatial.core.ecs.ModelComponent
import com.pico.spatial.core.ecs.resource.BlendingMode
import com.pico.spatial.core.ecs.resource.Material
import com.pico.spatial.core.ecs.resource.MeshModel
import com.pico.spatial.core.ecs.resource.MeshResource
import com.pico.spatial.core.ecs.resource.PhysicallyBasedMaterial
import com.pico.spatial.core.ecs.resource.PhysicsMaterialResource
import com.pico.spatial.core.ecs.resource.ShapeResource
import com.pico.spatial.core.ecs.resource.TextureResource
import com.pico.spatial.core.ecs.resource.UnlitMaterial
import com.pico.spatial.core.math.EulerAngles
import com.pico.spatial.core.math.Vector2
import com.pico.spatial.core.math.Vector3
import com.pico.spatial.core.math.Vector4
import com.vrgames.deepsea.core.Dive
import com.vrgames.deepsea.core.Kind
import com.vrgames.deepsea.core.P
import com.vrgames.deepsea.core.Sea
import com.vrgames.deepsea.core.Thing
import com.vrgames.deepsea.core.Vec3
import com.vrgames.deepsea.core.Zone
import kotlin.math.PI
import kotlin.math.sin

/** What a pinch on the tank means. */
sealed interface Target {
    data class Water(val at: P) : Target
    data class OnThing(val key: String) : Target
}

/**
 * The glass tank standing in the ship's lab, the sea inside it in four zones. Everything in the sea
 * is in [sea]'s frame: x across, y up from the floor, z out of the tank; the sub and the animals keep
 * to z = 0, the same numbers as [Dive]. Dark zones are dark by swapping a model's materials for a
 * silhouette until the lamp reaches it.
 */
class SeaView(private val models: Map<String, Entity>, private val tex: Map<String, TextureResource>) {
    val root = Entity()
    val sea = Entity()
    private var life = Entity().also { sea.addChild(it) }
    private val iblSource = Entity().also { root.addChild(it) }

    private val w = Sea.W.toFloat()
    private val h = Sea.H.toFloat()
    private val d = DEPTH

    private val steel = pbr(color(0.2f, 0.22f, 0.25f), metallic = 0.85f, roughness = 0.35f, emissive = color(0.02f, 0.025f, 0.03f)).shared()
    private val plinth = pbr(color(0.1f, 0.11f, 0.13f), metallic = 0.5f, roughness = 0.5f).shared()
    private val glass = pbr(color(0.7f, 0.85f, 0.95f, 0.07f), roughness = 0.05f, emissive = color(0f, 0f, 0f), blending = BlendingMode.TRANSPARENT).shared()
    private val silhouette = unlit(color(0.008f, 0.012f, 0.022f)).shared()
    private val sphere = MeshResource.createSphere(1f).shared()
    private val hoverMat = glow(color(0.6f, 0.85f, 1f, 0.015f)).shared()
    private val cellMesh = plane((CELL * 0.97).toFloat(), (CELL * 0.97).toFloat(), "cell").shared()
    private val lampCone = glow(color(0.75f, 0.88f, 1f, 0.16f)).shared()
    private val lampHalo = softDisc(0.34f)
    private val markMat = glow(color(1f, 0.85f, 0.35f, 0.9f)).shared()
    private val wantMat = glow(color(1f, 0.78f, 0.3f, 0.55f)).shared()
    private val surfaceMat = glow(color(1f, 0.9f, 0.45f, 0.6f)).shared()
    private val flashMat = glow(color(1f, 1f, 1f, 0.8f)).shared()
    private val limitMat = glow(color(1f, 0.25f, 0.2f, 0.75f)).shared()
    private val halos = mapOf(
        "lantern" to glow(color(0.35f, 0.8f, 1f, 0.45f)).shared(),
        "jelly" to glow(color(0.5f, 0.55f, 1f, 0.32f)).shared(),
        "angler" to glow(color(0.5f, 1f, 0.8f, 0.55f)).shared(),
    )
    private val bubbles = Sparks("sea_bubbles", 80, sphere, glow(color(0.7f, 0.85f, 1f, 0.4f)).shared()).also { sea.addChild(it.entity) }
    private val plume = Sparks("sea_plume", 70, sphere, glow(color(0.9f, 0.45f, 0.2f, 0.28f)).shared()).also { sea.addChild(it.entity) }
    private val glitter = Sparks("sea_glitter", 120, sphere, glow(color(0.5f, 0.9f, 1f, 0.9f)).shared()).also { sea.addChild(it.entity) }

    private val waterOf = HashMap<Entity, P>()
    private val thingOf = HashMap<Entity, String>()
    private val views = HashMap<String, ThingView>()
    private var limit: Entity? = null
    private var subView: Entity? = null
    private var cone: Entity? = null
    private var halo: Entity? = null
    private val flash = model(sphere, flashMat).apply { setScale(0.1f); enabled = false }.also { sea.addChild(it) }
    private var flashLeft = 0.0
    private var played: Dive? = null
    private var clock = 0.0
    private var subYaw = 0f
    private var goal: Entity? = null
    private var surfaceBand: Entity? = null
    private var surfaceTag: Entity? = null
    private val ringMesh = MeshResource.createTorus(0.03f, 0.0028f).shared()
    private val wantRings = HashMap<String, Entity>()
    private val trophyViews = ArrayList<Entity>()
    private val trophyMats = listOf(
        pbr(color(0.72f, 0.45f, 0.22f), metallic = 1f, roughness = 0.3f), pbr(color(0.8f, 0.82f, 0.86f), metallic = 1f, roughness = 0.22f), pbr(color(1f, 0.78f, 0.3f), metallic = 1f, roughness = 0.2f),
    ).map { it.shared() }
    private val trophyOff = pbr(color(0.08f, 0.08f, 0.1f), roughness = 0.8f).shared()

    /**
     * A model and its silhouette, of which one shows. Never swap materials back and forth instead: the
     * SDK closes a material taken off a model, and putting it back crashes (it took a few levels to show).
     */
    private class ThingView(val e: Entity, val body: Entity, val shadow: Entity, val halo: Entity?) {
        var dark = false
        var yaw = 0f
        /** Seconds into its performance on a first meeting, or negative. */
        var show = -1.0
    }

    /** A first meeting: the creature rises, turns right round and shows itself, shedding light. */
    fun perform(key: String) { views[key]?.show = 0.0 }

    init {
        root.addChild(sea)
        sea.setPos(Vector3(0f, BASE, 0f))
        tank()
        cells()
    }

    /** A round glow fading to nothing at its edge, added to what is behind it. */
    private fun softDisc(strength: Float): Material {
        val n = 256
        val b = Bitmap.createBitmap(n, n, Bitmap.Config.ARGB_8888)
        val m = n / 2f
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = android.graphics.RadialGradient(m, m, m, intArrayOf(0xFF8FB4D8.toInt(), 0xFF2C4560.toInt(), 0xFF000000.toInt()), floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP)
        }
        Canvas(b).drawRect(0f, 0f, n.toFloat(), n.toFloat(), p)
        return UnlitMaterial.create(BlendingMode.ADD).apply {
            setBaseColorTexture(TextureResource.create(b))
            setBaseColor(color(strength, strength, strength, strength))
        }.shared()
    }

    private fun plane(pw: Float, ph: Float, name: String): MeshResource {
        val x = pw / 2
        val y = ph / 2
        return MeshResource.createWithMeshModel(
            MeshModel(
                listOf(Vector3(-x, -y, 0f), Vector3(x, -y, 0f), Vector3(x, y, 0f), Vector3(-x, y, 0f)), listOf(0, 1, 2, 0, 2, 3),
                List(4) { Vector3(0f, 0f, 1f) }, List(4) { Vector4(1f, 0f, 0f, 1f) }, listOf(Vector2(0f, 0f), Vector2(1f, 0f), Vector2(1f, 1f), Vector2(0f, 1f)),
            ),
            BoundingBox(Vector3(-x, -y, -0.001f), Vector3(x, y, 0.001f)), name,
        )
    }

    private fun clone(name: String): Entity? = models[name]?.clone(Entity.CloneOptions(true, true))

    private fun setMaterials(e: Entity, m: Material) {
        e.components[ModelComponent::class.java]?.materials?.let { mats -> for (i in 0 until mats.size) mats[i] = m }
        e.getChildren().forEach { setMaterials(it, m) }
    }

    /** Sea from top to bottom: the four zones run into one another, sunbeams in the first. */
    private fun waterBitmap(): Bitmap {
        val bw = 512
        val bh = 2048
        val b = Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888)
        val c = Canvas(b)
        val p = Paint()
        p.shader = LinearGradient(
            0f, 0f, 0f, bh.toFloat(),
            intArrayOf(0xFF4FB6D8.toInt(), 0xFF1F7FB0.toInt(), 0xFF0E4C80.toInt(), 0xFF072650.toInt(), 0xFF031028.toInt(), 0xFF020810.toInt(), 0xFF010408.toInt()),
            floatArrayOf(0f, 0.12f, 0.25f, 0.42f, 0.55f, 0.78f, 1f), Shader.TileMode.CLAMP,
        )
        c.drawRect(0f, 0f, bw.toFloat(), bh.toFloat(), p)
        val ray = Paint().apply { isAntiAlias = true }
        val r = java.util.Random(11)
        repeat(9) {
            val x = r.nextFloat() * bw
            val wide = 18f + r.nextFloat() * 50f
            ray.shader = LinearGradient(0f, 0f, 0f, bh * 0.36f, 0x38FFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP)
            val path = android.graphics.Path().apply {
                moveTo(x, 0f); lineTo(x + wide, 0f); lineTo(x + wide * 2.2f - 60f, bh * 0.36f); lineTo(x - wide * 0.8f - 60f, bh * 0.36f); close()
            }
            c.drawPath(path, ray)
        }
        return b
    }

    /** The glass box on its plinth, the painted sea on its back and sides, the sea floor, the zone names. */
    private fun tank() {
        val water = UnlitMaterial.create().apply { setBaseColorTexture(TextureResource.create(waterBitmap())) }.shared()
        sea.addChild(model(plane(w, h, "back"), water).apply { setPos(Vector3(0f, h / 2, -d / 2)) })
        for (s in listOf(-1f, 1f)) sea.addChild(model(plane(d, h, "side"), water).apply {
            setPos(Vector3(s * w / 2, h / 2, 0f))
            transform().eulerAngles = EulerAngles(0f, -s * 90f, 0f)
        })
        val sand = tex["sand"]?.let { t ->
            PhysicallyBasedMaterial.create().apply {
                setBaseColorTexture(t)
                setEmissiveTexture(t)
                setEmissiveColor(color(0.08f, 0.1f, 0.13f))
                setRoughness(1f)
            }.shared()
        } ?: pbr(color(0.15f, 0.15f, 0.17f), roughness = 1f).shared()
        sea.addChild(model(plane(w, d, "floor"), sand).apply {
            setPos(Vector3(0f, 0.002f, 0f))
            transform().eulerAngles = EulerAngles(-90f, 0f, 0f)
        })
        val top = glow(color(0.5f, 0.85f, 1f, 0.22f)).shared()
        sea.addChild(model(plane(w, d, "surface"), top).apply {
            setPos(Vector3(0f, h, 0f))
            transform().eulerAngles = EulerAngles(90f, 0f, 0f)
        })
        // Glass in front, frame around.
        sea.addChild(model(plane(w, h, "glass"), glass).apply { setPos(Vector3(0f, h / 2, d / 2)) })
        val bar = 0.018f
        for (sx in listOf(-1f, 1f)) for (sz in listOf(-1f, 1f)) {
            sea.addChild(model(MeshResource.createBox(Vector3(bar, h + bar, bar), 0.003f), steel).apply { setPos(Vector3(sx * w / 2, h / 2, sz * d / 2)) })
        }
        for (y in listOf(0f, h)) for (sz in listOf(-1f, 1f)) sea.addChild(model(MeshResource.createBox(Vector3(w + bar, bar, bar), 0.003f), steel).apply { setPos(Vector3(0f, y, sz * d / 2)) })
        for (y in listOf(0f, h)) for (sx in listOf(-1f, 1f)) sea.addChild(model(MeshResource.createBox(Vector3(bar, bar, d + bar), 0.003f), steel).apply { setPos(Vector3(sx * w / 2, y, 0f)) })
        root.addChild(model(MeshResource.createBox(Vector3(w + 0.1f, BASE, d + 0.1f), 0.01f), plinth).apply { setPos(Vector3(0f, BASE / 2, 0f)) })
        // Zone names on the left glass edge.
        for (z in Zone.entries) {
            val y = (Sea.ZONE_H * (3 - z.ordinal) + Sea.ZONE_H - 0.03).toFloat()
            val t = tag(z.title + "  " + z.meters)
            t.setPos(Vector3(-w / 2 + 0.085f, y, d / 2 + 0.004f))
            sea.addChild(t)
        }
        // The shelf with the trophies, on the right.
        val sx = w / 2 + 0.24f
        root.addChild(model(MeshResource.createBox(Vector3(0.34f, 0.02f, 0.14f), 0.004f), plinth).apply { setPos(Vector3(sx, 0.84f, 0f)) })
        root.addChild(model(MeshResource.createBox(Vector3(0.03f, 0.84f, 0.03f), 0.004f), plinth).apply { setPos(Vector3(sx, 0.42f, 0f)) })
        for (k in 0 until 3) {
            val t = clone("trophy") ?: model(MeshResource.createCone(0.1f, 0.03f), steel)
            t.setScale(0.1f)
            t.setPos(Vector3(sx - 0.11f + k * 0.11f, 0.85f, 0f))
            t.transform().eulerAngles = EulerAngles(0f, Looks.TROPHY_TURN, 0f)
            root.addChild(t)
            trophyViews += t
        }
        trophies(0)
    }

    fun trophies(earned: Int) = trophyViews.forEachIndexed { k, t -> setMaterials(t, if (k < earned) trophyMats[k] else trophyOff) }

    private fun tag(text: String): Entity {
        val px = 40
        val ink = Paint().apply { isAntiAlias = true; color = 0xFFE8F6FF.toInt(); textSize = px * 0.66f; typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER }
        val b = Bitmap.createBitmap((ink.measureText(text) + px * 0.8f).toInt(), px, Bitmap.Config.ARGB_8888)
        Canvas(b).apply {
            drawRoundRect(0f, 0f, b.width.toFloat(), px.toFloat(), px / 2f, px / 2f, Paint().apply { isAntiAlias = true; color = 0x99041828.toInt() })
            drawText(text, b.width / 2f, px * 0.72f, ink)
        }
        val m = UnlitMaterial.create(BlendingMode.TRANSPARENT).apply { setBaseColorTexture(TextureResource.create(b)) }
        val th = 0.02f
        val tw = th * b.width / px
        return model(plane(tw, th, "tag$text"), m)
    }

    /** A pinch box and a hover glow for every piece of water. */
    private fun cells() {
        val nx = (Sea.W / CELL).toInt()
        val ny = (Sea.H / CELL).toInt()
        for (j in 0 until ny) for (i in 0 until nx) {
            val x = -Sea.W / 2 + (i + 0.5) * Sea.W / nx
            val y = (j + 0.5) * Sea.H / ny
            val e = model(cellMesh, hoverMat)
            e.setPos(Vector3(x.toFloat(), y.toFloat(), -0.02f))
            e.components.set(CollisionComponent(listOf(ShapeResource.createBox(Vector3((Sea.W / nx).toFloat(), (Sea.H / ny).toFloat(), 0.01f))), PhysicsMaterialResource()))
            e.components.set(InteractableComponent())
            e.components.set(HoverEffectComponent())
            sea.addChild(e)
            waterOf[e] = P(x, y)
        }
    }

    fun target(e: Entity?): Target? {
        var cur = e
        while (cur != null) {
            thingOf[cur]?.let { return Target.OnThing(it) }
            waterOf[cur]?.let { return Target.Water(it) }
            cur = cur.getParent()
        }
        return null
    }

    fun build(p: Dive) {
        played = p
        life.destroy()
        life = Entity().also { sea.addChild(it) }
        views.clear()
        thingOf.clear()
        wantRings.clear()
        bubbles.clear()
        plume.clear()
        glitter.clear()
        for (t in p.things) thing(t)
        clone("sub")?.let { s ->
            s.setScale(Looks.height("sub"))
            life.addChild(s)
            subView = s
        } ?: run { subView = null }
        cone = model(MeshResource.createCone(1f, 1f), lampCone).also { life.addChild(it) }
        // A flat glow in the plane of the sub. A ball this big would stand out through the glass and cut the panels.
        halo = model(plane(Dive.LAMP.toFloat() * 2, Dive.LAMP.toFloat() * 2, "lamp_disc"), lampHalo).also { life.addChild(it) }
        // Where the sub is headed, and what it has been told to do.
        goal = model(ringMesh, markMat).apply { enabled = false }.also { life.addChild(it) }
        surfaceBand = model(MeshResource.createBox(Vector3(w - 0.03f, 0.012f, 0.012f), 0f), surfaceMat).apply {
            setPos(Vector3(0f, h - 0.025f, 0f))
            enabled = false
        }.also { life.addChild(it) }
        surfaceTag = tag("↑ 回到海面").apply {
            setPos(Vector3(0f, h - 0.06f, 0.05f))
            enabled = false
        }.also { life.addChild(it) }
        limit = if (p.stage.floor > 0.1) model(MeshResource.createBox(Vector3(w, 0.004f, 0.004f), 0.001f), limitMat).apply {
            setPos(Vector3(0f, (p.stage.floor - 0.03).toFloat(), 0f))
        }.also { life.addChild(it) } else null
    }

    private fun thing(t: Thing) {
        val s = t.sp
        // A holder banks the animal toward the player; the body inside it turns left and right.
        val body = clone(s.id) ?: model(sphere, steel)
        body.setScale(Looks.height(s.id))
        val e = Entity()
        e.addChild(body)
        val shadow = clone(s.id) ?: model(sphere, silhouette)
        shadow.setScale(Looks.height(s.id))
        setMaterials(shadow, silhouette)
        shadow.enabled = false
        e.addChild(shadow)
        e.transform().eulerAngles = EulerAngles(Looks.bank(s.id), 0f, 0f)
        life.addChild(e)
        val halo = halos[s.id]?.let { m -> model(sphere, m).apply { setScale((Looks.box(s.id).first * 0.38).toFloat()) }.also { life.addChild(it) } }
        views[t.key] = ThingView(e, body, shadow, halo)
        // The pinch box on the holder, so hovering lights up the model inside it.
        val (bw, bh) = Looks.box(s.id)
        val center = Vector3(0f, if (s.kind == Kind.THING) bh.toFloat() / 2 else Looks.height(s.id) * Looks.mid(s.id), 0f)
        e.components.set(CollisionComponent(listOf(ShapeResource.createBox(Vector3(bw.toFloat() * 1.1f, bh.toFloat() * 1.1f, 0.08f)).offsetByTranslation(center)), PhysicsMaterialResource()))
        e.components.set(InteractableComponent())
        e.components.set(HoverEffectComponent())
        thingOf[e] = t.key
        val ring = model(ringMesh, wantMat).apply { enabled = false }
        life.addChild(ring)
        wantRings[t.key] = ring
    }


    fun sync(p: Dive, dt: Double) {
        if (p !== played) return
        clock += dt
        for (t in p.things) syncThing(t, dt)
        val s = p.sub
        val y = (s.at.y + 0.012 * sin(clock * 1.5)).toFloat()
        val want = if (s.heading > 0) Looks.yaw("sub") else Looks.yaw("sub") + 180f
        subYaw += (want - subYaw) * (dt * 4).toFloat().coerceAtMost(1f)
        subView?.let {
            it.setPos(Vector3(s.at.x.toFloat(), y - Looks.height("sub") * Looks.mid("sub"), 0f))
            it.transform().eulerAngles = EulerAngles(0f, subYaw, 0f)
        }
        cone?.let { c ->
            if (c.enabled != s.lamp) c.enabled = s.lamp
            val len = (Dive.LAMP * 0.95).toFloat()
            c.setPos(Vector3((s.at.x + s.heading * (len / 2 + 0.03)).toFloat(), y, 0f))
            c.transform().eulerAngles = EulerAngles(0f, 0f, if (s.heading > 0) 90f else -90f)
            c.transform().setScaleVector(Vector3(len * 0.45f, len, len * 0.45f))
        }
        halo?.let { h ->
            if (h.enabled != s.lamp) h.enabled = s.lamp
            h.setPos(Vector3(s.at.x.toFloat(), y, -0.01f))
        }
        marks(p)
        if ((clock * 3).toInt() != ((clock - dt) * 3).toInt() && s.speed > 0.01) {
            bubbles.emit(Vec3(s.at.x - s.heading * 0.04, s.at.y, 0.0), 1, 0.01, 2.2, 0.0035f, Vec3(0.0, 0.05, 0.0))
        }
        // The vent smokes, the ones that glow shed sparks.
        if (p.stage.floor < 0.1 && (clock * 5).toInt() != ((clock - dt) * 5).toInt()) {
            p.thing("vent0")?.let { v -> plume.emit(Vec3(v.at.x, v.at.y + Looks.box("vent").second, 0.0), 2, 0.012, 3.0, 0.009f, Vec3(0.0, 0.06, 0.0)) }
        }
        if (flashLeft > 0) {
            flashLeft -= dt
            if (flashLeft <= 0) flash.enabled = false
        }
        bubbles.update(dt, 0.1)
        plume.update(dt, 0.1)
        glitter.update(dt)
    }

    /** Rings on what the goals want, a ring where the sub is headed, the band at the surface when it's time to go home. */
    private fun marks(p: Dive) {
        val pulse = 1f + 0.1f * sin(clock * 3).toFloat()
        for (t in p.things) wantRings[t.key]?.let { r ->
            val on = p.wanted(t)
            if (r.enabled != on) r.enabled = on
            if (on) {
                val (bw, bh) = Looks.box(t.sp.id)
                val mid = t.at.y + if (t.sp.kind == Kind.THING) bh / 2 else 0.0
                r.setPos(Vector3(t.at.x.toFloat(), mid.toFloat(), 0.03f))
                r.transform().eulerAngles = EulerAngles(90f, 0f, 0f)
                r.setScale((maxOf(bw, bh) / 0.06 * 0.62 * pulse).toFloat())
            }
        }
        goal?.let { g ->
            val job = p.job?.first
            val tgt = job?.at ?: p.sub.target
            val on = (job != null) || (tgt - p.sub.at).len() > 0.04
            if (g.enabled != on) g.enabled = on
            if (on) {
                g.setPos(Vector3(tgt.x.toFloat(), tgt.y.toFloat(), 0.04f))
                g.transform().eulerAngles = EulerAngles(90f, 0f, 0f)
                g.setScale(0.8f * pulse)
            }
        }
        val home = p.wantsSurface
        surfaceBand?.let { if (it.enabled != home) it.enabled = home }
        surfaceTag?.let { if (it.enabled != home) it.enabled = home }
    }

    private fun syncThing(t: Thing, dt: Double) {
        val v = views[t.key] ?: return
        val s = t.sp
        val swims = s.swim > 0
        val base = if (swims) Looks.height(s.id) * Looks.mid(s.id) else 0f
        v.e.setPos(Vector3(t.at.x.toFloat(), t.at.y.toFloat() - base, 0f))
        if (v.show >= 0) {
            v.show += dt
            val k = (v.show / PERFORM_S).coerceIn(0.0, 1.0)
            val up = sin(k * PI)
            // Turn once about itself while lifting and growing a little, then settle back.
            val h = Looks.height(s.id) * (1f + 0.35f * up.toFloat())
            v.e.setPos(Vector3(t.at.x.toFloat(), (t.at.y - base + up * 0.05).toFloat(), (up * 0.1).toFloat()))
            v.body.transform().eulerAngles = EulerAngles(0f, v.yaw + (360 * ease(k)).toFloat(), 0f)
            v.body.transform().setScaleVector(Vector3(h))
            if ((v.show * 5).toInt() != ((v.show - dt) * 5).toInt()) glitter.emit(Vec3(t.at.x, t.at.y, 0.06), 7, 0.07, 1.1, 0.003f)
            if (k >= 1.0) {
                v.show = -1.0
                v.body.transform().setScaleVector(Vector3(Looks.height(s.id)))
                v.body.transform().eulerAngles = EulerAngles(0f, v.yaw, 0f)
            }
            return
        }
        if (swims) {
            val want = if (t.heading > 0) Looks.yaw(s.id) else Looks.yaw(s.id) + 180f
            v.yaw += (want - v.yaw) * (dt * 2).toFloat().coerceAtMost(1f)
            v.body.transform().eulerAngles = EulerAngles(0f, v.yaw, 0f)
            v.shadow.transform().eulerAngles = EulerAngles(0f, v.yaw, 0f)
            if (s.id == "manta") v.e.transform().eulerAngles = EulerAngles(Looks.bank(s.id) + (6.0 * sin(clock * 1.1 + t.phase)).toFloat(), 0f, 0f)
            if (s.id == "jelly") {
                val pulse = 1f + 0.06f * sin(clock * 2.2 + t.phase).toFloat()
                v.body.transform().setScaleVector(Vector3(Looks.height(s.id) * pulse, Looks.height(s.id) * (2f - pulse), Looks.height(s.id) * pulse))
            }
        } else if (v.yaw == 0f) {
            v.yaw = Looks.yaw(s.id).takeIf { it != 0f } ?: 0.001f
            v.body.transform().eulerAngles = EulerAngles(0f, v.yaw, 0f)
            v.shadow.transform().eulerAngles = EulerAngles(0f, v.yaw, 0f)
        }
        if (v.dark == t.lit) {
            v.dark = !t.lit
            v.body.enabled = !v.dark
            v.shadow.enabled = v.dark
        }
        v.halo?.let { h ->
            h.setPos(Vector3(t.at.x.toFloat(), t.at.y.toFloat(), 0f))
            val pulse = 0.85f + 0.15f * sin(clock * 2.6 + t.phase).toFloat()
            h.setScale((Looks.box(s.id).first * 0.38 * pulse).toFloat())
        }
    }

    /** The camera's flash, or the sampler's burst. */
    fun flashAt(at: P) {
        flash.setPos(Vector3(at.x.toFloat(), at.y.toFloat(), 0f))
        flash.enabled = true
        flashLeft = 0.18
        glitter.emit(Vec3(at.x, at.y, 0.0), 26, 0.09, 0.8, 0.003f)
    }

    fun show(dt: Double) {
        if ((clock * 4).toInt() != ((clock - dt) * 4).toInt()) {
            val k = (clock * 4).toInt()
            glitter.emit(Vec3(((k * 37) % 100) / 100.0 * 0.8 - 0.4, ((k * 61) % 100) / 100.0, 0.0), 20, 0.1, 1.5, 0.003f)
        }
    }

    fun useIbl(t: TextureResource) {
        iblSource.components.set(ImageBasedLightComponent(ImageBasedLightSource.Single(t), 7f))
        receive(root)
    }

    private fun receive(e: Entity) {
        if (iblSource.components[ImageBasedLightComponent::class.java] == null) return
        if (e.components[ModelComponent::class.java] != null && e.components[ImageBasedLightReceiverComponent::class.java] == null) {
            e.components.set(ImageBasedLightReceiverComponent(iblSource))
            e.components.set(EnvironmentLightingSettingsComponent(0f))
        }
        e.getChildren().forEach { receive(it) }
    }

    fun receiveAll() = receive(root)

    companion object {
        const val PERFORM_S = 5.0
        fun ease(t: Double) = t * t * (3 - 2 * t)

        // The plinth under the tank. 0.24 m put the lowest zone near the floor on the headset (2026-11-08 device test):
        // 18 cm higher, so the bottom of the sea is worked without bending down.
        const val BASE = 0.42f
        const val DEPTH = 0.3f
        const val CELL = 0.07
    }
}