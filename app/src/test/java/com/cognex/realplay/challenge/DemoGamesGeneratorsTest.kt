package com.cognex.realplay.challenge

import com.cognex.realplay.challenge.generators.GComboPoseGenerator
import com.cognex.realplay.challenge.generators.GGroupColourGenerator
import com.cognex.realplay.challenge.generators.GGroupKindGenerator
import com.cognex.realplay.challenge.generators.GLineUpGenerator
import com.cognex.realplay.challenge.generators.GShowNamedGenerator
import com.cognex.realplay.challenge.generators.GSortSizeGenerator
import com.cognex.realplay.verify.RuleId
import com.cognex.realplay.verify.VerifierRegistry
import com.cognex.realplay.world.ColorTag
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Behaviour of the new demo-library generators (Grab, Line-up, Sort, Group×2, Combo). */
class DemoGamesGeneratorsTest {

    // ── G8 · Grab ─────────────────────────────────────────────────────────────

    @Test fun grab_emitsObjectPresent_onTheNamedObject() {
        val g = GShowNamedGenerator()
        val objects = listOf(CFix.obj(1, label = "cup", w = 0.2f, h = 0.2f), CFix.obj(2, label = "book", w = 0.1f, h = 0.1f))
        val affs = objects.map { CFix.aff(it.trackId, movable = true, handheld = true, nameable = true) }
        val world = CFix.world(objects = objects, affordances = affs)
        val spec = g.generate(world, world.affordances, CFix.ctx(), 1)
        assertEquals(1, spec.steps.size)
        assertEquals(RuleId.OBJECT_PRESENT, spec.steps.first().rule)
        assertTrue(spec.steps.first().params.containsKey("minArea"))
        assertTrue(spec.instruction.contains("cup") || spec.instruction.contains("book"))
        assertTrue(spec.actors.first() is ActorRef.ByTrackId)
    }

    @Test fun grab_isAToddlerBasic_andOfferedEverywhere() {
        val g = GShowNamedGenerator()
        assertEquals(1f, g.ageGate(AgeBand.TODDLER), 0f)
        assertEquals(1f, g.ageGate(AgeBand.OLDER), 0f)
        assertTrue(g.feasibility(CFix.cap(nameableCount = 2), CFix.ctx()) > 0f)
        assertEquals(0f, g.feasibility(CFix.cap(nameableCount = 0), CFix.ctx()), 0f)
    }

    // ── G9 · Line-up ──────────────────────────────────────────────────────────

    @Test fun lineUp_emitsCollinearOverThreePlus() {
        val g = GLineUpGenerator()
        val objects = (1..3).map { CFix.obj(it, cx = 0.2f * it) }
        val affs = objects.map { CFix.aff(it.trackId, movable = true) }
        val world = CFix.world(objects = objects, affordances = affs)
        val spec = g.generate(world, world.affordances, CFix.ctx(), 1)
        assertEquals(RuleId.COLLINEAR, spec.steps.first().rule)
        assertTrue(spec.actors.size >= 3)
        assertEquals(0f, g.ageGate(AgeBand.EARLY), 0f)
        assertEquals(1f, g.ageGate(AgeBand.MIDDLE), 0f)
    }

    // ── G10 · Sort by size ──────────────────────────────────────────────────────

    @Test fun sort_emitsSizeOrder_withADirection_proOnly() {
        val g = GSortSizeGenerator()
        val objects = (1..3).map { CFix.obj(it, cx = 0.2f * it, w = 0.05f * it, h = 0.05f * it) }
        val affs = objects.map { CFix.aff(it.trackId, movable = true) }
        val world = CFix.world(objects = objects, affordances = affs)
        val spec = g.generate(world, world.affordances, CFix.ctx(ageBand = AgeBand.OLDER, tier = Tier.HARD), 1)
        assertEquals(RuleId.SIZE_ORDER, spec.steps.first().rule)
        assertTrue(spec.steps.first().params.containsKey("direction"))
        assertEquals(0f, g.ageGate(AgeBand.MIDDLE), 0f)
        assertEquals(1f, g.ageGate(AgeBand.OLDER), 0f)
    }

    // ── G11 / G12 · Grouping ────────────────────────────────────────────────────

    @Test fun groupColour_clustersTheSameColour() {
        val g = GGroupColourGenerator()
        val objects = listOf(
            CFix.obj(1, cx = 0.2f, color = ColorTag.RED),
            CFix.obj(2, cx = 0.3f, color = ColorTag.RED),
            CFix.obj(3, cx = 0.8f, color = ColorTag.BLUE)
        )
        val affs = objects.map { CFix.aff(it.trackId, movable = true) }
        val world = CFix.world(objects = objects, affordances = affs)
        val spec = g.generate(world, world.affordances, CFix.ctx(), 1)
        assertEquals(RuleId.GROUP_CLUSTERED, spec.steps.first().rule)
        // The red pair is the group.
        val ids = spec.actors.mapNotNull { (it as? ActorRef.ByTrackId)?.trackId }.toSet()
        assertEquals(setOf(1, 2), ids)
        assertEquals(0f, g.ageGate(AgeBand.TODDLER), 0f)
    }

