package com.cognex.realplay.perception.zone

import com.cognex.realplay.world.ColorTag
import com.cognex.realplay.world.NormPoint
import kotlin.math.max
import kotlin.math.min

/**
 * A raw, single-frame colour region — a candidate zone before temporal confirmation (S7). Pure JVM.
 * [polygon] is an ordered quad (TL, TR, BR, BL) in NORMALIZED image space; [areaFrac] is the region
 * pixel count as a fraction of the whole frame.
 */
data class RawZone(
    val color: ColorTag,
    val polygon: List<NormPoint>,
    val centroid: NormPoint,
    val areaFrac: Float
)

/**
 * Detects strong single-colour regions on a downscaled ARGB grid (Architecture §13 S7). Pure JVM —
 * no Android, no OpenCV — so the whole detector is unit-testable on the JVM.
 *
 * Algorithm (per §S7 prompt):
 *   1. classify every pixel to a *saturated* [ColorTag] with saturation/value minimums so shadows
 *      and washed-out background are rejected (achromatic pixels are ignored entirely);
 *   2. 4-connected connected-components per colour;
 *   3. keep components whose area is between [MIN_AREA_FRAC] and [MAX_AREA_FRAC] of the frame;
 *   4. approximate each to a quad from its extreme points;
 *   5. emit a NORMALIZED [RawZone].
 *
 * Temporal confirmation / survival / EMA smoothing is [ZoneTracker]'s job, not this object's.
 */
object ZoneRegions {

    const val MIN_AREA_FRAC = 0.015f
    const val MAX_AREA_FRAC = 0.40f

    // Reject shadows (low value) and washed-out / near-grey pixels (low saturation).
    private const val MIN_SAT = 0.35f
    private const val MIN_VAL = 0.30f

    /**
     * Detects candidate zones on an ARGB [pixels] grid of size [w]×[h]. Returns at most a handful of
     * regions, ordered largest-first.
     */
    fun detect(pixels: IntArray, w: Int, h: Int): List<RawZone> {
        if (w <= 0 || h <= 0 || pixels.size < w * h) return emptyList()
        val total = w * h
        val minPx = (MIN_AREA_FRAC * total).toInt().coerceAtLeast(1)
        val maxPx = (MAX_AREA_FRAC * total).toInt()

        // Pass 1: per-pixel saturated colour label (-1 = ignore).
        val label = IntArray(total) { i ->
            val p = pixels[i]
            zoneColorOrdinal((p shr 16) and 0xFF, (p shr 8) and 0xFF, p and 0xFF)
        }

        val visited = BooleanArray(total)
        val stack = IntArray(total)
        val out = ArrayList<RawZone>()

        for (start in 0 until total) {
            if (visited[start]) continue
            val color = label[start]
            if (color < 0) { visited[start] = true; continue }

            // Iterative 4-connected flood fill of the same colour.
            var sp = 0
            stack[sp++] = start
            visited[start] = true
            var count = 0
            var sumX = 0L
            var sumY = 0L
            var minX = w; var maxX = -1; var minY = h; var maxY = -1
            // Extreme carriers for the quad corners.
            var tlIdx = start; var brIdx = start; var trIdx = start; var blIdx = start
            var tlKey = Int.MAX_VALUE; var brKey = Int.MIN_VALUE
            var trKey = Int.MIN_VALUE; var blKey = Int.MAX_VALUE

            while (sp > 0) {
                val idx = stack[--sp]
                val x = idx % w
                val y = idx / w
                count++
                sumX += x; sumY += y
                if (x < minX) minX = x; if (x > maxX) maxX = x
                if (y < minY) minY = y; if (y > maxY) maxY = y
                val sum = x + y
                val diff = x - y
                if (sum < tlKey) { tlKey = sum; tlIdx = idx }
                if (sum > brKey) { brKey = sum; brIdx = idx }
                if (diff > trKey) { trKey = diff; trIdx = idx }
                if (diff < blKey) { blKey = diff; blIdx = idx }

                // Neighbours (4-connected). Inline pushes keep the fill allocation-free.
                if (x > 0) { val n = idx - 1; if (!visited[n] && label[n] == color) { visited[n] = true; stack[sp++] = n } }
                if (x < w - 1) { val n = idx + 1; if (!visited[n] && label[n] == color) { visited[n] = true; stack[sp++] = n } }
                if (y > 0) { val n = idx - w; if (!visited[n] && label[n] == color) { visited[n] = true; stack[sp++] = n } }
                if (y < h - 1) { val n = idx + w; if (!visited[n] && label[n] == color) { visited[n] = true; stack[sp++] = n } }
            }

            if (count < minPx || count > maxPx) continue

            fun norm(idx: Int) = NormPoint((idx % w) / w.toFloat(), (idx / w) / h.toFloat())
            val polygon = listOf(norm(tlIdx), norm(trIdx), norm(brIdx), norm(blIdx))
            val centroid = NormPoint((sumX.toFloat() / count) / w, (sumY.toFloat() / count) / h)
            out.add(RawZone(ColorTag.entries[color], polygon, centroid, count.toFloat() / total))
        }

        return out.sortedByDescending { it.areaFrac }
    }

    /**
     * Maps an RGB pixel to a saturated zone-colour ordinal, or -1 when it is achromatic / too dark /
     * too washed-out to be part of a taped or coloured region. Hue buckets match [ColorTagger].
     */
    fun zoneColorOrdinal(r: Int, g: Int, b: Int): Int {
        val rf = r / 255f; val gf = g / 255f; val bf = b / 255f
        val cMax = max(rf, max(gf, bf))
        val cMin = min(rf, min(gf, bf))
        val delta = cMax - cMin
        val v = cMax
        val s = if (cMax == 0f) 0f else delta / cMax
        if (v < MIN_VAL || s < MIN_SAT) return -1
        var h = when {
            delta == 0f -> 0f
            cMax == rf -> 60f * (((gf - bf) / delta) % 6f)
            cMax == gf -> 60f * (((bf - rf) / delta) + 2f)
            else -> 60f * (((rf - gf) / delta) + 4f)
        }
        if (h < 0f) h += 360f
        return when {
            h < 15f || h >= 345f -> ColorTag.RED.ordinal
            h < 45f -> ColorTag.ORANGE.ordinal
            h < 70f -> ColorTag.YELLOW.ordinal
            h < 170f -> ColorTag.GREEN.ordinal
            h < 200f -> ColorTag.CYAN.ordinal
            h < 255f -> ColorTag.BLUE.ordinal
            h < 290f -> ColorTag.PURPLE.ordinal
            else -> ColorTag.PINK.ordinal
        }
    }
}
