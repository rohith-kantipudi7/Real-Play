package com.cognex.realplay.engine

import kotlin.math.max
import kotlin.math.roundToInt

/**
 * The scoring formula (Architecture §9). Pure JVM — no state, fully unit-tested.
 *
 *   score = ( base
 *           + speedBonus(≤30% of limit → +50, ≤60% → +25)
 *           + precisionBonus(measured ≤ 0.6 × required → +25)
 *           + stepBonus(+40 per completed step beyond the first) )
 *           × streakMultiplier(1.0 / 1.1 / 1.2 …, cap 1.5)
 *           − hintPenalty(10 each, floor 0)
 *
 * `stepBonus` (v3.4) makes a 2-step HARD mission worth meaningfully more, and partial completion
 * still scores. Speed/precision bonuses apply only to a completed pass; step bonus and a
 * proportional base apply to partial completion too.
 */
object ScoreEngine {

    const val SPEED_FULL = 50
    const val SPEED_HALF = 25
    const val PRECISION_BONUS = 25
    const val STEP_BONUS = 40
    const val HINT_PENALTY = 10
    const val STREAK_STEP = 0.1f
    const val STREAK_CAP = 1.5f

    data class ScoreBreakdown(
        val base: Int,
        val speedBonus: Int,
        val precisionBonus: Int,
        val stepBonus: Int,
        val streakMultiplier: Float,
        val hintPenalty: Int,
        val total: Int
    )

    /** 1.0 for a first win, +0.1 per additional consecutive pass, capped at 1.5. */
    fun streakMultiplier(streak: Int): Float =
        (1f + STREAK_STEP * max(0, streak - 1)).coerceIn(1f, STREAK_CAP)

    /** +50 within 30% of the limit, +25 within 60%, else 0. No limit → 0. */
    fun speedBonus(elapsedMs: Long, timeLimitMs: Long?): Int {
        if (timeLimitMs == null || timeLimitMs <= 0L) return 0
        val frac = elapsedMs.toFloat() / timeLimitMs
        return when {
            frac <= 0.30f -> SPEED_FULL
            frac <= 0.60f -> SPEED_HALF
            else -> 0
        }
    }

    /** +25 when the measured value is at most 60% of what was required (a clean, precise pass). */
    fun precisionBonus(measured: Float?, required: Float?): Int {
        if (measured == null || required == null || required <= 0f) return 0
        return if (measured <= 0.6f * required) PRECISION_BONUS else 0
    }

    /** +40 per completed step beyond the first. */
    fun stepBonus(completedSteps: Int): Int = STEP_BONUS * max(0, completedSteps - 1)

    fun compute(
        baseScore: Int,
        passed: Boolean,
        completedSteps: Int,
        stepCount: Int,
        elapsedMs: Long,
        timeLimitMs: Long?,
        streak: Int,
        hintsUsed: Int,
        precisionMeasured: Float? = null,
        precisionRequired: Float? = null
    ): ScoreBreakdown {
        val effectiveBase = when {
            passed -> baseScore
            stepCount <= 0 -> 0
            else -> (baseScore.toFloat() * completedSteps / stepCount).roundToInt()
        }
        val speed = if (passed) speedBonus(elapsedMs, timeLimitMs) else 0
        val precision = if (passed) precisionBonus(precisionMeasured, precisionRequired) else 0
        val steps = stepBonus(completedSteps)
        val multiplier = streakMultiplier(streak)
        val penalty = max(0, hintsUsed) * HINT_PENALTY

        val preMultiply = effectiveBase + speed + precision + steps
        val total = max(0, (preMultiply * multiplier).roundToInt() - penalty)
        return ScoreBreakdown(effectiveBase, speed, precision, steps, multiplier, penalty, total)
    }
}
