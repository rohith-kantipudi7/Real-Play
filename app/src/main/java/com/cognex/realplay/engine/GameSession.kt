package com.cognex.realplay.engine

import com.cognex.realplay.challenge.ChallengeType
import com.cognex.realplay.challenge.Tier

/**
 * Mutable per-session progression state (Architecture §8, §9). Pure JVM.
 *
 * Holds the skill [rating] (0–100), the pass [streak], accumulated [totalScore], the recent
 * challenge types (for the registry novelty bonus) and the previous tier (for skill-tier
 * hysteresis). Rating deltas follow §8: two consecutive passes weigh more, a fast pass adds a
 * little, two consecutive fails cost more — and **Unsure never changes difficulty** (perception
 * noise must not move the rating, §8 / §20 invariant 2).
 */
class GameSession(startRating: Float = 40f) {

    var rating: Float = startRating.coerceIn(0f, 100f)
        private set
    var streak: Int = 0
        private set
    var totalScore: Int = 0
        private set
    var previousTier: Tier? = null

    private var consecutivePasses: Int = 0
    private var consecutiveFails: Int = 0

    private val recent = ArrayDeque<ChallengeType>()
    val recentTypes: List<ChallengeType> get() = recent.toList()

    fun recordPass(fast: Boolean) {
        consecutivePasses++
        consecutiveFails = 0
        streak++
        var delta = if (consecutivePasses >= 2) 12f else 6f
        if (fast) delta += 6f
        rating = (rating + delta).coerceIn(0f, 100f)
    }

    fun recordFail() {
        consecutiveFails++
        consecutivePasses = 0
        streak = 0
        val delta = if (consecutiveFails >= 2) -18f else -10f
        rating = (rating + delta).coerceIn(0f, 100f)
    }

    /** Perception uncertainty — deliberately no rating or streak change (§8). */
    fun recordUnsure() { /* no-op by design */ }

    fun addScore(points: Int) { totalScore += points }

    fun pushType(type: ChallengeType) {
        recent.addLast(type)
        while (recent.size > RECENT_MEMORY) recent.removeFirst()
    }

    private companion object {
        const val RECENT_MEMORY = 3
    }
}
