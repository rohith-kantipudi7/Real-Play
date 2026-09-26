package com.cognex.realplay.challenge

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * End-to-end TODDLER constraints across the live registry (Architecture §10, §13 S10):
 * step budget is always 1, no spec ever has a time limit, and no instruction addresses more than
 * 2 actors — checked against the actual shipped generators, not just the pure knob math.
 */
class ToddlerConstraintsTest {

    private fun toddlerCtx(spread: Float, stability: Float) = GenerationContext(
        ageBand = AgeBand.TODDLER,
        effectiveTier = Difficulty.effectiveTier(rating = 90f, richness = 0.9f, band = AgeBand.TODDLER),
        knobs = ToddlerPolicy.apply(
            DifficultyKnobs.forTier(Tier.EASY, spread, stability),
            AgeBand.TODDLER
        ),
        trackOnlyMode = false
    )

    @Test
    fun objectOnlyScene_selectsWithinToddlerConstraints() {
        val reg = ChallengeRegistry.default()
        val objects = listOf(
            CFix.obj(1, cx = 0.3f, color = com.cognex.realplay.world.ColorTag.RED),
            CFix.obj(2, cx = 0.7f, color = com.cognex.realplay.world.ColorTag.BLUE)
        )
        val world = CFix.world(objects = objects, affordances = objects.map { CFix.aff(it.trackId, movable = true) })
        val cap = CFix.cap(
            movableCount = 2, nameableCount = 2,
            distinctColors = setOf(com.cognex.realplay.world.ColorTag.RED, com.cognex.realplay.world.ColorTag.BLUE),
            richness = 0.9f
        )
        val result = reg.select(world, cap, toddlerCtx(spread = 1f, stability = 1f), SelectionMode.RECOMMENDED)

        assertEquals(1, result.stepBudget)
        assertTrue("no more than 2 actors: ${result.spec.actors}", result.spec.actors.size <= 2)
        assertEquals(null, result.spec.timeLimitMs)
    }

    @Test
    fun humanOnlyScene_selectsWithinToddlerConstraints() {
        val reg = ChallengeRegistry.default()
        val world = CFix.world(players = listOf(CFix.player(1)))
        val cap = CFix.cap(playerCount = 1, richness = 0.9f)
        val result = reg.select(world, cap, toddlerCtx(spread = 1f, stability = 1f), SelectionMode.RECOMMENDED)

        assertEquals(1, result.stepBudget)
        assertTrue("no more than 2 actors: ${result.spec.actors}", result.spec.actors.size <= 2)
        assertEquals(null, result.spec.timeLimitMs)
    }

    @Test
    fun emptyScene_g0StillRespectsConstraints() {
        val reg = ChallengeRegistry.default()
        val result = reg.select(CFix.world(), CFix.cap(), toddlerCtx(spread = 0f, stability = 0f), SelectionMode.RECOMMENDED)

        assertEquals("G0", result.winnerId)
        assertEquals(1, result.stepBudget)
        assertTrue(result.spec.actors.size <= 2)
        assertEquals(null, result.spec.timeLimitMs)
    }
}
