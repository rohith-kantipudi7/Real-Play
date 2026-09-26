package com.cognex.realplay.verify

import com.cognex.realplay.world.NormPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pure multi-object geometry tests (Architecture §4.1). */
class VerifyGeometryTest {

    @Test
    fun triangleArea_collinearPoints_isZero() {
        val a = NormPoint(0.1f, 0.1f); val b = NormPoint(0.3f, 0.3f); val c = NormPoint(0.5f, 0.5f)
        assertEquals(0f, VerifyGeometry.triangleArea(a, b, c), 1e-6f)
    }

    @Test
    fun triangleMetrics_collinear_hasTinyMinAngle() {
        val m = VerifyGeometry.triangleMetrics(NormPoint(0.1f, 0.1f), NormPoint(0.3f, 0.3f), NormPoint(0.5f, 0.5f))
        assertEquals(0f, m.area, 1e-6f)
        assertTrue("collinear min angle should be ~0°", m.minAngleDeg < 1f)
    }

    @Test
    fun triangleMetrics_equilateral_hasSixtyDegreeMinAngle() {
        val m = VerifyGeometry.triangleMetrics(
            NormPoint(0.5f, 0.1f),
            NormPoint(0.1f, 0.8f),
            NormPoint(0.9f, 0.8f)
        )
        assertTrue(m.minAngleDeg > 45f)
        assertTrue(m.area > 0f)
    }

    @Test
    fun normalizeArrangement_invariantToScaleAndTranslation() {
        val shape = listOf(NormPoint(0.4f, 0.4f), NormPoint(0.6f, 0.4f), NormPoint(0.5f, 0.6f))
        val scaledShifted = shape.map { NormPoint(it.x * 2f + 0.1f, it.y * 2f + 0.05f) }
        val a = VerifyGeometry.normalizeArrangement(shape)
        val b = VerifyGeometry.normalizeArrangement(scaledShifted)
        for (i in a.indices) {
            assertEquals(a[i].x, b[i].x, 1e-4f)
            assertEquals(a[i].y, b[i].y, 1e-4f)
        }
    }

    @Test
    fun normalizeArrangement_degenerate_isAllZero() {
        val single = listOf(NormPoint(0.5f, 0.5f))
        val n = VerifyGeometry.normalizeArrangement(single)
        assertEquals(0f, n[0].x, 0f)
        assertEquals(0f, n[0].y, 0f)
    }
}
