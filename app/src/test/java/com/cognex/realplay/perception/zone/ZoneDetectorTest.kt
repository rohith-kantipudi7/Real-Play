package com.cognex.realplay.perception.zone

import com.cognex.realplay.world.ColorTag
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pure-JVM zone detection + temporal confirmation (Architecture §13 S7). */
class ZoneDetectorTest {

    private val w = 160
    private val h = 120

    /** Builds an ARGB grid with a filled rectangle of [argb] over [bg]. */
    private fun grid(
        bg: Int = 0xFF202020.toInt(),
        argb: Int,
        x0: Int, y0: Int, x1: Int, y1: Int
    ): IntArray {
        val px = IntArray(w * h) { bg }
        for (y in y0 until y1) for (x in x0 until x1) px[y * w + x] = argb
        return px
    }

    private val red = 0xFFE03030.toInt()
    private val green = 0xFF30C030.toInt()

    // ── ZoneRegions ────────────────────────────────────────────────────────────

    @Test
    fun detect_findsSaturatedRedSquare_asOneQuad() {
        // ~28×28 over 160×120 = 0.041 area fraction → inside [1.5%, 40%].
        val px = grid(argb = red, x0 = 40, y0 = 30, x1 = 68, y1 = 58)
        val zones = ZoneRegions.detect(px, w, h)
        assertEquals(1, zones.size)
        assertEquals(ColorTag.RED, zones.first().color)
        assertEquals(4, zones.first().polygon.size)
        assertTrue(zones.first().areaFrac in 0.015f..0.40f)
        // Centroid near the square's centre (~0.34, ~0.37 normalized).
        assertEquals(0.337f, zones.first().centroid.x, 0.03f)
    }

    @Test
    fun detect_rejectsTooSmallRegion() {
        val px = grid(argb = red, x0 = 40, y0 = 30, x1 = 44, y1 = 34)   // 4×4 ≈ 0.08%
        assertTrue(ZoneRegions.detect(px, w, h).isEmpty())
    }

    @Test
    fun detect_ignoresAchromaticAndShadow() {
        // A dark, low-saturation block must not become a zone.
        val px = grid(argb = 0xFF303030.toInt(), x0 = 40, y0 = 30, x1 = 80, y1 = 70)
        assertTrue(ZoneRegions.detect(px, w, h).isEmpty())
    }

    @Test
    fun zoneColorOrdinal_rejectsShadowAndLowSaturation() {
        assertEquals(-1, ZoneRegions.zoneColorOrdinal(20, 20, 20))     // too dark
        assertEquals(-1, ZoneRegions.zoneColorOrdinal(180, 175, 178))  // near-grey
        assertEquals(ColorTag.RED.ordinal, ZoneRegions.zoneColorOrdinal(220, 40, 40))
    }

    // ── ZoneTracker ────────────────────────────────────────────────────────────

    private fun redSquare() = ZoneRegions.detect(
        grid(argb = red, x0 = 40, y0 = 30, x1 = 68, y1 = 58), w, h
    )

    @Test
    fun tracker_confirmsAfterFiveConsecutiveFrames() {
        val t = ZoneTracker()
        val raw = redSquare()
        repeat(4) { assertTrue(t.update(raw).isEmpty()) }   // frames 1..4 → not yet confirmed
        val confirmed = t.update(raw)                        // frame 5 → confirmed
        assertEquals(1, confirmed.size)
        assertEquals(ColorTag.RED, confirmed.first().color)
    }

    @Test
    fun tracker_survivesBriefAbsenceThenRetires() {
        val t = ZoneTracker()
        val raw = redSquare()
        repeat(5) { t.update(raw) }                          // confirmed
        assertEquals(1, t.update(emptyList()).size)          // one missed frame → still present
        repeat(ZoneTracker.SURVIVE_FRAMES) { t.update(emptyList()) }
        assertTrue(t.update(emptyList()).isEmpty())          // gone after > SURVIVE_FRAMES absent
    }

    @Test
    fun tracker_keepsAStableZoneId() {
        val t = ZoneTracker()
        val raw = redSquare()
        repeat(5) { t.update(raw) }
        val first = t.update(raw).first().zoneId
        val second = t.update(raw).first().zoneId
        assertEquals(first, second)
    }
}
