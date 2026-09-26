package com.cognex.realplay.challenge

/**
 * Toddler-only knob overrides (Architecture §10, §13 S10). Applied AFTER the generic scene-scaled
 * knobs ([DifficultyKnobs.forTier]) so every other band's scaling is untouched. Pure JVM.
 *
 *   holdMs             ×= 1.7, then capped at [MAX_HOLD_MS] (§10 rule 5 — the stability multiplier
 *                          can otherwise compound past what a toddler can hold)
 *   distanceThreshold, poseToleranceDeg ×= 2.0 (§10 rule 5 — wider tolerances)
 *   timeLimitMs         forced to null (§10 rule 1 — no timers, no clock, ever)
 */
object ToddlerPolicy {
    private const val HOLD_MULTIPLIER = 1.7f
    private const val TOLERANCE_MULTIPLIER = 2.0f
    const val MAX_HOLD_MS = 2_000L

    /** Returns [knobs] unchanged for every band except TODDLER. */
    fun apply(knobs: DifficultyKnobs, ageBand: AgeBand): DifficultyKnobs {
        if (ageBand != AgeBand.TODDLER) return knobs
        return knobs.copy(
            distanceThreshold = knobs.distanceThreshold * TOLERANCE_MULTIPLIER,
            holdMs = (knobs.holdMs * HOLD_MULTIPLIER).toLong().coerceAtMost(MAX_HOLD_MS),
            timeLimitMs = null,
            poseToleranceDeg = knobs.poseToleranceDeg * TOLERANCE_MULTIPLIER
        )
    }
}
