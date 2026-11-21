package com.vrgames.deepsea.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** v0.5.2: the cabin's console, its panels and its tank (device 2026-11-19: a card half sunk in the console; the tank must be on the right). */
class CabinLayoutTest {
    /** The hull's radius (render.Cockpit.RH). */
    private val hull = 1.5

    @Test
    fun everyPanelLiesOnTheDeckAtLeast8mmAboveIt() {
        for (p in CabinLayout.PANELS) {
            for (q in CabinLayout.panelCorners(p)) {
                val up = CabinLayout.aboveDeck(q)
                assertTrue("${p.id} corner $q is ${up * 1000} mm over the deck", up >= 0.008)
                assertTrue("${p.id} corner $q floats ${up * 1000} mm over the deck", up <= 0.02)
                // Over the deck, not hanging off its edges.
                val l = CabinLayout.deskLocal(q)
                assertTrue("${p.id} off the deck sideways", kotlin.math.abs(l.x) <= CabinLayout.DECK_W / 2)
                assertTrue("${p.id} off the deck front or back", kotlin.math.abs(l.z) <= CabinLayout.DECK_D / 2)
            }
            assertTrue("${p.id} taller than 0.30 m", p.h <= 0.30)
        }
    }

    @Test
    fun theScreenSitsWhereItWasAsked() {
        val c = CabinLayout.panelCentre(CabinLayout.SCREEN)
        assertEquals(-0.468, c.y, 0.004)
        assertEquals(-0.702, c.z, 0.004)
        // Its tilt is the deck's: its face points the way the deck's top does.
        assertEquals(-60.0, CabinLayout.PANEL_TILT, 1e-9)
        val n = CabinLayout.deckUp
        val tilt = Math.toRadians(CabinLayout.PANEL_TILT)
        // A panel turned by PANEL_TILT about x faces (0, -sin, cos).
        assertEquals(n.y, -kotlin.math.sin(tilt), 1e-9)
        assertEquals(n.z, kotlin.math.cos(tilt), 1e-9)
    }

    @Test
    fun thePanelsDoNotOverlap() {
        val ps = CabinLayout.PANELS
        for (i in ps.indices) for (j in i + 1 until ps.size) {
            val a = ps[i]; val b = ps[j]
            val apart = kotlin.math.abs(a.x - b.x) >= (a.w + b.w) / 2 || kotlin.math.abs(a.zl - b.zl) >= (a.h + b.h) / 2
            assertTrue("${a.id} and ${b.id} overlap", apart)
        }
    }

    @Test
    fun theCabinTankIsOnTheRightClearOfTheConsoleAndInsideTheHull() {
        val corners = CabinLayout.tankCorners()
        println(CabinLayout.describeTank())
        // Its left edge at least 25 degrees right of straight ahead.
        assertTrue("left edge ${CabinLayout.tankLeftEdgeDeg()}°", CabinLayout.tankLeftEdgeDeg() >= 25.0)
        // Its middle about 1 m away, 40 degrees right, a little below the eyes.
        val c = CabinLayout.tankCentre()
        assertEquals(1.0, c.length(), 0.01)
        assertEquals(40.0, CabinLayout.yawOf(c), 0.01)
        assertTrue(c.y < 0 && c.y > -0.1)
        // Its glass within 1.04 m; the whole box well inside the 1.5 m hull.
        for (q in CabinLayout.tankGlassCorners()) assertTrue("glass corner ${q.length()} m", q.length() <= 1.04)
        for (q in corners) assertTrue("corner ${q.length()} m reaches the hull", q.length() <= hull - 0.3)
        // About 0.44 x 0.50 m.
        assertEquals(0.44, Sea.W * CabinLayout.TANK_SCALE, 0.01)
        assertEquals(0.50, Sea.H * CabinLayout.TANK_SCALE, 0.01)
        // It stands clear above the console: its bottom about 7 cm over the deck's back edge, and no corner in the desk.
        val over = CabinLayout.tankBottomOverConsole()
        assertTrue("bottom $over m over the console's back edge", over in 0.05..0.10)
        for (q in corners) assertFalse("corner $q inside the console", CabinLayout.inDesk(q, 0.03))
        // Nothing on the deck is hidden behind it: every panel corner is either left of the tank or below it in view.
        val left = CabinLayout.tankLeftEdgeDeg()
        val bottom = corners.minOf { CabinLayout.elevationOf(it) }
        for (p in CabinLayout.PANELS) for (q in CabinLayout.panelCorners(p)) {
            assertTrue("${p.id} hidden behind the tank", CabinLayout.yawOf(q) < left || CabinLayout.elevationOf(q) < bottom - 3)
        }
    }

    @Test
    fun theTankViewIsTurnedToFaceTheEyes() {
        // The view is turned by tankViewYaw about y: +z (out of the glass) then points back at the eyes.
        val a = Math.toRadians(CabinLayout.tankViewYaw)
        val out = Vec3(kotlin.math.sin(a), 0.0, kotlin.math.cos(a))
        val toEyes = (Vec3.ZERO - CabinLayout.tankCentre()).let { Vec3(it.x, 0.0, it.z) }.normalized()
        assertEquals(1.0, out.dot(toEyes), 1e-6)
        // The root sits under the sea's middle by the sea's base and half its height, scaled.
        val r = CabinLayout.tankRoot()
        assertEquals(CabinLayout.tankCentre().y - CabinLayout.TANK_SCALE * (CabinLayout.TANK_BASE + Sea.H / 2), r.y, 1e-9)
    }

    @Test
    fun theConsolesCopyStandsLeftInsideTheHull() {
        val c = CabinLayout.copyCentre()
        assertTrue(CabinLayout.yawOf(c) < -30)
        assertTrue(c.length() < hull - 0.4)
    }
}
