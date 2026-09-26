package com.cognex.realplay.verify

import com.cognex.realplay.world.Landmark
import com.cognex.realplay.world.PlayerDynamics
import com.cognex.realplay.world.TrackedPlayer
import kotlin.math.ceil
import kotlin.math.hypot

/**
 * Accumulates session-level [PlayerDynamics] from tracked players (Architecture §6.2). Pure JVM —
 * no Android, fully testable on synthetic frames. Fed one frame at a time via [observe]; the
 * current [dynamics] can be read at any point and is consumed by [com.cognex.realplay.world.richness].
 *
 *  - `poseVariety` — count of distinct pose-feature clusters observed this session
 *    ([PoseMath.poseClusterId]), normalised against [VARIETY_NORM]. Rewards a player who has
 *    actually moved through different shapes.
 *  - `motionRange` — 95th-percentile per-frame landmark displacement over a [windowMs] window,
 *    normalised against [MOTION_NORM]. Distinguishes an engaged player from someone standing still.
 *
 * Ambiguous or low-confidence players are ignored so an unreliable frame never inflates either term.
 */
class PlayerDynamicsTracker(private val windowMs: Long = 3_000L) {

    private val clusters = HashSet<Int>()
    private val prevByPlayer = HashMap<Int, List<Landmark>>()
    private val samples = ArrayDeque<Sample>()

    private data class Sample(val timestampMs: Long, val displacement: Float)

    /** Folds one frame of tracked players into the running dynamics. */
    fun observe(players: List<TrackedPlayer>, timestampMs: Long) {
        for (p in players) {
            if (p.ambiguous || p.confidence < CONFIDENCE_FLOOR) continue
            clusters.add(PoseMath.poseClusterId(PoseMath.poseFeatureVector(p.landmarks)))
            prevByPlayer[p.playerId]?.let { prev -> addDisplacements(prev, p.landmarks, timestampMs) }
            prevByPlayer[p.playerId] = p.landmarks
        }
        val cutoff = timestampMs - windowMs
        while (samples.isNotEmpty() && samples.first().timestampMs < cutoff) samples.removeFirst()
    }

    /** Records each visible landmark's frame-to-frame displacement as its own sample so a single
     *  fast limb registers as real motion (the 95th percentile rewards the quickest body parts). */
    private fun addDisplacements(previous: List<Landmark>, current: List<Landmark>, timestampMs: Long) {
        val n = minOf(previous.size, current.size)
        for (i in 0 until n) {
            val w = minOf(previous[i].visibility, current[i].visibility)
            if (w <= 0f) continue
            val d = hypot(current[i].point.x - previous[i].point.x, current[i].point.y - previous[i].point.y)
            samples.addLast(Sample(timestampMs, d))
        }
    }

    /** The current session dynamics. Both terms are clamped to 0..1. */
    fun dynamics(): PlayerDynamics = PlayerDynamics(
        poseVariety = (clusters.size / VARIETY_NORM).coerceIn(0f, 1f),
        motionRange = (percentile95() / MOTION_NORM).coerceIn(0f, 1f)
    )

    /** Clears all accumulated state — call at the start of each session (§5). */
    fun reset() {
        clusters.clear()
        prevByPlayer.clear()
        samples.clear()
    }

    private fun percentile95(): Float {
        if (samples.isEmpty()) return 0f
        val sorted = samples.map { it.displacement }.sorted()
        val idx = ceil(0.95f * (sorted.size - 1)).toInt().coerceIn(0, sorted.size - 1)
        return sorted[idx]
    }

    companion object {
        /** Distinct pose clusters that map `poseVariety` to 1.0. */
        const val VARIETY_NORM = 5f

        /** Per-frame displacement (normalised units) that maps `motionRange` to 1.0. */
        const val MOTION_NORM = 0.12f

        /** Players below this detector confidence are ignored. */
        const val CONFIDENCE_FLOOR = 0.5f
    }
}
