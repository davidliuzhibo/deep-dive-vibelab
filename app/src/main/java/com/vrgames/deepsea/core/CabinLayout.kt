package com.vrgames.deepsea.core

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/**
 * v0.5.2: where things stand in the cabin, about the eyes (x right, y up, -z straight ahead), in metres. Pure maths, so
 * the unit tests can check that nothing sinks into the console or reaches the hull.
 *
 * - The console: a desk across the cabin under the front window, its top tipped up toward the player by [CONSOLE_TILT].
 *   0.5.2 (device 2026-11-19, the species card half sunk into it): 12 cm farther out (0.60 → 0.72 m), and every panel now
 *   lies ON its deck, [LIFT] above it, with the deck's own tilt.
 * - The tank: the same sea and the same sub as the tank window, [TANK_SCALE] times its size, [TANK_YAW]° to the right
 *   (device 2026-11-19: in the cabin the tank must be on the right; the Shared Space window cannot be moved, so the cabin
 *   has its own copy and the window folds away).
 */
object CabinLayout {
    const val CONSOLE_Y = -0.50
    const val CONSOLE_Z = -0.72
    /** Degrees about x: the deck's top turned up toward the player. */
    const val CONSOLE_TILT = 30.0
    const val DECK_W = 1.2
    const val DECK_T = 0.05
    const val DECK_D = 0.42
    /** The desk's front plate down to the floor (was 0.78 with the console at 0.60). */
    const val FRONT_PLATE_Z = -0.90
    /** Panels lie this far above the deck. */
    const val LIFT = 0.012
    /** The panels' tilt about x (degrees): flat on the deck. */
    const val PANEL_TILT = -(90.0 - CONSOLE_TILT)

    /** A panel on the deck: its middle at [x] across and [zl] along the deck (+ toward the player), [w] by [h] metres. */
    class Panel(val id: String, val x: Double, val zl: Double, val w: Double, val h: Double)

    /** The screen in the middle: the state and one big button, 「回到舱外视角」. */
    val SCREEN = Panel("cabin", 0.0, 0.0, 0.46, 0.29)
    /** Left: 「打开控制台 / 收起控制台」. */
    val CONSOLE_KEY = Panel("cabin_console", -0.42, 0.01, 0.28, 0.16)
    /** Right: 「探照灯：开 / 关」. */
    val LAMP_KEY = Panel("cabin_lamp", 0.42, 0.01, 0.28, 0.16)
    val PANELS = listOf(SCREEN, CONSOLE_KEY, LAMP_KEY)

    /** A point of the desk in its own frame (x across, y up from its middle, z toward the player), about the eyes. */
    fun desk(x: Double, y: Double, z: Double): Vec3 {
        val a = Math.toRadians(CONSOLE_TILT)
        return Vec3(x, CONSOLE_Y + y * cos(a) - z * sin(a), CONSOLE_Z + y * sin(a) + z * cos(a))
    }

    /** The deck's top face points this way. */
    val deckUp: Vec3 get() = desk(0.0, 1.0, 0.0) - desk(0.0, 0.0, 0.0)

    /** How far [q] is above the deck's top face. */
    fun aboveDeck(q: Vec3): Double = (q - desk(0.0, DECK_T / 2, 0.0)).dot(deckUp)

    /** [q] in the desk's own frame (the inverse of [desk]). */
    fun deskLocal(q: Vec3): Vec3 {
        val a = Math.toRadians(CONSOLE_TILT)
        val y = q.y - CONSOLE_Y
        val z = q.z - CONSOLE_Z
        return Vec3(q.x, y * cos(a) + z * sin(a), -y * sin(a) + z * cos(a))
    }

    /** Whether [q] is inside the desk's slab (with [margin] round it). */
    fun inDesk(q: Vec3, margin: Double = 0.0): Boolean {
        val l = deskLocal(q)
        return kotlin.math.abs(l.x) <= DECK_W / 2 + margin && kotlin.math.abs(l.y) <= DECK_T / 2 + margin && kotlin.math.abs(l.z) <= DECK_D / 2 + margin
    }

    fun panelCentre(p: Panel): Vec3 = desk(p.x, DECK_T / 2 + LIFT, p.zl)

    /** A panel's four corners: its own up is the deck's far direction (-z of the desk). */
    fun panelCorners(p: Panel): List<Vec3> = listOf(-1.0 to -1.0, 1.0 to -1.0, 1.0 to 1.0, -1.0 to 1.0).map { (sx, sy) ->
        desk(p.x + sx * p.w / 2, DECK_T / 2 + LIFT, p.zl - sy * p.h / 2)
    }

