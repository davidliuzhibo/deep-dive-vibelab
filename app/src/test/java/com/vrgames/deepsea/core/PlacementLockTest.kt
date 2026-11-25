package com.vrgames.deepsea.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.math.hypot

/**
 * Placement is locked (2026-11-23: tables and scenes moved in new versions without being asked). Where the lab's tank and
 * the cabin's console, panels and side tank sit relative to the player must stay as in 0.5.2, the last version passed on
 * the device (2026-11-21; source copy Pico_Vibe_Lab/提交包_20261121/DeepDive). The numbers come from the real
 * core.CabinLayout and the current source, held to 0.5.2's; nothing solid in front comes within 0.30 m of the eyes.
 *
 * Lab: the world at the eyes' x/z and the room's y 0, turned once to the settled heading; the tank's root (under its
 * plinth) [AHEAD] + [FARTHER] ahead on the world's floor (0.5.2 does not lift it with the measured eyes).
 * Cabin: everything about the measured eyes (core.EyeHeight, offset 0).
 */
class PlacementLockTest {
    private val src = File("src/main/java/com/vrgames/deepsea")
    private val stage = File(src, "content/DeepStage.kt").readText()
    private val sea = File(src, "render/SeaView.kt").readText()
    private val cockpit = File(src, "render/Cockpit.kt").readText()

    private fun const(text: String, name: String): Double =
        Regex("const val $name = (-?[0-9.]+)f?").find(text)?.groupValues?.get(1)?.toDouble() ?: error("$name not found")

    companion object {
        /** 0.5.2 (device-approved 2026-11-21). The lab tank. */
        const val AHEAD = 0.85
        const val FARTHER = 0.18
        const val BASE = 0.42
        const val DEPTH = 0.3
        const val COCKPIT_SCALE = 12.0
        /** The cabin console. */
        const val CONSOLE_Y = -0.50
        const val CONSOLE_Z = -0.72
        const val CONSOLE_TILT = 30.0
        const val DECK_W = 1.2
        const val DECK_T = 0.05
        const val DECK_D = 0.42
        const val FRONT_PLATE_Z = -0.90
        const val LIFT = 0.012
        /** The cabin's own tank. */
        const val TANK_YAW = 40.0
        const val TANK_DIST = 1.0
        const val TANK_DROP = 0.05
        const val TANK_SCALE = 0.48
        /** The hatch handle overhead: 0.38 m above the eyes, 0.18 m ahead, pulled down at most 0.12 m. */
        const val HATCH_Y = 0.38
        const val HATCH_Z = -0.18
        const val BODY_CLEAR = 0.30
    }

    @Test
    fun labTankStaysAs052() {
        assertEquals(AHEAD, const(stage, "AHEAD"), 1e-3)
        assertEquals(FARTHER, const(stage, "FARTHER"), 1e-3)
        assertEquals(COCKPIT_SCALE, const(stage, "COCKPIT_SCALE"), 1e-3)
        assertEquals(BASE, const(sea, "BASE"), 1e-3)
        assertEquals(DEPTH, const(sea, "DEPTH"), 1e-3)
        assertTrue("tank on the world's floor, straight ahead", stage.contains("machine?.setPos(Vector3(0f, 0f, -AHEAD - FARTHER))"))
        assertTrue("world at the eyes' x/z, y 0, turned once", stage.contains("it.setPos(Vector3(eye.x, 0f, eye.z))") && stage.contains("it.transform().eulerAngles = EulerAngles(0f, facing.yaw.toFloat(), 0f)"))
        assertTrue("plinth 10 cm wider and deeper than the tank", sea.contains("MeshResource.createBox(Vector3(w + 0.1f, BASE, d + 0.1f), 0.01f), plinth).apply { setPos(Vector3(0f, BASE / 2, 0f)) }"))
    }

