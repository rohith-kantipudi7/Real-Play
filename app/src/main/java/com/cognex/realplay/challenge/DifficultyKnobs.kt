package com.cognex.realplay.challenge

/**
 * The scene-scaled numeric difficulty knobs (Architecture §8.1 — Axis 1). Pure JVM.
 *
 * Base values come from the tier; two scene multipliers then adjust them:
 *   distanceThreshold ×= (1 + 0.3·(1 − spread))   — a clustered scene is more forgiving
 *   holdMs            ×= (1 + 0.4·(1 − stability)) — a jittery scene demands a longer hold
 *
 * The toddler cap on effective hold (≤ 2000 ms, §10) is applied later by ToddlerPolicy (S10), not
 * here — these are the generic per-tier knobs.
 */
data class DifficultyKnobs(
    val distanceThreshold: Float,
    val holdMs: Long,
    val timeLimitMs: Long?,
    val poseToleranceDeg: Float
) {
    companion object {
        /**
         * Builds the knobs for [tier], scaled by scene [spread] (0..1) and [stability] (0..1).
         */
        fun forTier(tier: Tier, spread: Float, stability: Float): DifficultyKnobs {
            val s = spread.coerceIn(0f, 1f)
            val st = stability.coerceIn(0f, 1f)
            val baseDistance: Float
            val baseHold: Long
            val timeLimit: Long?
            val poseTol: Float
            when (tier) {
                Tier.EASY -> { baseDistance = 0.30f; baseHold = 500L; timeLimit = null; poseTol = 30f }
                Tier.MEDIUM -> { baseDistance = 0.20f; baseHold = 1_000L; timeLimit = 30_000L; poseTol = 20f }
                Tier.HARD -> { baseDistance = 0.12f; baseHold = 2_000L; timeLimit = 20_000L; poseTol = 12f }
            }
            val distance = baseDistance * (1f + 0.3f * (1f - s))
            val hold = (baseHold * (1f + 0.4f * (1f - st))).toLong()
            return DifficultyKnobs(distance, hold, timeLimit, poseTol)
        }
    }
}
