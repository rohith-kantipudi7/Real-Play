package com.cognex.realplay.challenge

import com.cognex.realplay.challenge.generators.G0LastResortGenerator
import com.cognex.realplay.challenge.generators.G1MoveNearGenerator
import com.cognex.realplay.challenge.generators.G2DropZoneGenerator
import com.cognex.realplay.challenge.generators.G3FindColorGenerator
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

    // ── G2 ────────────────────────────────────────────────────────────────────

    @Test
    fun g2_requirements_feasibility_ageGate() {
        val g2 = G2DropZoneGenerator()
        assertEquals(1, g2.requires.minMovable)
        assertEquals(1, g2.requires.minContainerOrZone)
        assertEquals(2, g2.maxStepsForTier(Tier.HARD))
        assertEquals(1, g2.maxStepsForTier(Tier.EASY))
        assertEquals(0f, g2.ageGate(AgeBand.TODDLER), 0f)     // not a toddler game (§10)
        assertEquals(1f, g2.ageGate(AgeBand.EARLY), 0f)
        // 0.9 with a zone, 0.6 for the object-container path (§7).
        assertEquals(0.9f, g2.feasibility(CFix.cap(zoneCount = 1), CFix.ctx()), 1e-4f)
        assertEquals(0.6f, g2.feasibility(CFix.cap(containerCount = 1), CFix.ctx()), 1e-4f)
    }

    @Test
    fun g2_prefersDetectedZone_withPointInZoneStep() {
        val g2 = G2DropZoneGenerator()
        val ball = CFix.obj(1, cx = 0.2f, cy = 0.2f)          // outside the centred zone
        val box = CFix.obj(2, cx = 0.7f, cy = 0.7f)
        val world = CFix.world(
            objects = listOf(ball, box),
            zones = listOf(CFix.zone("Z1", cx = 0.5f, cy = 0.5f, half = 0.12f)),
            affordances = listOf(CFix.aff(1, movable = true), CFix.aff(2, movable = true, container = true))
        )
        val spec = g2.generate(world, world.affordances, CFix.ctx(), 1)
        assertEquals(1, spec.steps.size)
        assertEquals(RuleId.POINT_IN_ZONE, spec.steps.first().rule)
        assertEquals(2, spec.actors.size)
        assertTrue(spec.actors[0] is ActorRef.ByTrackId)
        assertTrue(spec.actors[1] is ActorRef.ByZone)         // a zone was preferred over the container
    }

    @Test
    fun g2_zoneSubject_isNeverAlreadyInsideTheZone() {
        val g2 = G2DropZoneGenerator()
        val inside = CFix.obj(1, cx = 0.5f, cy = 0.5f)        // already in the zone
        val outside = CFix.obj(2, cx = 0.15f, cy = 0.15f)     // outside it
        val world = CFix.world(
            objects = listOf(inside, outside),
            zones = listOf(CFix.zone("Z1", cx = 0.5f, cy = 0.5f, half = 0.1f)),
            affordances = listOf(CFix.aff(1, movable = true), CFix.aff(2, movable = true))
        )
        val spec = g2.generate(world, world.affordances, CFix.ctx(), 1)
        assertEquals(2, (spec.actors[0] as ActorRef.ByTrackId).trackId)   // chose the outside one
    }

    @Test
    fun g2_objectContainerPath_sourceNeverEqualsTarget() {
        val g2 = G2DropZoneGenerator()
        val ball = CFix.obj(1, cx = 0.2f, cy = 0.2f)
        val cup = CFix.obj(2, cx = 0.7f, cy = 0.7f)
        val world = CFix.world(
            objects = listOf(ball, cup),
            affordances = listOf(
                CFix.aff(1, movable = true),
                CFix.aff(2, movable = true, handheld = true, container = true)
            )
        )
        val spec = g2.generate(world, world.affordances, CFix.ctx(), 1)
        assertEquals(RuleId.OVERLAP_RATIO_ABOVE, spec.steps.first().rule)
        assertEquals(0.5f, spec.steps.first().params["threshold"])
        val source = (spec.actors[0] as ActorRef.ByTrackId).trackId
        val target = (spec.actors[1] as ActorRef.ByTrackId).trackId
        assertTrue("source must differ from target (§7.1)", source != target)
    }

    @Test
    fun g2_cupInItself_isImpossible_degradesToBringClose() {
        // The ONLY object is both movable and a container. Without the guard the game would ask to
        // put the cup inside itself; instead it must degrade to a single-actor "bring it close".
        val g2 = G2DropZoneGenerator()
        val cup = CFix.obj(7, cx = 0.5f, cy = 0.5f)
        val world = CFix.world(
            objects = listOf(cup),
            affordances = listOf(CFix.aff(7, movable = true, handheld = true, container = true))
        )
        val spec = g2.generate(world, world.affordances, CFix.ctx(), 1)
        assertEquals(1, spec.actors.size)                     // never a source==target pair
        assertEquals(RuleId.OBJECT_PRESENT, spec.steps.first().rule)
    }

    // ── G3 ────────────────────────────────────────────────────────────────────

    @Test
    fun g3_requirements_feasibility_ageGate() {
        val g3 = G3FindColorGenerator()
        assertEquals(2, g3.requires.minDistinctColors)
        assertEquals(2, g3.maxStepsForTier(Tier.HARD))
        assertEquals(1, g3.maxStepsForTier(Tier.EASY))
        assertEquals(1f, g3.ageGate(AgeBand.TODDLER), 0f)     // offered in every band (§10)
        assertEquals(0.7f, g3.feasibility(CFix.cap(distinctColors = setOf(ColorTag.RED, ColorTag.BLUE)), CFix.ctx()), 1e-4f)
        // Capped at 0.95.
        val many = setOf(ColorTag.RED, ColorTag.BLUE, ColorTag.GREEN, ColorTag.YELLOW, ColorTag.PURPLE)
        assertEquals(0.95f, g3.feasibility(CFix.cap(distinctColors = many), CFix.ctx()), 1e-4f)
    }

    @Test
    fun g3_picksColour_notOnTheLargestObject() {
        val g3 = G3FindColorGenerator()
        val bigGreen = CFix.obj(1, cx = 0.5f, cy = 0.5f, w = 0.4f, h = 0.4f, color = ColorTag.GREEN)
        val smallRed = CFix.obj(2, cx = 0.2f, cy = 0.2f, w = 0.1f, h = 0.1f, color = ColorTag.RED)
        val world = CFix.world(
            objects = listOf(bigGreen, smallRed),
            affordances = listOf(CFix.aff(1), CFix.aff(2, movable = true, handheld = true))
        )
        val spec = g3.generate(world, world.affordances, CFix.ctx(), 1)
        assertEquals(RuleId.COLOR_MATCH, spec.steps.first().rule)
        assertEquals(ColorTag.RED.ordinal.toFloat(), spec.steps.first().params["color"])
        assertEquals(2, (spec.actors[0] as ActorRef.ByTrackId).trackId)   // the red (non-largest) object
        assertTrue(spec.instruction.contains("red"))
    }
}

