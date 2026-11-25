package com.vrgames.deepsea.render

import com.pico.spatial.core.ecs.Entity
import com.pico.spatial.core.ecs.ModelComponent
import com.pico.spatial.core.ecs.ModelEntity
import com.pico.spatial.core.ecs.resource.Material
import com.pico.spatial.core.ecs.resource.MeshInstancesResource
import com.pico.spatial.core.ecs.resource.MeshResource
import com.pico.spatial.core.math.EulerAngles
import com.pico.spatial.core.math.Transform
import com.pico.spatial.core.math.Vector3
import com.vrgames.deepsea.core.CabinMotion
import kotlin.math.atan2
import kotlin.math.hypot

/**
 * v0.5.2 (device 2026-11-19: no sense of moving in the cabin): specks suspended in the sea outside the cabin, fixed in
 * the water, so they stream past the windows the other way as the sub moves — [COUNT] of them in a box about the eyes
 * (x ±[HALF_X], y ±[HALF_Y], [NEAR]..[FAR] m ahead, some of it out of the side windows), wrapping round at its sides. At
 * speed each draws out into a streak along its way, up to [STREAK] m long. Replaces the old snow, which drifted the same
 * way whatever the sub did.
 */
class Motes(
    mesh: MeshResource,
    material: Material,
    /** v0.6.0: the same specks also make the far layer and the glowing plankton (twinkling), each with its own name. */
    name: String = "cabin_motes",
    private val COUNT: Int = 300,
    private val NEAR: Double = 1.7,
    private val FAR: Double = 7.7,
    private val HALF_X: Double = 5.0,
    private val HALF_Y: Double = 3.0,
    private val sizeMin: Double = 0.006,
    private val sizeMax: Double = 0.014,
    private val twinkle: Boolean = false,
) {
    val entity: Entity = ModelEntity(mesh, material)
    private val names = Array(COUNT) { "$name$it" }
    private val instances = MeshInstancesResource.create(name, names.map { MeshInstancesResource.Instance(it, HIDDEN) })
    private val phase = DoubleArray(COUNT)
    private val px = DoubleArray(COUNT)
    private val py = DoubleArray(COUNT)
    private val pz = DoubleArray(COUNT)
    private val size = FloatArray(COUNT)
    private var vx = 0.0
    private var vy = 0.0
    private var clock = 0.0
    /** The specks' speed past the windows (m/s), for the log. */
    val speed get() = hypot(vx, vy)
    val count get() = COUNT

    init {
        entity.components[ModelComponent::class.java]!!.meshInstances = instances
        val r = java.util.Random(7)
        for (i in 0 until COUNT) {
            if (i < COUNT / 6) {
                // Out of the side windows (straight out to the sides and a little ahead).
                val side = if (i % 2 == 0) 1.0 else -1.0
                px[i] = side * (2.4 + r.nextDouble() * (HALF_X - 2.4))
                pz[i] = -(NEAR + r.nextDouble() * 2.2)
            } else {
                px[i] = (r.nextDouble() * 2 - 1) * HALF_X
                pz[i] = -(NEAR + r.nextDouble() * (FAR - NEAR))
            }
            py[i] = (r.nextDouble() * 2 - 1) * HALF_Y
            size[i] = (sizeMin + (sizeMax - sizeMin) * r.nextDouble()).toFloat()
            phase[i] = r.nextDouble() * 100
        }
    }

    /**
     * Once a frame: everything moves by ([dx], [dy]) m — the opposite of the sub's move, as large as the sea is shown — and
     * a slow sinking; [dt] seconds.
     */
    fun frame(dx: Double, dy: Double, dt: Double) {
        if (dt <= 0) return
        clock += dt
        val k = (dt * 8).coerceAtMost(1.0)
        vx += (dx / dt - vx) * k
        vy += (dy / dt - vy) * k
        val sink = SINK * dt
        val s = speed
        val streak = (s * STREAK_S).coerceAtMost(STREAK)
        val roll = Math.toDegrees(atan2(vy, vx)).toFloat()
        for (i in 0 until COUNT) {
            px[i] = CabinMotion.wrap(px[i] + dx, HALF_X * 2)
            py[i] = CabinMotion.wrap(py[i] + dy - sink, HALF_Y * 2)
            // Plankton flashes now and then, dark between (v0.6.0).
            val d = if (twinkle) size[i] * (kotlin.math.sin(clock * (0.7 + (i % 7) * 0.23) + phase[i]).let { w -> (w * w * w * w).toFloat() }) else size[i]
            // A sphere of radius d, drawn out along the way it goes into a streak [streak] long.
            val len = maxOf(d, (streak / 2).toFloat())
            instances.update(
                names[i],
                Transform(Vector3(px[i].toFloat(), py[i].toFloat(), pz[i].toFloat()), if (streak > 0.002) EulerAngles(0f, 0f, roll) else NO_TURN, Vector3(len, d, d)),
            )
        }
    }

    companion object {
        /** Longest streak (m), and how many seconds of travel a streak shows. */
        const val STREAK = 0.15
        const val STREAK_S = 0.25
        /** Marine snow sinks slowly even when the sub stands (m/s). */
        const val SINK = 0.015
        private val NO_TURN = EulerAngles(0f, 0f, 0f)
        private val HIDDEN = Transform(Vector3(0f, -100f, 0f), NO_TURN, Vector3(0f))
    }
}
