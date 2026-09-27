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
 * Result of fitting a straight line to a point set (Architecture §4.1 COLLINEAR). [maxPerpendicular]
 * is the worst point-to-line distance; [spread] is the extent ALONG the line (its length). A real
 * row has a small [maxPerpendicular] and a large [spread]; a tight blob has a small [spread].
 */
data class LineFit(val maxPerpendicular: Float, val spread: Float)

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

    /**
     * Total-least-squares (PCA) line fit of [points] (Architecture §4.1 COLLINEAR). Returns the
     * worst perpendicular distance to the best-fit line and the spread ALONG it. Rotation-invariant
     * — a straight row at any angle fits well; a blob has a small along-line spread. Fewer than two
     * points fit trivially (zero deviation, zero spread).
     */
    fun lineFit(points: List<NormPoint>): LineFit {
        if (points.size < 2) return LineFit(0f, 0f)
        val n = points.size.toFloat()
        val cx = points.map { it.x }.average().toFloat()
        val cy = points.map { it.y }.average().toFloat()
        var sxx = 0f; var syy = 0f; var sxy = 0f
        for (p in points) {
            val dx = p.x - cx; val dy = p.y - cy
            sxx += dx * dx; syy += dy * dy; sxy += dx * dy
        }
        sxx /= n; syy /= n; sxy /= n
        // Principal axis = eigenvector of the larger eigenvalue of the 2×2 covariance matrix.
        val half = (sxx - syy) / 2f
        val lambda1 = (sxx + syy) / 2f + sqrt(half * half + sxy * sxy)
        var vx: Float; var vy: Float
        if (abs(sxy) > 1e-6f) { vx = lambda1 - syy; vy = sxy }
        else if (sxx >= syy) { vx = 1f; vy = 0f } else { vx = 0f; vy = 1f }
        val len = hypot(vx, vy)
        if (len > 0f) { vx /= len; vy /= len }
        // Unit normal to the line — perpendicular deviation is the projection onto it.
        val nx = -vy; val ny = vx
        var maxPerp = 0f; var minProj = Float.MAX_VALUE; var maxProj = -Float.MAX_VALUE
        for (p in points) {
            val dx = p.x - cx; val dy = p.y - cy
            val perp = abs(dx * nx + dy * ny)
            if (perp > maxPerp) maxPerp = perp
            val proj = dx * vx + dy * vy
            if (proj < minProj) minProj = proj
            if (proj > maxProj) maxProj = proj
        }
        return LineFit(maxPerp, maxProj - minProj)
    }

    private fun dist(a: NormPoint, b: NormPoint) = hypot(b.x - a.x, b.y - a.y)

    /** Interior angle at [vertex] (radians) formed with [p1] and [p2]. */
    private fun angleAt(vertex: NormPoint, p1: NormPoint, p2: NormPoint): Float =
        PoseMath.jointAngle(p1, vertex, p2)
}