    @Test
    fun cabinStaysAs052() {
        assertEquals(CONSOLE_Y, CabinLayout.CONSOLE_Y, 1e-3)
        assertEquals(CONSOLE_Z, CabinLayout.CONSOLE_Z, 1e-3)
        assertEquals(CONSOLE_TILT, CabinLayout.CONSOLE_TILT, 0.1)
        assertEquals(DECK_W, CabinLayout.DECK_W, 1e-3)
        assertEquals(DECK_T, CabinLayout.DECK_T, 1e-3)
        assertEquals(DECK_D, CabinLayout.DECK_D, 1e-3)
        assertEquals(FRONT_PLATE_Z, CabinLayout.FRONT_PLATE_Z, 1e-3)
        assertEquals(LIFT, CabinLayout.LIFT, 1e-3)
        assertEquals(TANK_YAW, CabinLayout.TANK_YAW, 0.1)
        assertEquals(TANK_DIST, CabinLayout.TANK_DIST, 1e-3)
        assertEquals(TANK_DROP, CabinLayout.TANK_DROP, 1e-3)
        assertEquals(TANK_SCALE, CabinLayout.TANK_SCALE, 1e-3)
        // Panels: the screen in the middle, the two keys 0.42 m to either side, on the deck.
        val ids = CabinLayout.PANELS.associateBy { it.id }
        assertEquals(0.0, ids["cabin"]!!.x, 1e-3); assertEquals(-0.42, ids["cabin_console"]!!.x, 1e-3); assertEquals(0.42, ids["cabin_lamp"]!!.x, 1e-3)
        // The cabin is built about the measured eyes: its root at the eye height, the side tank and panels offset from it.
        assertTrue(stage.contains("ck.root.setPos(Vector3(0f, eyeY, 0f))"))
        assertTrue(stage.contains("e.setPos(Vector3(q.x.toFloat(), (eyeY + q.y).toFloat(), q.z.toFloat()))"))
        assertTrue(cockpit.contains("setPos(Vector3(0f, CabinLayout.CONSOLE_Y.toFloat(), CabinLayout.CONSOLE_Z.toFloat()))"))
        assertTrue(cockpit.contains("setPos(Vector3(0f, ${HATCH_Y}f, ${HATCH_Z}f))"))
        // Standing 1.65 m and seated 1.25 m: the console's middle 0.50 m under the eyes, in room height.
        for (eye in listOf(1.65f, 1.25f)) {
            val e = EyeHeight()
            repeat(EyeHeight.NEED + 2) { e.feed(0.1f, eye, -0.2f, 0.05) }
            assertEquals(eye - 0.50, e.value!! + CabinLayout.CONSOLE_Y, 1e-3)
        }
    }

    @Test
    fun nothingSolidWithin30cmOfTheEyes() {
        // Lab: the plinth's near face (the tank's glass is 5 cm further out).
        val plinth = AHEAD + FARTHER - (DEPTH + 0.1) / 2
        val glass = AHEAD + FARTHER - DEPTH / 2
        assertTrue("lab plinth $plinth m", plinth >= BODY_CLEAR)
        // Cabin: the console's near rim (desk-frame z 0.21 + 1.5 cm, top 3 cm + 1.5 cm) and the deck's own near edge.
        val rim = -CabinLayout.desk(0.0, 0.045, 0.225).z
        val deck = -CabinLayout.desk(0.0, DECK_T / 2, DECK_D / 2).z
        assertTrue("console rim $rim m", rim >= BODY_CLEAR)
        assertTrue("deck $deck m", deck >= BODY_CLEAR)
        val tank = CabinLayout.tankCorners().minOf { hypot(it.x, it.z) }
        assertTrue("cabin tank $tank m", tank >= BODY_CLEAR)
        for (p in CabinLayout.PANELS) { val c = CabinLayout.panelCentre(p); assertTrue("${p.id}", hypot(c.x, c.z) >= BODY_CLEAR) }
        // The hatch handle hangs overhead (pulled down it still stays 0.25 m above the eyes), not in front of the body.
        assertTrue("hatch above the eyes", HATCH_Y - 0.12 - 0.0125 >= 0.2)
        println("placement: lab tank root ${AHEAD + FARTHER} m ahead on the room floor, plinth near face ${"%.2f".format(plinth)} m, glass ${"%.2f".format(glass)} m; cabin console ${-CONSOLE_Y} m under the eyes, rim ${"%.3f".format(rim)} m ahead; cabin tank ${TANK_DIST} m at ${TANK_YAW}° right (nearest corner ${"%.2f".format(tank)} m); hatch overhead ${HATCH_Y} m up, ${-HATCH_Z} m ahead")
    }
}
