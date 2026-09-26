package com.cognex.realplay.world

/** Where a [Zone] came from (Architecture §4). Pure JVM. */
enum class ZoneSource {
    /** Derived from a detected object's footprint (e.g. a box, a mat). */
    DETECTED,

    /** Established during the calibration pass (the framing rectangle / surface). */
    CALIBRATION,

    /** Computed by the world model (e.g. a grid cell or a derived region). */
    DERIVED
}

/**
 * A named region of the scene in NORMALIZED analysis-image space (Architecture §4). Pure JVM.
 * (Zones are produced by the zone detector in a later stage; declared here so [WorldState] can
 * compile and so verifiers can reference zones.)
 */
data class Zone(
    val zoneId: String,
    val polygon: List<NormPoint>,
    val color: ColorTag,
    val source: ZoneSource
)