    @Test fun groupKind_clustersTheSameLabel() {
        val g = GGroupKindGenerator()
        val objects = listOf(
            CFix.obj(1, cx = 0.2f, label = "cup"),
            CFix.obj(2, cx = 0.3f, label = "cup"),
            CFix.obj(3, cx = 0.8f, label = "book")
        )
        val affs = objects.map { CFix.aff(it.trackId, movable = true, nameable = true) }
        val world = CFix.world(objects = objects, affordances = affs)
        val spec = g.generate(world, world.affordances, CFix.ctx(), 1)
        assertEquals(RuleId.GROUP_CLUSTERED, spec.steps.first().rule)
        val ids = spec.actors.mapNotNull { (it as? ActorRef.ByTrackId)?.trackId }.toSet()
        assertEquals(setOf(1, 2), ids)
        assertTrue(spec.instruction.contains("cup"))
    }

    // ── G13 · Combo (finale) ────────────────────────────────────────────────────

    @Test fun combo_chainsHoldThenPose_proOnly() {
        val g = GComboPoseGenerator()
        val objects = listOf(CFix.obj(1, label = "bottle", w = 0.06f, h = 0.06f))
        val affs = objects.map { CFix.aff(it.trackId, handheld = true, nameable = true) }
        val world = CFix.world(objects = objects, players = listOf(CFix.player(1)), affordances = affs)
        val spec = g.generate(world, world.affordances, CFix.ctx(ageBand = AgeBand.OLDER), 2)
        assertEquals(2, spec.steps.size)
        assertEquals(RuleId.PLAYER_HOLDS_OBJECT, spec.steps[0].rule)
        assertEquals(RuleId.POSE_MATCH, spec.steps[1].rule)
        assertTrue(spec.steps[1].mustFollowPreviousStep)
        assertTrue(spec.steps[1].params.containsKey("poseId"))
        assertEquals(listOf(0, 1), spec.steps[0].actorIndices)
        assertEquals(listOf(0), spec.steps[1].actorIndices)
        assertTrue(spec.actors[0] is ActorRef.ByPlayer)
        assertTrue(spec.actors[1] is ActorRef.ByTrackId)
        assertEquals(0f, g.ageGate(AgeBand.MIDDLE), 0f)
        assertEquals(1f, g.ageGate(AgeBand.OLDER), 0f)
    }

    // ── Registry integration ─────────────────────────────────────────────────

    @Test fun everyNewGeneratorType_hasAVerifierBackedRule() {
        // The verify registry is total, so building a spec's rules always resolve. This asserts the
        // new generators produce specs whose rules are registered (no silent unverified step).
        val registry = VerifierRegistry.default()
        val allSpecs = ChallengeRegistry.default().generators.map { it.id }
        assertTrue(allSpecs.containsAll(listOf("G8", "G9", "G10", "G11", "G12", "G13")))
        // Spot-check the new rules resolve.
        assertNotNull(registry.verifierFor(RuleId.COLLINEAR))
        assertNotNull(registry.verifierFor(RuleId.SIZE_ORDER))
        assertNotNull(registry.verifierFor(RuleId.GROUP_CLUSTERED))
    }

    // ── Demo arc wiring ──────────────────────────────────────────────────────

    @Test fun demoArc_idsAllExistInRegistry() {
        val ids = ChallengeRegistry.default().generators.map { it.id }.toSet()
        for (band in AgeBand.entries) {
            assertTrue(com.cognex.realplay.engine.DemoArc.orderFor(band).all { it in ids })
        }
    }

    @Test fun demoArc_toddlerIsBasicGamesOnly() {
        // Toddler must never list geometry/pose/sort/combo — only Grab + Find-colour.
        assertEquals(listOf("G8", "G3"), com.cognex.realplay.engine.DemoArc.orderFor(AgeBand.TODDLER))
    }

    @Test fun canPlay_ignoresAgeGate_butHonoursRequirement() {
        val reg = ChallengeRegistry.default()
        // Triangle (G7) needs 3 movable; ignore tier via canPlay — true on EARLY when objects exist.
        val threeMovable = CFix.cap(movableCount = 3, nameableCount = 3, spread = 0.6f)
        assertTrue(reg.canPlay("G7", threeMovable, CFix.ctx(ageBand = AgeBand.EARLY)))
        // But false when the scene lacks the objects, regardless of tier.
        assertFalse(reg.canPlay("G7", CFix.cap(movableCount = 1), CFix.ctx(ageBand = AgeBand.OLDER)))
    }
}
