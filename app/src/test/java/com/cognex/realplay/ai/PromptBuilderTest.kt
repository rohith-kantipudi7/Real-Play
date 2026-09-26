package com.cognex.realplay.ai

import com.cognex.realplay.challenge.AgeBand
import com.cognex.realplay.challenge.CFix
import com.cognex.realplay.challenge.ChallengeType
import com.cognex.realplay.challenge.DifficultyKnobs
import com.cognex.realplay.challenge.GenerationContext
import com.cognex.realplay.challenge.RankedCandidate
import com.cognex.realplay.challenge.Tier
import com.cognex.realplay.world.ColorTag
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pure-JVM tests for the composer prompt (Architecture §6.5). */
class PromptBuilderTest {

    private fun ctx(trackOnly: Boolean = false) = GenerationContext(
        ageBand = AgeBand.MIDDLE,
        effectiveTier = Tier.MEDIUM,
        knobs = DifficultyKnobs.forTier(Tier.MEDIUM, 0.5f, 1f),
        trackOnlyMode = trackOnly
    )

    private val ranked = listOf(
        RankedCandidate("G1", ChallengeType.MOVE_CLOSE, 0.8f, 1f, 1f, 1f, 0.8f, null),
        RankedCandidate("G0", ChallengeType.LAST_RESORT, 0.05f, 1f, 1f, 1f, 0.05f, null),
        RankedCandidate("G7", ChallengeType.TRIANGLE_BUILD, 0f, 0f, 0f, 0f, 0f, "needs 3 movable, found 2")
    )

    @Test fun skills_drop_zero_scored_candidates_and_sort_by_score() {
        val skills = PromptBuilder.skillsFrom(ranked)
        assertTrue(skills.map { it.generatorId } == listOf("G1", "G0"))
    }

    @Test fun prompt_lists_only_feasible_skills() {
        val world = CFix.world(objects = listOf(CFix.obj(1, label = "cup")))
        val cap = CFix.cap(movableCount = 1, distinctColors = setOf(ColorTag.BLUE))
        val bundle = PromptBuilder.build(ranked, world, cap, ctx())
        assertTrue(bundle.prompt.contains("id=G1"))
        assertTrue(bundle.prompt.contains("id=G0"))
        assertFalse(bundle.prompt.contains("id=G7"))
    }

    @Test fun track_only_scene_instructs_colour_phrasing() {
        val world = CFix.world(objects = listOf(CFix.obj(1, label = "cup")))
        val cap = CFix.cap(movableCount = 1, trackOnlyMode = true)
        val bundle = PromptBuilder.build(ranked, world, cap, ctx(trackOnly = true))
        assertTrue(bundle.prompt.uppercase().contains("UNRELIABLE"))
    }

    @Test fun prompt_demands_json_only() {
        val world = CFix.world(objects = listOf(CFix.obj(1, label = "cup")))
        val cap = CFix.cap(movableCount = 1)
        val bundle = PromptBuilder.build(ranked, world, cap, ctx())
        assertTrue(bundle.prompt.contains("\"generatorId\""))
    }
}
