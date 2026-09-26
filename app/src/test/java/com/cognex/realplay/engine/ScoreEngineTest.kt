package com.cognex.realplay.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The scoring formula (Architecture §9). Pure, deterministic. */
class ScoreEngineTest {

    @Test
    fun streakMultiplier_stepsAndCaps() {
        assertEquals(1.0f, ScoreEngine.streakMultiplier(0), 1e-4f)
        assertEquals(1.0f, ScoreEngine.streakMultiplier(1), 1e-4f)
        assertEquals(1.1f, ScoreEngine.streakMultiplier(2), 1e-4f)
        assertEquals(1.2f, ScoreEngine.streakMultiplier(3), 1e-4f)
        assertEquals(1.5f, ScoreEngine.streakMultiplier(6), 1e-4f)
        assertEquals(1.5f, ScoreEngine.streakMultiplier(50), 1e-4f)  // capped
    }

    @Test
    fun speedBonus_thresholds() {
        assertEquals(50, ScoreEngine.speedBonus(2_000L, 10_000L))   // 20% → +50
        assertEquals(25, ScoreEngine.speedBonus(5_000L, 10_000L))   // 50% → +25
        assertEquals(0, ScoreEngine.speedBonus(8_000L, 10_000L))    // 80% → 0
        assertEquals(0, ScoreEngine.speedBonus(1_000L, null))       // no limit → 0
    }

    @Test
    fun precisionBonus_awardedWhenComfortablyUnderTarget() {
        assertEquals(25, ScoreEngine.precisionBonus(0.10f, 0.30f))  // 0.10 ≤ 0.18
        assertEquals(0, ScoreEngine.precisionBonus(0.25f, 0.30f))   // 0.25 > 0.18
        assertEquals(0, ScoreEngine.precisionBonus(null, 0.30f))
    }

    @Test
    fun stepBonus_perStepBeyondFirst() {
        assertEquals(0, ScoreEngine.stepBonus(1))
        assertEquals(40, ScoreEngine.stepBonus(2))
        assertEquals(80, ScoreEngine.stepBonus(3))
    }

    @Test
    fun compute_fullPass_appliesBonusesAndMultiplier() {
        val b = ScoreEngine.compute(
            baseScore = 30, passed = true, completedSteps = 2, stepCount = 2,
            elapsedMs = 2_000L, timeLimitMs = 10_000L, streak = 2, hintsUsed = 0,
            precisionMeasured = 0.10f, precisionRequired = 0.30f
        )
        // (30 base + 50 speed + 25 precision + 40 step) × 1.1 = 145 × 1.1 = 159.5 → 160
        assertEquals(30, b.base)
        assertEquals(50, b.speedBonus)
        assertEquals(25, b.precisionBonus)
        assertEquals(40, b.stepBonus)
        assertEquals(1.1f, b.streakMultiplier, 1e-4f)
        assertEquals(160, b.total)
    }

    @Test
    fun compute_partialCompletion_stillScores() {
        // 1 of 2 steps done, mission not passed → proportional base, no speed/precision, no step bonus.
        val b = ScoreEngine.compute(
            baseScore = 30, passed = false, completedSteps = 1, stepCount = 2,
            elapsedMs = 25_000L, timeLimitMs = 20_000L, streak = 0, hintsUsed = 0
        )
        assertEquals(15, b.base)      // 30 × 1/2
        assertEquals(0, b.speedBonus)
        assertEquals(0, b.stepBonus)
        assertEquals(15, b.total)
    }

    @Test
    fun compute_hintPenalty_floorsAtZero() {
        val b = ScoreEngine.compute(
            baseScore = 10, passed = true, completedSteps = 1, stepCount = 1,
            elapsedMs = 100L, timeLimitMs = null, streak = 1, hintsUsed = 5
        )
        // 10 × 1.0 − 50 = −40 → floored to 0
        assertEquals(50, b.hintPenalty)
        assertEquals(0, b.total)
    }

    @Test
    fun compute_totalNeverNegative() {
        val b = ScoreEngine.compute(
            baseScore = 0, passed = false, completedSteps = 0, stepCount = 1,
            elapsedMs = 0L, timeLimitMs = null, streak = 0, hintsUsed = 3
        )
        assertTrue(b.total >= 0)
    }
}
