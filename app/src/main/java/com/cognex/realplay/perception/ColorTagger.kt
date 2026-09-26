package com.cognex.realplay.perception

import android.graphics.Bitmap
import com.cognex.realplay.world.ColorTag
import kotlin.math.max
import kotlin.math.min

/**
 * Classifies the dominant colour of an image region into a [ColorTag] (Architecture §S2).
 *
 * The classification core ([rgbToHsv], [classify], [dominant]) is pure — no Android — so it is
 * fully unit-tested on the JVM. [sampleCentral] is the thin Android wrapper that pulls pixels
 * from a [Bitmap].
 *
 * Algorithm: sample the central 40% of the box (≤200 pixels), convert each RGB→HSV, split
 * achromatic (white/gray/black) from chromatic by saturation/value, bucket chromatic pixels by
 * hue, then take the most common tag — returning UNKNOWN if no tag reaches 45% dominance.
 */
object ColorTagger {

    private const val MAX_SAMPLES = 200
    private const val CENTRAL_FRACTION = 0.40f
    private const val DOMINANCE_THRESHOLD = 0.45f

    // Achromatic split thresholds (HSV, s and v in 0..1).
    private const val BLACK_MAX_VALUE = 0.20f
    private const val ACHROMATIC_MAX_SAT = 0.15f
    private const val WHITE_MIN_VALUE = 0.75f

    /** Converts 8-bit RGB to HSV. Returns (h in 0..360, s in 0..1, v in 0..1). */
    fun rgbToHsv(r: Int, g: Int, b: Int): Triple<Float, Float, Float> {
        val rf = r / 255f
        val gf = g / 255f
        val bf = b / 255f
        val cMax = max(rf, max(gf, bf))
        val cMin = min(rf, min(gf, bf))
        val delta = cMax - cMin

        val h = when {
            delta == 0f -> 0f
            cMax == rf -> 60f * (((gf - bf) / delta) % 6f)
            cMax == gf -> 60f * (((bf - rf) / delta) + 2f)
            else -> 60f * (((rf - gf) / delta) + 4f)
        }.let { if (it < 0f) it + 360f else it }

        val s = if (cMax == 0f) 0f else delta / cMax
        return Triple(h, s, cMax)
    }

    /** Classifies a single RGB pixel into a [ColorTag]. */
    fun classify(r: Int, g: Int, b: Int): ColorTag {
        val (h, s, v) = rgbToHsv(r, g, b)
        if (v < BLACK_MAX_VALUE) return ColorTag.BLACK
        if (s < ACHROMATIC_MAX_SAT) return if (v >= WHITE_MIN_VALUE) ColorTag.WHITE else ColorTag.GRAY
        return when {
            h < 15f || h >= 345f -> ColorTag.RED
            h < 45f -> ColorTag.ORANGE
            h < 70f -> ColorTag.YELLOW
            h < 170f -> ColorTag.GREEN
            h < 200f -> ColorTag.CYAN
            h < 255f -> ColorTag.BLUE
            h < 290f -> ColorTag.PURPLE
            else -> ColorTag.PINK
        }
    }

    /**
     * Returns the dominant [ColorTag] across ARGB [pixels], or UNKNOWN if no tag reaches 45%.
     * Empty input → UNKNOWN.
     */
    fun dominant(pixels: IntArray): ColorTag {
        if (pixels.isEmpty()) return ColorTag.UNKNOWN
        val counts = IntArray(ColorTag.entries.size)
        for (p in pixels) {
            val r = (p shr 16) and 0xFF
            val g = (p shr 8) and 0xFF
            val b = p and 0xFF
            counts[classify(r, g, b).ordinal]++
        }
        var bestOrdinal = 0
        var bestCount = 0
        for (i in counts.indices) {
            if (counts[i] > bestCount) {
                bestCount = counts[i]
                bestOrdinal = i
            }
        }
        return if (bestCount.toFloat() / pixels.size < DOMINANCE_THRESHOLD) {
            ColorTag.UNKNOWN
        } else {
            ColorTag.entries[bestOrdinal]
        }
    }

    /**
     * Samples the central 40% of the given NORMALIZED box from [bitmap] (≤200 pixels) and returns
     * the dominant [ColorTag]. Best-effort — colour is never on the truth path (§6).
     */
    fun sampleCentral(
        bitmap: Bitmap,
        normLeft: Float,
        normTop: Float,
        normRight: Float,
        normBottom: Float
    ): ColorTag {
        val w = bitmap.width
        val h = bitmap.height
        if (w <= 0 || h <= 0) return ColorTag.UNKNOWN

        // Shrink the box to its central 40% around the centre.
        val cx = (normLeft + normRight) / 2f
        val cy = (normTop + normBottom) / 2f
        val halfW = (normRight - normLeft) * CENTRAL_FRACTION / 2f
        val halfH = (normBottom - normTop) * CENTRAL_FRACTION / 2f

        val x0 = ((cx - halfW) * w).toInt().coerceIn(0, w - 1)
        val x1 = ((cx + halfW) * w).toInt().coerceIn(0, w - 1)
        val y0 = ((cy - halfH) * h).toInt().coerceIn(0, h - 1)
        val y1 = ((cy + halfH) * h).toInt().coerceIn(0, h - 1)
        if (x1 <= x0 || y1 <= y0) return ColorTag.UNKNOWN

        val regionW = x1 - x0
        val regionH = y1 - y0
        // Choose a stride so we read at most MAX_SAMPLES pixels.
        val total = regionW * regionH
        val step = max(1, Math.round(Math.sqrt(total.toDouble() / MAX_SAMPLES)).toInt())

        val samples = ArrayList<Int>(MAX_SAMPLES)
        var y = y0
        while (y < y1 && samples.size < MAX_SAMPLES) {
            var x = x0
            while (x < x1 && samples.size < MAX_SAMPLES) {
                samples.add(bitmap.getPixel(x, y))
                x += step
            }
            y += step
        }
        return dominant(samples.toIntArray())
    }
}
