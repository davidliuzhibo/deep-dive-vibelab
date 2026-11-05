package com.vrgames.deepsea.render

/**
 * Sizes and facings of the models, from their four-side renders. Models come normalized to 1 m along
 * their longest side, standing on the origin, so the scale is the longest side in the tank.
 */
object Looks {
    fun height(id: String): Float = SCALE[id] ?: 0.08f

    /** How high the middle of the body is, as a share of the scale: swimmers are placed by their middle. */
    fun mid(id: String): Float = MID[id] ?: 0.5f

    /** The pinch box, wide and high. */
    fun box(id: String): Pair<Double, Double> = BOX[id] ?: (0.1 to 0.1)

    /** The yaw at which the model faces +x (to the right). */
    fun yaw(id: String): Float = YAW[id] ?: 0f

    /** Flat animals are seen edge-on from the side: they swim banked, their backs toward the player. */
    fun bank(id: String): Float = BANK[id] ?: 0f

    const val TROPHY_TURN = -90f

    private val SCALE = mapOf("sub" to 0.09f, "manta" to 0.17f, "turtle" to 0.13f, "coral" to 0.12f, "lantern" to 0.06f, "jelly" to 0.1f, "squid" to 0.2f, "angler" to 0.11f, "wreck" to 0.28f, "whale" to 0.26f, "vent" to 0.17f, "tube" to 0.11f, "chest" to 0.06f)
    private val MID = mapOf("sub" to 0.22f, "manta" to 0.05f, "turtle" to 0.09f, "lantern" to 0.1f, "jelly" to 0.5f, "squid" to 0.06f, "angler" to 0.24f)
    private val BOX = mapOf(
        "manta" to (0.17 to 0.08), "turtle" to (0.13 to 0.07), "coral" to (0.12 to 0.11), "lantern" to (0.07 to 0.05), "jelly" to (0.08 to 0.1), "squid" to (0.2 to 0.09),
        "angler" to (0.12 to 0.08), "wreck" to (0.28 to 0.11), "whale" to (0.26 to 0.09), "vent" to (0.13 to 0.17), "tube" to (0.1 to 0.1), "chest" to (0.07 to 0.05),
    )
    private val YAW = mapOf("sub" to 90f, "manta" to -90f, "turtle" to 90f, "angler" to 90f, "squid" to 0f, "lantern" to -90f, "wreck" to 90f, "whale" to 90f, "chest" to 0f)
    private val BANK = mapOf("manta" to 42f, "squid" to 30f, "turtle" to 28f)
}
