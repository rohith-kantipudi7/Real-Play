package com.cognex.realplay.perception

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** JVM tests for the pure scoring core of [FrameQualityAnalyzer] (Architecture §12, S3). */
class FrameQualityAnalyzerTest {

    private val w = 32
    private val h = 24

    private fun uniform(value: Int) = IntArray(w * h) { value }

    private fun checkerboard(lo: Int, hi: Int) = IntArray(w * h) { i ->
        val x = i % w
        val y = i / w
        if ((x + y) % 2 == 0) hi else lo
    }

    @Test
    fun lensCovered_isDark() {
        val q = FrameQualityAnalyzer.analyze(uniform(0), w, h)
        assertFalse(q.good)
        assertEquals("Move to better light", q.reason)
    }

    @Test
    fun brightButFlat_isBlurry() {
        // Uniform bright frame → zero Laplacian variance → treated as blur/hold-steady.
        val q = FrameQualityAnalyzer.analyze(uniform(200), w, h)
        assertFalse(q.good)
        assertEquals("Hold steady", q.reason)
    }

    @Test
    fun brightAndTextured_isGood() {
        val q = FrameQualityAnalyzer.analyze(checkerboard(50, 250), w, h)
        assertTrue(q.good)
        assertNull(q.reason)
    }

    @Test
    fun tinyGrid_isTreatedAsGood() {
        val q = FrameQualityAnalyzer.analyze(IntArray(4) { 0 }, 2, 2)
        assertTrue(q.good)
    }
}
