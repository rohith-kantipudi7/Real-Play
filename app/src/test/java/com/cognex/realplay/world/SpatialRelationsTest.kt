package com.cognex.realplay.world

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** JVM unit tests for the pure [SpatialRelations] helpers (Architecture §12, S3). */
class SpatialRelationsTest {

    private fun rect(cx: Float, cy: Float, w: Float, h: Float) =
        NormRect(cx - w / 2, cy - h / 2, cx + w / 2, cy + h / 2)

    @Test
    fun distance_isEuclidean() {
        assertEquals(5f, SpatialRelations.distance(NormPoint(0f, 0f), NormPoint(3f, 4f)), 1e-4f)
    }

    @Test
    fun iou_identicalBoxes_isOne() {
        val a = rect(0.5f, 0.5f, 0.4f, 0.4f)
        assertEquals(1f, SpatialRelations.iou(a, a), 1e-4f)
    }

    @Test
    fun iou_disjointBoxes_isZero() {
        val a = rect(0.2f, 0.2f, 0.2f, 0.2f)
        val b = rect(0.8f, 0.8f, 0.2f, 0.2f)
        assertEquals(0f, SpatialRelations.iou(a, b), 1e-4f)
    }

    @Test
    fun iou_halfOverlap() {
        // Two 0.2×0.2 boxes offset by 0.1 in x → intersection 0.1×0.2, union 0.06.
        val a = rect(0.3f, 0.5f, 0.2f, 0.2f)
        val b = rect(0.4f, 0.5f, 0.2f, 0.2f)
        val inter = 0.1f * 0.2f
        val union = 0.04f + 0.04f - inter
        assertEquals(inter / union, SpatialRelations.iou(a, b), 1e-4f)
    }

    @Test
    fun overlapRatio_smallInsideLarge_isOne() {
        val big = rect(0.5f, 0.5f, 0.6f, 0.6f)
        val small = rect(0.5f, 0.5f, 0.1f, 0.1f)
        assertEquals(1f, SpatialRelations.overlapRatio(big, small), 1e-4f)
    }

    @Test
    fun directionRelations() {
        val a = rect(0.2f, 0.2f, 0.1f, 0.1f)
        val b = rect(0.8f, 0.8f, 0.1f, 0.1f)
        assertTrue(SpatialRelations.leftOf(a, b))
        assertTrue(SpatialRelations.rightOf(b, a))
        assertTrue(SpatialRelations.above(a, b))
        assertTrue(SpatialRelations.below(b, a))
        assertFalse(SpatialRelations.leftOf(b, a))
    }

    @Test
    fun pointInPolygon_squareContainsCentre() {
        val square = listOf(
            NormPoint(0.2f, 0.2f), NormPoint(0.8f, 0.2f),
            NormPoint(0.8f, 0.8f), NormPoint(0.2f, 0.8f)
        )
        assertTrue(SpatialRelations.pointInPolygon(NormPoint(0.5f, 0.5f), square))
        assertFalse(SpatialRelations.pointInPolygon(NormPoint(0.05f, 0.05f), square))
    }

    @Test
    fun pointInPolygon_degenerate_isFalse() {
        assertFalse(SpatialRelations.pointInPolygon(NormPoint(0.5f, 0.5f), listOf(NormPoint(0f, 0f))))
    }
}
