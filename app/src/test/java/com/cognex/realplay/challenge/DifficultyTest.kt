package com.cognex.realplay.challenge

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Difficulty resolution + scene-scaled knobs (Architecture §8, §8.1). */
class DifficultyTest {

    @Test
    fun sceneTier_boundaries() {
        assertEquals(Tier.EASY, Difficulty.sceneTier(0.20f))
        assertEquals(Tier.EASY, Difficulty.sceneTier(0.34f))
        assertEquals(Tier.MEDIUM, Difficulty.sceneTier(0.35f))
        assertEquals(Tier.MEDIUM, Difficulty.sceneTier(0.64f))
        assertEquals(Tier.HARD, Difficulty.sceneTier(0.65f))
        assertEquals(Tier.HARD, Difficulty.sceneTier(0.95f))
    }

    @Test
    fun skillTier_noPrevious_usesRawBoundaries() {
        assertEquals(Tier.EASY, Difficulty.skillTier(10f))
        assertEquals(Tier.MEDIUM, Difficulty.skillTier(50f))
        assertEquals(Tier.HARD, Difficulty.skillTier(80f))
    }

    @Test
    fun skillTier_hysteresis_holdsNearBoundary() {
        // At rating 34 (just over 33), coming from EASY, hysteresis (needs 33+5=38) keeps EASY.
        assertEquals(Tier.EASY, Difficulty.skillTier(34f, Tier.EASY))
        // From MEDIUM, 34 is above the lower-5 (28) floor, so it stays MEDIUM.
        assertEquals(Tier.MEDIUM, Difficulty.skillTier(34f, Tier.MEDIUM))
    }

    @Test
    fun ageCap_limitsTier() {
        assertEquals(Tier.EASY, Difficulty.ageCapTier(AgeBand.TODDLER))
        assertEquals(Tier.MEDIUM, Difficulty.ageCapTier(AgeBand.EARLY))
        assertEquals(Tier.HARD, Difficulty.ageCapTier(AgeBand.MIDDLE))
        assertEquals(Tier.HARD, Difficulty.ageCapTier(AgeBand.OLDER))
    }

    @Test
    fun ageStepCap_isOneForYoungBands() {
        assertEquals(1, Difficulty.ageStepCap(AgeBand.TODDLER))
        assertEquals(1, Difficulty.ageStepCap(AgeBand.EARLY))
        assertEquals(2, Difficulty.ageStepCap(AgeBand.MIDDLE))
        assertEquals(2, Difficulty.ageStepCap(AgeBand.OLDER))
    }

    @Test
    fun effectiveTier_isTheMinimumOfThreeCeilings() {
        // High skill + rich scene, but TODDLER age caps it to EASY.
        assertEquals(Tier.EASY, Difficulty.effectiveTier(rating = 90f, richness = 0.9f, band = AgeBand.TODDLER))
        // High skill + rich scene, OLDER → HARD.
        assertEquals(Tier.HARD, Difficulty.effectiveTier(rating = 90f, richness = 0.9f, band = AgeBand.OLDER))
        // Rich scene but low skill → EASY (skill is the binding ceiling).
        assertEquals(Tier.EASY, Difficulty.effectiveTier(rating = 5f, richness = 0.9f, band = AgeBand.OLDER))
        // High skill but sparse scene → EASY (scene is the binding ceiling).
        assertEquals(Tier.EASY, Difficulty.effectiveTier(rating = 90f, richness = 0.1f, band = AgeBand.OLDER))
    }

    @Test
    fun knobs_perTierBaseValues() {
        // spread=1, stability=1 → no scaling.
        val easy = DifficultyKnobs.forTier(Tier.EASY, spread = 1f, stability = 1f)
        assertEquals(0.30f, easy.distanceThreshold, 1e-4f)
        assertEquals(500L, easy.holdMs)
        assertEquals(null, easy.timeLimitMs)

        val hard = DifficultyKnobs.forTier(Tier.HARD, spread = 1f, stability = 1f)
        assertEquals(0.12f, hard.distanceThreshold, 1e-4f)
        assertEquals(2000L, hard.holdMs)
        assertEquals(20_000L, hard.timeLimitMs)
    }

    @Test
    fun knobs_clusteredSceneIsMoreForgiving_jitteryDemandsLongerHold() {
        // spread=0 → distance × 1.3; stability=0 → hold × 1.4.
        val knobs = DifficultyKnobs.forTier(Tier.MEDIUM, spread = 0f, stability = 0f)
        assertEquals(0.20f * 1.3f, knobs.distanceThreshold, 1e-4f)
        assertEquals((1000L * 1.4f).toLong(), knobs.holdMs)
        assertTrue(knobs.distanceThreshold > 0.20f)
        assertTrue(knobs.holdMs > 1000L)
    }
}
