package com.vrgames.deepsea.core

import kotlin.math.cos
import kotlin.math.sin

/**
 * v0.7.0 (the user, 2026-11-25: 「舱内更有氛围感，更像仿真的潜水器，科技感更强……尤其是驾驶舱和操作台……主要用于真正操作的那几个
 * 按钮还是要突出，而且要跟周边的装饰性的按钮和装置区分开来」): where the cabin's dressing goes, about the eyes (x right, y up,
 * -z ahead, metres), in one place so the tests can keep it out of the windows, the tank, the console copy and the face.
 *
 * Modelled on a real manned submersible's sphere (Fendouzhe / Jiaolong class: a 2 m titanium sphere, quilted insulation,
 * cable runs, a multi-screen pilot desk with joystick and throttles, oxygen and CO2 scrubbing, emergency kit).
 *
 * The rule the user asked for: ONLY the real controls glow orange ([ORANGE]) — the deck's three keys, the hatch handle, the
 * small tank's frame. Nothing else in the cabin is orange, glows like them, or takes a pinch.
 */
object CabinDecor {
    /** The real controls' colour (r, g, b): bright amber orange. */
    val ORANGE = floatArrayOf(1f, 0.55f, 0.12f)

    /** Where a thing stands: [yaw] degrees right of straight ahead, [dist] metres out (level), [y] up from the eyes. */
    data class Spot(val name: String, val yaw: Double, val dist: Double, val y: Double, val size: Double) {
        val at: Vec3 get() { val a = Math.toRadians(yaw); return Vec3(dist * sin(a), y, -dist * cos(a)) }
    }

    // ---- The side desks (wings) left and right of the pilot desk, low, tipped up toward the player ----
    const val WING_YAW = 58.0
    const val WING_DIST = 0.78
    const val WING_Y = -0.50
    const val WING_TILT = 25.0
    const val WING_W = 0.44
    const val WING_D = 0.30

    /** The props (Seed3D models, assets/models): name, where, size (their longest side, m). */
    val PROPS = listOf(
        // The pilot's seat: under and behind the player; only its cushion's edge, the armrests and the belts show.
        Spot("seat", 0.0, -0.08, -1.20, 1.10),
        // The acoustic telephone on the left wall and the arm master on the right, under the side windows.
        Spot("radio", -80.0, 1.12, -0.66, 0.24),
        Spot("armctrl", 84.0, 1.1, -0.62, 0.2),
        // Life support and the emergency kit on the sphere's wall, behind the side windows (the sphere is 1.5 m round the
        // eyes and the floor 1.2 m down, so low down the wall is near: each sits inside it, see [HULL_R]).
        Spot("o2rack", -128.0, 0.96, -0.90, 0.55),
        Spot("scrubber", 128.0, 1.05, -0.86, 0.46),
        Spot("extinguisher", 102.0, 1.12, -0.62, 0.42),
        Spot("firstaid", -100.0, 1.28, -0.42, 0.30),
        Spot("valve", 160.0, 1.28, -0.30, 0.28),
    )

    /** The sphere's radius about the eyes (render.Cockpit.RH) and the floor's height. */
    const val HULL_R = 1.5
    const val FLOOR_Y = -1.2

    /** The little text plates (cream on dark, never orange), each by what it names. */
    val PLATES = listOf(
        Spot("O₂ 供氧", -128.0, 1.06, -0.28, 0.12),
        Spot("CO₂ 吸收", 128.0, 1.10, -0.36, 0.12),
        Spot("应急灭火", 100.0, 1.26, -0.12, 0.12),
        Spot("急救", -100.0, 1.32, -0.06, 0.08),
        Spot("载人舱 · 6000 米级", 180.0, 1.3, 0.18, 0.24),
        Spot("压载 · 抛载", -58.0, 0.86, -0.50, 0.12),
        Spot("推进 · 机械臂", 58.0, 0.86, -0.50, 0.12),
    )

    /**
     * What stands ON the side desks, in the desk's own frame (x along it, y up from its top, z toward the player): the
     * thruster joystick and the arm master on the right (by the right hand), the throttles and the sonar on the left.
     */
    data class OnWing(val name: String, val right: Boolean, val x: Double, val y: Double, val z: Double, val size: Double)
    val ON_WINGS = listOf(
        OnWing("joystick", true, -0.13, 0.02, 0.02, 0.16),
        OnWing("throttle", false, -0.14, 0.02, -0.02, 0.16),
        OnWing("sonar", false, 0.08, 0.026, -0.03, 0.18),
    )
    /** The live instrument screen: flat on the right desk beside the joystick (a panel, no keys), in the desk's frame. */
    val GAUGES_ON_WING = OnWing("gauges", true, 0.09, 0.026, -0.02, 0.20)
    const val GAUGES_H = 0.13
    /** Its tilt about x as a panel lying on the desk (as core.CabinLayout.PANEL_TILT on the pilot desk). */
    const val GAUGES_PITCH = -(90.0 - WING_TILT)

    /** A point of a side desk ([right] or left) in its own frame (x along, y up from its middle, z toward the player), about the eyes. */
    fun onWing(right: Boolean, x: Double, y: Double, z: Double): Vec3 {
        val t = Math.toRadians(WING_TILT)
        val yy = y * cos(t) - z * sin(t); val zz = y * sin(t) + z * cos(t)
        val yaw = if (right) WING_YAW else -WING_YAW
        val f = Math.toRadians(-yaw)
        val x2 = x * cos(f) + zz * sin(f); val z2 = -x * sin(f) + zz * cos(f)
        val c = wingCentre(right).at
        return Vec3(c.x + x2, c.y + yy, c.z + z2)
    }

    /** The instrument screen's middle about the eyes. */
    val GAUGES: Spot get() { val q = onWing(true, GAUGES_ON_WING.x, GAUGES_ON_WING.y, GAUGES_ON_WING.z); return Spot("gauges", CabinLayout.yawOf(q), Math.hypot(q.x, q.z), q.y, GAUGES_ON_WING.size) }

    /** A desk's middle (about the eyes). */
    fun wingCentre(right: Boolean) = Spot("wing", if (right) WING_YAW else -WING_YAW, WING_DIST, WING_Y, WING_W)

    /** The ceiling's work lights (cool white, soft), either side of the hatch. */
    val LIGHTS = listOf(Spot("light", -30.0, 0.62, 1.12, 0.22), Spot("light", 30.0, 0.62, 1.12, 0.22))

    /** Ribs down the sphere (their yaw), clear of the windows: behind and to the sides. */
    val RIB_YAWS = listOf(-150.0, -105.0, 105.0, 150.0, 180.0)
    /** Rings round the ceiling (degrees above level). */
    val RING_ELEVATIONS = listOf(52.0, 72.0)
    /** The cable runs: along the hull at this elevation, between these yaws, each side. */
    const val CABLE_ELEVATION = -27.0
    const val CABLE_YAW_FROM = 88.0
    const val CABLE_YAW_TO = 172.0

    /** Every place something of the dressing stands (for the tests). */
    fun allSpots(): List<Spot> = PROPS + listOf(GAUGES, wingCentre(true), wingCentre(false)) + LIGHTS + PLATES
}