    /** The deck's far (high) edge, the one the tank stands over. */
    val deckBackEdge: Vec3 get() = desk(DECK_W / 2, DECK_T / 2, -DECK_D / 2)

    // ---- The tank in the cabin ----
    const val TANK_YAW = 40.0
    const val TANK_DIST = 1.0
    /** Its middle this far below the eyes. */
    const val TANK_DROP = 0.05
    const val TANK_SCALE = 0.48
    /** The tank's sea and frame bars, as in render.SeaView (W, H, DEPTH and the 1.8 cm bar), unscaled. */
    const val TANK_DEPTH = 0.3
    const val TANK_BAR = 0.018
    /** render.SeaView.BASE: the sea stands this high over the view's root. */
    const val TANK_BASE = 0.42

    /** The tank's middle about the eyes. */
    fun tankCentre(): Vec3 {
        val a = Math.toRadians(TANK_YAW)
        return Vec3(TANK_DIST * sin(a), -TANK_DROP, -TANK_DIST * cos(a))
    }

    /** The yaw the tank's view is turned by (degrees; + turns -z to the left): its glass faces the eyes. */
    val tankViewYaw: Double get() = -TANK_YAW

    /** Where the tank's view root goes about the eyes (the root is under the middle of the tank's floor, below the sea). */
    fun tankRoot(): Vec3 = tankCentre() - Vec3(0.0, TANK_SCALE * (TANK_BASE + Sea.H / 2), 0.0)

    private fun tankAxes(): Triple<Vec3, Vec3, Vec3> {
        val a = Math.toRadians(TANK_YAW)
        val right = Vec3(cos(a), 0.0, sin(a))
        val out = Vec3(-sin(a), 0.0, cos(a))
        return Triple(right, Vec3.UP, out)
    }

    private fun tankBox(front: Boolean?): List<Vec3> {
        val c = tankCentre()
        val (r, u, o) = tankAxes()
        val hw = (Sea.W / 2 + TANK_BAR / 2) * TANK_SCALE
        val hh = (Sea.H / 2 + TANK_BAR / 2) * TANK_SCALE
        val hd = (TANK_DEPTH / 2 + TANK_BAR / 2) * TANK_SCALE
        val faces = when (front) { true -> listOf(1.0); false -> listOf(-1.0); null -> listOf(-1.0, 1.0) }
        return faces.flatMap { sz -> listOf(-1.0 to -1.0, 1.0 to -1.0, 1.0 to 1.0, -1.0 to 1.0).map { (sx, sy) -> c + r * (sx * hw) + u * (sy * hh) + o * (sz * hd) } }
    }

    /** The 8 corners of the tank and its frame. */
    fun tankCorners(): List<Vec3> = tankBox(null)

    /** The 4 corners of its front glass. */
    fun tankGlassCorners(): List<Vec3> = tankBox(true)

    /** Degrees to the right of straight ahead (left is negative). */
    fun yawOf(q: Vec3): Double = Math.toDegrees(atan2(q.x, -q.z))

    /** Degrees above the eyes' level. */
    fun elevationOf(q: Vec3): Double = Math.toDegrees(atan2(q.y, hypot(q.x, q.z)))

    /** The tank's edge nearest straight ahead (degrees right). */
    fun tankLeftEdgeDeg(): Double = tankCorners().minOf { yawOf(it) }

    /** Its bottom over the deck's back edge (metres). */
    fun tankBottomOverConsole(): Double = tankCorners().minOf { it.y } - deckBackEdge.y

    /** One line for the log. */
    fun describeTank(): String {
        val far = tankCorners().maxOf { it.length() }
        val glass = tankGlassCorners().maxOf { it.length() }
        return "yaw ${TANK_YAW.toInt()}° dist ${"%.2f".format(TANK_DIST)} m drop ${"%.2f".format(TANK_DROP)} scale $TANK_SCALE " +
            "size ${"%.2f".format(Sea.W * TANK_SCALE)}x${"%.2f".format(Sea.H * TANK_SCALE)} m, left edge ${"%.1f".format(tankLeftEdgeDeg())}°, " +
            "glass corners ≤${"%.2f".format(glass)} m, far corner ${"%.2f".format(far)} m, bottom ${"%.1f".format(tankBottomOverConsole() * 100)} cm over the console's back edge"
    }

    // ---- The console's copy in the cabin (only when the console window will not come back) ----
    const val COPY_YAW = 40.0
    const val COPY_DIST = 0.95
    const val COPY_DROP = 0.08

    /** The copy's middle about the eyes, to the left. */
    fun copyCentre(): Vec3 {
        val a = Math.toRadians(COPY_YAW)
        return Vec3(-COPY_DIST * sin(a), -COPY_DROP, -COPY_DIST * cos(a))
    }
}
