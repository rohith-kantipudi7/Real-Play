package com.cognex.realplay.engine

import com.cognex.realplay.challenge.AgeBand
import com.cognex.realplay.challenge.Tier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** DifficultyDirector — the resolved tier + presentable "why" string (Architecture §8, §13 S9). */
class DifficultyDirectorTest {

    @Test
    fun highSkill_richScene_older_playsHard_andSaysSkill() {
        val r = DifficultyDirector.resolve(
            rating = 90f, richness = 0.9f, band = AgeBand.OLDER, spread = 1f, stability = 1f
        )
        assertEquals(Tier.HARD, r.effectiveTier)
        assertEquals(DifficultyDirector.Ceiling.SKILL, r.bindingCeiling)
        assertTrue(r.explanation.contains("skill=HARD"))
        assertTrue(r.explanation.contains("playing HARD"))
    }

    @Test
    fun sceneCapsSkill_explanationNamesRichness() {
        // Skill HARD (rating 90) but a sparse scene (richness 0.41 → MEDIUM) is the binding ceiling.
        val r = DifficultyDirector.resolve(
            rating = 90f, richness = 0.41f, band = AgeBand.OLDER, spread = 1f, stability = 1f
        )
        assertEquals(Tier.MEDIUM, r.effectiveTier)
        assertEquals(DifficultyDirector.Ceiling.SCENE, r.bindingCeiling)
        assertEquals("skill=HARD but richness 0.41 → playing MEDIUM", r.explanation)
    }

    @Test
    fun ageCapsSkill_explanationNamesAge() {
        // Skill HARD, rich scene, but TODDLER caps to EASY.
        val r = DifficultyDirector.resolve(
            rating = 90f, richness = 0.9f, band = AgeBand.TODDLER, spread = 1f, stability = 1f
        )
        assertEquals(Tier.EASY, r.effectiveTier)
        assertEquals(DifficultyDirector.Ceiling.AGE, r.bindingCeiling)
        assertTrue(r.explanation.contains("age TODDLER"))
        assertTrue(r.explanation.contains("playing EASY"))
    }

    @Test
    fun knobsAreSceneScaled() {
        // Jittery scene (stability 0) demands a longer hold than a steady one.
        val steady = DifficultyDirector.resolve(60f, 0.7f, AgeBand.OLDER, spread = 1f, stability = 1f)
        val jittery = DifficultyDirector.resolve(60f, 0.7f, AgeBand.OLDER, spread = 1f, stability = 0f)
        assertEquals(Tier.MEDIUM, steady.effectiveTier)
        assertTrue(jittery.knobs.holdMs > steady.knobs.holdMs)
    }

    @Test
    fun hysteresis_holdsTierNearBoundary() {
        // Coming from EASY, a rating of 34 (just over 33) stays EASY until it clears 33+5.
        val r = DifficultyDirector.resolve(
            rating = 34f, richness = 0.9f, band = AgeBand.OLDER, spread = 1f, stability = 1f,
            previousTier = Tier.EASY
        )
        assertEquals(Tier.EASY, r.skillTier)
        assertEquals(Tier.EASY, r.effectiveTier)
    }
}
