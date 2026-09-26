package com.cognex.realplay.world

/**
 * A point in NORMALIZED analysis-image space (0..1, top-left origin). Pure JVM — no Android.
 * Every coordinate in world/, challenge/ and verify/ uses this space (Architecture §12); pixels
 * exist only inside [com.cognex.realplay.camera.CoordinateMapper].
 */
data class NormPoint(val x: Float, val y: Float)

/**
 * An axis-aligned rectangle in NORMALIZED analysis-image space (0..1). Pure JVM — no Android.
 */
data class NormRect(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float
) {
    val width: Float get() = right - left
    val height: Float get() = bottom - top

    /** Fraction of the frame area this rectangle occupies (0..1). Drives affordance flags (§6). */
    val area: Float get() = (width.coerceAtLeast(0f)) * (height.coerceAtLeast(0f))

    val center: NormPoint get() = NormPoint((left + right) / 2f, (top + bottom) / 2f)

    companion object {
        /** Builds a rect from a centre + size, clamped to the unit square. */
        fun fromCenter(cx: Float, cy: Float, w: Float, h: Float): NormRect = NormRect(
            left = (cx - w / 2f).coerceIn(0f, 1f),
            top = (cy - h / 2f).coerceIn(0f, 1f),
            right = (cx + w / 2f).coerceIn(0f, 1f),
            bottom = (cy + h / 2f).coerceIn(0f, 1f)
        )
    }
}
