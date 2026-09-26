package com.cognex.realplay.world

import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Pure geometric relations over NORMALIZED analysis-image coordinates (Architecture §12, S3).
 * No Android, no state — every function is a total function of its inputs, so the whole file is
 * unit-tested on the JVM. All "left/right/above/below" use image convention: x grows right,
 * y grows DOWN (so "above" means a smaller y).
 */
object SpatialRelations {

    /** Euclidean distance between two normalized points. */
    fun distance(a: NormPoint, b: NormPoint): Float {
        val dx = a.x - b.x
        val dy = a.y - b.y
        return sqrt(dx * dx + dy * dy)
    }

    /** Intersection-over-union of two boxes (0..1). Used by the tracker and by verifiers. */
    fun iou(a: NormRect, b: NormRect): Float {
        val inter = intersectionArea(a, b)
        if (inter <= 0f) return 0f
        val union = a.area + b.area - inter
        return if (union <= 0f) 0f else inter / union
    }

    /**
     * Overlap of two boxes as a fraction of the SMALLER box's area (0..1). This answers
     * "is A mostly inside B (or vice-versa)" better than IoU when the boxes differ in size.
     */
    fun overlapRatio(a: NormRect, b: NormRect): Float {
        val inter = intersectionArea(a, b)
        if (inter <= 0f) return 0f
        val smaller = min(a.area, b.area)
        return if (smaller <= 0f) 0f else (inter / smaller).coerceAtMost(1f)
    }

    /** True when A's centre is left of B's centre. */
    fun leftOf(a: NormRect, b: NormRect): Boolean = a.center.x < b.center.x

    /** True when A's centre is right of B's centre. */
    fun rightOf(a: NormRect, b: NormRect): Boolean = a.center.x > b.center.x

    /** True when A's centre is above B's centre (smaller y, image convention). */
    fun above(a: NormRect, b: NormRect): Boolean = a.center.y < b.center.y

    /** True when A's centre is below B's centre (larger y, image convention). */
    fun below(a: NormRect, b: NormRect): Boolean = a.center.y > b.center.y

    /**
     * Ray-casting point-in-polygon test. [polygon] is an ordered list of vertices (open or closed);
     * the edge case of a point exactly on an edge is treated as inside for our tolerance-free use.
     * Fewer than 3 vertices → always false.
     */
    fun pointInPolygon(point: NormPoint, polygon: List<NormPoint>): Boolean {
        if (polygon.size < 3) return false
        var inside = false
        var j = polygon.size - 1
        for (i in polygon.indices) {
            val pi = polygon[i]
            val pj = polygon[j]
            val intersects = (pi.y > point.y) != (pj.y > point.y) &&
                point.x < (pj.x - pi.x) * (point.y - pi.y) / (pj.y - pi.y) + pi.x
            if (intersects) inside = !inside
            j = i
        }
        return inside
    }

    private fun intersectionArea(a: NormRect, b: NormRect): Float {
        val ix0 = max(a.left, b.left)
        val iy0 = max(a.top, b.top)
        val ix1 = min(a.right, b.right)
        val iy1 = min(a.bottom, b.bottom)
        val iw = (ix1 - ix0).coerceAtLeast(0f)
        val ih = (iy1 - iy0).coerceAtLeast(0f)
        return iw * ih
    }
}
