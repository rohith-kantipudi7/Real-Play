package com.cognex.realplay.challenge

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** ToddlerPolicy — knob overrides for the youngest band (Architecture §10, §13 S10). */
class ToddlerPolicyTest {

    @Test
    fun nonToddlerBands_areUnchanged() {
        val knobs = DifficultyKnobs.forTier(Tier.MEDIUM, spread = 0.5f, stability = 0.5f)
        for (band in listOf(AgeBand.EARLY, AgeBand.MIDDLE, AgeBand.OLDER)) {
            assertEquals(knobs, ToddlerPolicy.apply(knobs, band))
        }
    }

    @Test
    fun toddler_stripsTimeLimit() {
        val knobs = DifficultyKnobs.forTier(Tier.MEDIUM, spread = 0.5f, stability = 0.5f)
        assertNull(ToddlerPolicy.apply(knobs, AgeBand.TODDLER).timeLimitMs)
    }

    @Test
    fun toddler_widensToleranceAndHold() {
        val knobs = DifficultyKnobs.forTier(Tier.EASY, spread = 1f, stability = 1f)
        val out = ToddlerPolicy.apply(knobs, AgeBand.TODDLER)
        assertEquals(knobs.distanceThreshold * 2f, out.distanceThreshold, 1e-6f)
        assertEquals(knobs.poseToleranceDeg * 2f, out.poseToleranceDeg, 1e-6f)
        assertTrue(out.holdMs > knobs.holdMs)
    }

    @Test
    fun toddler_holdNeverExceedsCap_evenOnAJitteryScene() {
        // Jittery scene (stability 0) already inflates HARD's 2000 ms base hold before ToddlerPolicy
        // multiplies by 1.7 — without the cap this would compound to ~4760 ms.
        val knobs = DifficultyKnobs.forTier(Tier.HARD, spread = 0f, stability = 0f)
        val out = ToddlerPolicy.apply(knobs, AgeBand.TODDLER)
        assertTrue(out.holdMs <= ToddlerPolicy.MAX_HOLD_MS)
    }
}
