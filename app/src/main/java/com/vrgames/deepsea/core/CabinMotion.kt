package com.vrgames.deepsea.core

import kotlin.math.atan2
import kotlin.math.hypot

/**
 * v0.5.2 (device 2026-11-19: no sense of moving in the cabin): how the sea outside the cabin moves with the sub. Pure, so it
 * is unit-tested. Once a frame [step] gets the point of the tank's sea the cabin is at (x across, y up, metres in the tank)
 * and works out
 * - [dx], [dy]: how far everything fixed in the sea moves past the windows this frame, in the cabin's metres: the opposite
 *   of the sub's own move, [SCALE] times larger (the sea in the cabin is the tank's grown twelve times);
 * - [speed]: the sub's speed in the cabin's metres per second (from the dive's own smooth speed), and [vx], [vy] its
 *   direction (from the moves, smoothed);
 * - [distance]: how far ahead the sea's plane stands. Speeding up, it draws [PULL] m closer over [EASE_S] seconds (the
 *   sub surges forward); slowing down, it goes back.
 */
class CabinMotion {
    var dx = 0.0; private set
    var dy = 0.0; private set
    /** Smoothed velocity in the cabin's metres per second (the sub's, not the sea's). */
    var vx = 0.0; private set
    var vy = 0.0; private set
    /** The sub's speed in the cabin's metres per second. */
    var speed = 0.0; private set
    var distance = FAR; private set
    /** True while drawing in or drawn in. */
    var pulling = false; private set
    private var u = 0.0
    private var last: P? = null
    private var accel = 0.0
    private var lastSpeed = 0.0

    fun reset() { last = null; vx = 0.0; vy = 0.0; speed = 0.0; dx = 0.0; dy = 0.0; u = 0.0; distance = FAR; pulling = false; accel = 0.0; lastSpeed = 0.0 }

    /**
     * One frame: [at] the place in the tank the cabin is at, [subSpeed] the dive's sub speed (tank metres per second; it
     * changes smoothly, unlike the moves seen frame by frame), [dt] seconds.
     */
    fun step(at: P, subSpeed: Double, dt: Double) {
        val before = last
        last = at
        if (before == null || dt <= 0) { dx = 0.0; dy = 0.0; return }
        val mx = (at.x - before.x) * SCALE
        val my = (at.y - before.y) * SCALE
        // A jump (a new level, the view placed again) is not a move.
        if (hypot(mx, my) > JUMP) { dx = 0.0; dy = 0.0; return }
        dx = -mx; dy = -my
        val k = (dt * 6).coerceAtMost(1.0)
        vx += (mx / dt - vx) * k
        vy += (my / dt - vy) * k
        val s = subSpeed * SCALE
        speed = s
        accel += ((s - lastSpeed) / dt - accel) * (dt * 3).coerceAtMost(1.0)
        lastSpeed = s
        // Speeding up draws the sea in; slowing down (or stopped) lets it go back.
        if (s < STILL) pulling = false
        else if (accel > ACCEL_IN) pulling = true
        else if (accel < -ACCEL_IN) pulling = false
        u = if (pulling) (u + dt / EASE_S).coerceAtMost(1.0) else (u - dt / EASE_S).coerceAtLeast(0.0)
        distance = FAR - PULL * ease(u)
    }

    /** 「航速 x.x 节 →」: the sub's speed as the cabin screen shows it, its top speed being [TOP_KNOTS]. */
    fun knots(): Double = speed / (Dive.MAX_SPEED * SCALE) * TOP_KNOTS

    /** An arrow for the way the sub goes (the sea goes the other way), or "" when it stands. */
    fun arrow(): String {
        if (speed < STILL) return ""
        val a = Math.toDegrees(atan2(vy, vx))
        val k = Math.floorMod(Math.round(a / 45.0).toInt(), 8)
        return listOf("→", "↗", "↑", "↖", "←", "↙", "↓", "↘")[k]
    }

    companion object {
        /** The cabin's sea is the tank's this many times larger (content.COCKPIT_SCALE). */
        const val SCALE = 12.0
        /** The sea's plane ahead of the eyes at rest, and drawn in. */
        const val FAR = 4.0
        const val PULL = 0.5
        const val EASE_S = 0.8
        /** Cabin metres per second: below this the sub stands. */
        const val STILL = 0.03
        /** Cabin metres per second per second. */
        const val ACCEL_IN = 0.06
        /** A move longer than this in one frame (cabin metres) is a jump, not a move. */
        const val JUMP = 0.5
        /** The sub's top speed on the gauge (deep-sea research subs make about 2–3 knots). */
        const val TOP_KNOTS = 2.5

        fun ease(t: Double) = t * t * (3 - 2 * t)

        /** [v] brought into [-period/2, period/2). */
        fun wrap(v: Double, period: Double): Double {
            val h = period / 2
            var r = (v + h) % period
            if (r < 0) r += period
            return r - h
        }
    }
}
