package com.vrgames.deepsea.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** v0.5.2: the sea outside the cabin moves with the sub (device 2026-11-19: no sense of moving in the cabin). */
class CabinMotionTest {
    private val dt = 1.0 / 72

    @Test
    fun theSeaMovesTheOtherWayTwelveTimesTheSubsMove() {
        val m = CabinMotion()
        m.step(P(0.0, 0.5), 0.0, dt)
        m.step(P(0.01, 0.5 - 0.002), 0.03, dt)
        assertEquals(-0.12, m.dx, 1e-9)
        assertEquals(0.024, m.dy, 1e-9)
    }

    @Test
    fun aJumpIsNotAMove() {
        val m = CabinMotion()
        m.step(P(-0.3, 0.9), 0.0, dt)
        m.step(P(0.3, 0.2), 0.0, dt)
        assertEquals(0.0, m.dx, 0.0)
        assertEquals(0.0, m.dy, 0.0)
    }

    @Test
    fun speedingUpDrawsTheSeaInAndSlowingLetsItGoBack() {
        // A dive's sub starting off to a far point, then stopping there.
        val d = Dive(Stages.byId("a1"))
        d.steer(P(-0.35, d.sub.at.y))
        val m = CabinMotion()
        var nearest = CabinMotion.FAR
        var t = 0.0
        var inAt = -1.0
        while (t < 30) {
            d.step(dt)
            m.step(d.sub.at, d.sub.speed, dt)
            nearest = minOf(nearest, m.distance)
            if (inAt < 0 && m.distance <= CabinMotion.FAR - CabinMotion.PULL + 1e-6) inAt = t
            t += dt
        }
        assertEquals("drawn in by half a metre", CabinMotion.FAR - CabinMotion.PULL, nearest, 1e-6)
        assertTrue("drawn in within about a second of setting off ($inAt s)", inAt in 0.7..1.6)
        assertEquals("back out once stopped", CabinMotion.FAR, m.distance, 1e-9)
        assertFalse(m.pulling)
    }

    @Test
    fun theEaseTakesAbout08Seconds() {
        val m = CabinMotion()
        var x = 0.0
        var speed = 0.0
        m.step(P(x, 0.5), speed, dt)
        var t = 0.0
        var start = -1.0
        var full = -1.0
        while (t < 3) {
            speed = minOf(0.05, speed + 0.05 * dt)
            x += speed * dt
            m.step(P(x, 0.5), speed, dt)
            if (start < 0 && m.pulling) start = t
            if (full < 0 && m.distance <= CabinMotion.FAR - CabinMotion.PULL + 1e-6) full = t
            t += dt
        }
        assertTrue("$start .. $full", start >= 0 && full > start)
        assertEquals(CabinMotion.EASE_S, full - start, 0.05)
    }

    @Test
    fun theGaugeReadsKnotsAndTheWay() {
        val m = CabinMotion()
        var x = 0.0
        m.step(P(x, 0.5), 0.0, dt)
        repeat(200) { x += Dive.MAX_SPEED * dt; m.step(P(x, 0.5), Dive.MAX_SPEED, dt) }
        assertEquals(CabinMotion.TOP_KNOTS, m.knots(), 1e-6)
        assertEquals("→", m.arrow())
        val down = CabinMotion()
        var y = 0.9
        down.step(P(0.0, y), 0.0, dt)
        repeat(200) { y -= 0.03 * dt; down.step(P(0.0, y), 0.03, dt) }
        assertEquals("↓", down.arrow())
    }

    @Test
    fun wrapKeepsThingsInTheirBand() {
        assertEquals(-8.0, CabinMotion.wrap(10.0, 18.0), 1e-9)
        assertEquals(8.0, CabinMotion.wrap(-10.0, 18.0), 1e-9)
        assertEquals(3.0, CabinMotion.wrap(3.0, 18.0), 1e-9)
        assertEquals(-9.0, CabinMotion.wrap(9.0, 18.0), 1e-9)
        assertEquals(-30.0, CabinMotion.wrap(70.0, 100.0), 1e-9)
    }
}
