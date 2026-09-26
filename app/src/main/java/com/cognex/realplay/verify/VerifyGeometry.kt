package com.cognex.realplay.verify

import com.cognex.realplay.world.NormPoint
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.sqrt

/**
 * Result of analysing a triangle formed by three points (Architecture §4.1 NON_DEGENERATE_TRIANGLE).
 */
data class TriangleMetrics(
    val area: Float,
    val minAngleDeg: Float,
    val sideRatio: Float   // longest / shortest
)

/**
 * Pure multi-object geometry for the verifiers (Architecture §S4). All inputs are NORMALIZED
 * analysis-image points; the triangle is 2D camera-space only unless a planar surface is
 * calibrated (§7.1).
 */
object VerifyGeometry {

    /** Signed cross-product area helper → absolute triangle area. */
    fun triangleArea(a: NormPoint, b: NormPoint, c: NormPoint): Float =
        abs((b.x - a.x) * (c.y - a.y) - (c.x - a.x) * (b.y - a.y)) / 2f

    /** Full triangle metrics: area, smallest interior angle (degrees), and longest/shortest ratio. */
    fun triangleMetrics(a: NormPoint, b: NormPoint, c: NormPoint): TriangleMetrics {
        val ab = dist(a, b); val bc = dist(b, c); val ca = dist(c, a)
        val area = triangleArea(a, b, c)
        val angA = angleAt(a, b, c)
        val angB = angleAt(b, a, c)
        val angC = angleAt(c, a, b)
        val minAngle = minOf(angA, angB, angC)
        val longest = maxOf(ab, bc, ca)
        val shortest = minOf(ab, bc, ca)
        val ratio = if (shortest <= 0f) Float.MAX_VALUE else longest / shortest
        return TriangleMetrics(area, Math.toDegrees(minAngle.toDouble()).toFloat(), ratio)
    }

    /**
     * Centroid-subtracted, RMS-radius-normalised copy of [points] (Architecture §4.1
     * ARRANGEMENT_MATCH). This makes an arrangement invariant to translation and uniform scale —
     * a 2× larger, shifted copy normalises to the same shape. Returns points in the same order.
     * A degenerate (single point / zero spread) set normalises to all-zero.
     */
    fun normalizeArrangement(points: List<NormPoint>): List<NormPoint> {
        if (points.isEmpty()) return emptyList()
        val cx = points.map { it.x }.average().toFloat()
        val cy = points.map { it.y }.average().toFloat()
        val centered = points.map { NormPoint(it.x - cx, it.y - cy) }
        val rms = sqrt(centered.map { it.x * it.x + it.y * it.y }.average().toFloat())
        if (rms <= 0f) return centered.map { NormPoint(0f, 0f) }
        return centered.map { NormPoint(it.x / rms, it.y / rms) }
    }

    private fun dist(a: NormPoint, b: NormPoint) = hypot(b.x - a.x, b.y - a.y)

    /** Interior angle at [vertex] (radians) formed with [p1] and [p2]. */
    private fun angleAt(vertex: NormPoint, p1: NormPoint, p2: NormPoint): Float =
        PoseMath.jointAngle(p1, vertex, p2)
}
