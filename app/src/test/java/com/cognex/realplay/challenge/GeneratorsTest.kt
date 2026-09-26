package com.cognex.realplay.challenge

import com.cognex.realplay.challenge.generators.G0LastResortGenerator
import com.cognex.realplay.challenge.generators.G1MoveNearGenerator
import com.cognex.realplay.verify.RuleId
import com.cognex.realplay.world.ColorTag
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** G0 & G1 generator behaviour (Architecture §6.4, §7, §7.1). */
class GeneratorsTest {

    // ── G0 ────────────────────────────────────────────────────────────────────

    @Test
    fun g0_hasNoRequirements_andIsAlwaysFeasible() {
        val g0 = G0LastResortGenerator()
        assertEquals(Requirement.NONE, g0.requires)
        assertTrue(g0.feasibility(CFix.cap(), CFix.ctx()) > 0f)   // even an empty scene
        assertEquals(1, g0.maxStepsForTier(Tier.HARD))            // never chains
    }

    @Test
    fun g0_objectVariant_whenOnlyObjectPresent() {
        val g0 = G0LastResortGenerator()
        val obj = CFix.obj(3, w = 0.3f, h = 0.3f)
        val world = CFix.world(objects = listOf(obj), affordances = listOf(CFix.aff(3, movable = true)))
        val spec = g0.generate(world, world.affordances, CFix.ctx(), 1)
        assertEquals(1, spec.steps.size)
        assertEquals(RuleId.OBJECT_PRESENT, spec.steps.first().rule)
        assertEquals(0.06f, spec.steps.first().params["minArea"])
        assertTrue(spec.actors.first() is ActorRef.ByTrackId)
    }

    @Test
    fun g0_playerVariant_whenPlayerPresent() {
        val g0 = G0LastResortGenerator()
        val world = CFix.world(players = listOf(CFix.player(1)))
        val spec = g0.generate(world, world.affordances, CFix.ctx(), 1)
        assertEquals(2, spec.steps.size)
        assertEquals(RuleId.LIMB_RAISED, spec.steps[0].rule)
        assertEquals(RuleId.MOTION_ABOVE, spec.steps[1].rule)
        assertTrue(spec.actors.first() is ActorRef.ByPlayer)
    }

    @Test
    fun g0_playerPreferredOverObject() {
        val g0 = G0LastResortGenerator()
        val world = CFix.world(
            objects = listOf(CFix.obj(1, w = 0.3f, h = 0.3f)),
            players = listOf(CFix.player(1)),
            affordances = listOf(CFix.aff(1, movable = true))
        )
        val spec = g0.generate(world, world.affordances, CFix.ctx(), 1)
        assertEquals(RuleId.LIMB_RAISED, spec.steps.first().rule)  // player variant wins
    }

    // ── G1 ────────────────────────────────────────────────────────────────────

    @Test
    fun g1_requiresTwoMovable_andChainsAtHardOnly() {
        val g1 = G1MoveNearGenerator()
        assertEquals(2, g1.requires.minMovable)
        assertEquals(1, g1.maxStepsForTier(Tier.EASY))
        assertEquals(1, g1.maxStepsForTier(Tier.MEDIUM))
        assertEquals(2, g1.maxStepsForTier(Tier.HARD))
        assertEquals(0f, g1.ageGate(AgeBand.TODDLER), 0f)   // not a toddler game
        assertEquals(1f, g1.ageGate(AgeBand.MIDDLE), 0f)
    }

    @Test
    fun g1_picksTwoSeparatedObjects_withDistanceStep() {
        val g1 = G1MoveNearGenerator()
        val objects = listOf(CFix.obj(1, cx = 0.15f, label = "bottle"), CFix.obj(2, cx = 0.85f, label = "book"))
        val affs = objects.map { CFix.aff(it.trackId, movable = true, nameable = true) }
        val world = CFix.world(objects = objects, affordances = affs)
        val spec = g1.generate(world, world.affordances, CFix.ctx(tier = Tier.EASY), 1)
        assertEquals(1, spec.steps.size)
        assertEquals(RuleId.DISTANCE_LESS_THAN, spec.steps.first().rule)
        assertEquals(2, spec.actors.size)
        assertTrue(spec.actors.all { it is ActorRef.ByTrackId })
        assertTrue(spec.instruction.contains("next to"))
        // Nameable → uses labels, not highlight-colour phrasing.
        assertFalse(spec.instruction.contains("glowing"))
    }

    @Test
    fun g1_fallsBackToHighlightColour_whenTrackOnly() {
        val g1 = G1MoveNearGenerator()
        val objects = listOf(
            CFix.obj(1, cx = 0.15f, label = "bottle", color = ColorTag.BLUE),
            CFix.obj(2, cx = 0.85f, label = "book", color = ColorTag.RED)
        )
        val affs = objects.map { CFix.aff(it.trackId, movable = true, nameable = true) }
        val world = CFix.world(objects = objects, affordances = affs)
        val spec = g1.generate(world, world.affordances, CFix.ctx(trackOnlyMode = true), 1)
        assertTrue(spec.instruction.contains("glowing"))
    }

    @Test
    fun g1_fallsBackToHighlightColour_whenNotNameable() {
        val g1 = G1MoveNearGenerator()
        val objects = listOf(
            CFix.obj(1, cx = 0.15f, label = "", color = ColorTag.BLUE),
            CFix.obj(2, cx = 0.85f, label = "", color = ColorTag.RED)
        )
        val affs = objects.map { CFix.aff(it.trackId, movable = true, nameable = false) }
        val world = CFix.world(objects = objects, affordances = affs)
        val spec = g1.generate(world, world.affordances, CFix.ctx(), 1)
        assertTrue(spec.instruction.contains("glowing"))
    }

    @Test
    fun g1_feasibilityRisesWithMovableAndSpread() {
        val g1 = G1MoveNearGenerator()
        val low = g1.feasibility(CFix.cap(movableCount = 2, spread = 0.1f), CFix.ctx())
        val high = g1.feasibility(CFix.cap(movableCount = 4, spread = 0.9f), CFix.ctx())
        assertTrue(high > low)
    }
}
