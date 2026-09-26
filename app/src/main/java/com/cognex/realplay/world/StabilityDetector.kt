package com.cognex.realplay.world

/**
 * Decides which tracks are "settled" (Architecture §12, S3). Pure JVM — no Android.
 *
 * A track is stable when, over the last [windowMs] of history, its centre moved less than
 * [moveThreshold] (normalized). Stability is reported per-object; [isSceneStable] is the AND over
 * all confirmed objects. The detector needs at least [windowMs] of observation before it will call
 * anything stable (so a freshly-appeared object is never instantly "stable").
 */
class StabilityDetector(
    private val windowMs: Long = 500L,
    private val moveThreshold: Float = 0.02f
) {
    private val history = HashMap<Int, ArrayDeque<Sample>>()

    private data class Sample(val timeMs: Long, val center: NormPoint)

    /**
     * Records the current centres and returns the set of trackIds that are currently stable.
     * Tracks not present in [objects] have their history dropped.
     */
    fun update(objects: List<TrackedObject>, nowMs: Long): Set<Int> {
        val liveIds = objects.mapTo(HashSet()) { it.trackId }
        history.keys.retainAll(liveIds)

        val stable = HashSet<Int>()
        for (obj in objects) {
            val dq = history.getOrPut(obj.trackId) { ArrayDeque() }
            dq.addLast(Sample(nowMs, obj.center))
            // Keep a little more than the window so we can prove ≥ windowMs of coverage.
            val keepFrom = nowMs - windowMs * 2
            while (dq.isNotEmpty() && dq.first().timeMs < keepFrom) dq.removeFirst()

            val coverage = nowMs - dq.first().timeMs
            if (coverage >= windowMs) {
                val newest = dq.last().center
                val maxDisp = dq.asSequence()
                    .filter { nowMs - it.timeMs <= windowMs }
                    .maxOf { SpatialRelations.distance(it.center, newest) }
                if (maxDisp < moveThreshold) stable.add(obj.trackId)
            }
        }
        return stable
    }

    /** True when there is at least one confirmed object and every one of them is stable. */
    fun isSceneStable(objects: List<TrackedObject>): Boolean =
        objects.isNotEmpty() && objects.all { it.stable }

    /** Forgets all history (e.g. when the camera session restarts). */
    fun reset() = history.clear()
}
