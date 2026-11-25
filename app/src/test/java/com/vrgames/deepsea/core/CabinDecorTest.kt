package com.vrgames.deepsea.core

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.math.acos
import kotlin.math.hypot
import kotlin.math.sqrt

/**
 * v0.7.0: the cabin's dressing stays out of the way — not in front of any window, not over the tank or the console copy,
 * inside the sphere, clear of the face — and nothing but the real controls is orange or takes a pinch.
 */
class CabinDecorTest {
    private val d = CabinDecor

    /** The windows (render.Cockpit.WINDOWS): direction and half angle (degrees). */
    private val windows = listOf(Vec3(0.0, -0.02, -1.0) to 44.0, Vec3(-0.9, 0.0, -0.45) to 18.0, Vec3(0.9, 0.0, -0.45) to 18.0)

    private fun angle(a: Vec3, b: Vec3) = Math.toDegrees(acos((a.normalized().dot(b.normalized())).coerceIn(-1.0, 1.0)))

    @Test
    fun nothingStandsInAWindow() {
        for (s in d.allSpots()) {
            if (s.name == "seat") continue
            for ((w, half) in windows) {
                // The thing's own angular size counts too.
                val r = Math.toDegrees(kotlin.math.atan2(s.size / 2, s.at.length()))
                assertTrue("${s.name} ${"%.1f".format(angle(s.at, w))}° from a window's middle (half $half°, its size $r°)", angle(s.at, w) >= half + r)
            }
        }
    }

    @Test
    fun everythingIsInsideTheSphereAndClearOfTheFace() {
        // The props stand on their base (the models' origin): their back edge (a third of their size behind the spot), at
        // the base and at the top, must be inside the sphere.
        for (p in d.PROPS) {
            if (p.name == "seat") continue
            val back = hypot(p.at.x, p.at.z) + p.size * 0.33
            for (y in listOf(p.at.y, p.at.y + p.size)) assertTrue("${p.name} back edge ${"%.2f".format(hypot(back, y))} m at y ${"%.2f".format(y)}", hypot(back, y) <= d.HULL_R - 0.02)
        }
        for (s in d.allSpots()) {
            if (s.name == "seat" || s in d.PROPS) continue
            val far = s.at.length() + s.size / 2
            assertTrue("${s.name} reaches ${"%.2f".format(far)} m (hull ${d.HULL_R})", far <= d.HULL_R - 0.02)
            assertTrue("${s.name} ${"%.2f".format(s.at.length())} m from the eyes", s.at.length() - s.size / 2 >= 0.45)
            assertTrue("${s.name} above the floor", s.at.y >= d.FLOOR_Y)
        }
        // The floor-standing seat fits the floor's circle (√(1.5² − 1.2²) = 0.9 m).
        val seat = d.PROPS.first { it.name == "seat" }
        assertTrue(hypot(seat.at.x, seat.at.z) + seat.size / 2 <= sqrt(d.HULL_R * d.HULL_R - d.FLOOR_Y * d.FLOOR_Y))
    }

    @Test
    fun theTankAndTheConsoleCopyStayClear() {
        val tank = CabinLayout.tankCorners()
        val tl = tank.minOf { CabinLayout.yawOf(it) }; val tr = tank.maxOf { CabinLayout.yawOf(it) }
        val tb = tank.minOf { CabinLayout.elevationOf(it) }
        val copy = CabinLayout.copyCentre()
        for (s in d.allSpots()) {
            val yaw = CabinLayout.yawOf(s.at); val el = CabinLayout.elevationOf(s.at)
            val top = el + Math.toDegrees(kotlin.math.atan2(s.size / 2, s.at.length()))
            if (yaw in tl..tr) assertTrue("${s.name} under the tank: top ${"%.1f".format(top)}° vs tank bottom ${"%.1f".format(tb)}°", top < tb - 2 || s.at.length() > tank.maxOf { it.length() } + 0.1)
            assertTrue("${s.name} clear of the console copy", s.at.distanceTo(copy) > 0.3 || el < CabinLayout.elevationOf(copy) - 15)
        }
    }

    @Test
    fun onlyTheRealControlsAreOrangeOrTakeAPinch() {
        val dressing = File("src/main/java/com/vrgames/deepsea/render/CabinDressing.kt").readText()
        assertTrue("no collider in the dressing", !dressing.contains("CollisionComponent") && !dressing.contains("InteractableComponent") && !dressing.contains("HoverEffectComponent"))
        assertTrue("no orange in the dressing", !dressing.contains("ORANGE") && !dressing.contains("0xFFFF8C1F"))
        // No amber or orange among its glowing dots (red, green, cyan and white only).
        assertTrue(dressing.contains("color(0.3f, 1f, 0.45f), color(0.35f, 0.85f, 1f), color(0.9f, 0.95f, 1f), color(1f, 0.25f, 0.2f)"))
        val cockpit = File("src/main/java/com/vrgames/deepsea/render/Cockpit.kt").readText()
        assertTrue("the old amber LED is gone", !cockpit.contains("color(1f, 0.72f, 0.2f)"))
    }
}
