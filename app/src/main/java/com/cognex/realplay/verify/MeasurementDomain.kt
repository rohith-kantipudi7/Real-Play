package com.cognex.realplay.verify

/**
 * The measurement domain of a piece of [Evidence] (Architecture §12). Pure JVM.
 *
 * While [com.cognex.realplay.world.SceneCapability.planarSurfaceAvailable] is false, ALL evidence
 * is [NORMALIZED] — we never claim [METRIC] centimetres without a calibrated planar surface, and
 * [PIXEL] is only for debugging. Honest camera-space wording beats a fake centimetre (§12).
 */
enum class MeasurementDomain { NORMALIZED, PIXEL, METRIC }
