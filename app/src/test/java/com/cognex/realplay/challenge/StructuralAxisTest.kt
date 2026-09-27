package com.cognex.realplay.challenge

import com.cognex.realplay.challenge.generators.G1MoveNearGenerator
import com.cognex.realplay.challenge.generators.G2DropZoneGenerator
import com.cognex.realplay.challenge.generators.G3FindColorGenerator
import com.cognex.realplay.challenge.generators.G6FetchRaceGenerator
import com.cognex.realplay.challenge.generators.G7TriangleBuildGenerator
import com.cognex.realplay.engine.MissionRunner
import com.cognex.realplay.verify.RuleId
import com.cognex.realplay.world.ColorTag
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * S9 · structural axis (§8.1b) + G6/G7 (§7). Verifies the chained HARD forms, per-step actor
 * projection, the two new generators, and that TODDLER/EARLY stay single-step (§20 invariant 17).
 */
class StructuralAxisTest {

    // ── per-step actor projection (§8.1b) ──────────────────────────────────────

    @Test
    fun scopedTo_projectsOnlyTheStepsActors() {
        val step = VerificationStep(RuleId.POINT_IN_ZONE, emptyMap(), 0L, actorIndices = listOf(2, 3))
        val spec = ChallengeSpec(
            id = "t", type = ChallengeType.DROP_ZONE, tier = Tier.HARD, ageBand = AgeBand.OLDER,
            actors = listOf(
                ActorRef.ByTrackId(10), ActorRef.ByZone("zA"),
                ActorRef.ByTrackId(20), ActorRef.ByZone("zB")
            ),
            instruction = "", steps = listOf(step), timeLimitMs = null, baseScore = 0, hints = emptyList()
        )
        val scoped = spec.scopedTo(step)
        assertEquals(listOf(ActorRef.ByTrackId(20), ActorRef.ByZone("zB")), scoped.actors)
        // A step with no actorIndices leaves the spec unchanged.
        val plain = step.copy(actorIndices = null)
        assertEquals(4, spec.scopedTo(plain).actors.size)
    }

    // ── G1 HARD: 2 chained steps, ordered (§7.1) ──────────────────────────────

    @Test
    fun g1_hard_emitsTwoOrderedSteps_secondIsDistanceGreater() {
        val g1 = G1MoveNearGenerator()
        val objects = listOf(
            CFix.obj(1, cx = 0.20f, label = "bottle", color = ColorTag.BLUE, w = 0.08f, h = 0.08f),
            CFix.obj(2, cx = 0.85f, label = "book", color = ColorTag.RED, w = 0.14f, h = 0.14f),
            CFix.obj(3, cx = 0.50f, label = "cup", color = ColorTag.GREEN, w = 0.06f, h = 0.06f)
        )
        val affs = objects.map { CFix.aff(it.trackId, movable = true, nameable = true, distinct = true) }
        val world = CFix.world(objects = objects, affordances = affs)
        val spec = g1.generate(world, world.affordances, CFix.ctx(tier = Tier.HARD), stepBudget = 2)

        assertEquals(2, spec.steps.size)
        assertEquals(RuleId.DISTANCE_LESS_THAN, spec.steps[0].rule)
        assertEquals(RuleId.DISTANCE_GREATER_THAN, spec.steps[1].rule)
        assertTrue(spec.steps[1].mustFollowPreviousStep)
        assertEquals(listOf(0, 1), spec.steps[0].actorIndices)
        assertEquals(listOf(2, 1), spec.steps[1].actorIndices)
        assertEquals(3, spec.actors.size)
    }

    @Test
    fun g1_hard_missionRunnerRejectsOutOfOrderCompletion() {
        val g1 = G1MoveNearGenerator()
        // Layout: everything far apart — step 0 (move A next to B) is NOT satisfied, but step 1's
        // condition (C far from B) IS. The runner must stay on step 0 and never complete.
        val objects = listOf(
            CFix.obj(1, cx = 0.10f, w = 0.06f, h = 0.06f),
            CFix.obj(2, cx = 0.50f, w = 0.06f, h = 0.06f),
            CFix.obj(3, cx = 0.90f, w = 0.05f, h = 0.05f)
        )
        val affs = objects.map { CFix.aff(it.trackId, movable = true, nameable = true) }
        val world = CFix.world(objects = objects, affordances = affs)
        val spec = g1.generate(world, world.affordances, CFix.ctx(tier = Tier.HARD), stepBudget = 2)
        assertEquals(2, spec.steps.size)

        val runner = MissionRunner(spec)
        var ts = 0L
        repeat(6) {
            val tick = runner.onFrame(world, ts)
            assertEquals(0, runner.currentStepIndex)
            assertFalse(tick.missionComplete)
            ts += 100L
        }
    }

    @Test
    fun g1_easy_isSingleStep() {
        val g1 = G1MoveNearGenerator()
        val objects = listOf(CFix.obj(1, cx = 0.2f), CFix.obj(2, cx = 0.8f))
        val affs = objects.map { CFix.aff(it.trackId, movable = true, nameable = true) }
        val world = CFix.world(objects = objects, affordances = affs)
        val spec = g1.generate(world, world.affordances, CFix.ctx(tier = Tier.EASY), stepBudget = 1)
        assertEquals(1, spec.steps.size)
    }

    // ── G3 HARD: two colours in order (§7.1) ──────────────────────────────────

    @Test
    fun g3_hard_emitsTwoColourStepsInOrder() {
        val g3 = G3FindColorGenerator()
        val objects = listOf(
            CFix.obj(1, cx = 0.5f, color = ColorTag.GREEN, w = 0.4f, h = 0.4f), // largest → excluded
            CFix.obj(2, cx = 0.2f, color = ColorTag.RED, w = 0.08f, h = 0.08f),
            CFix.obj(3, cx = 0.8f, color = ColorTag.BLUE, w = 0.08f, h = 0.08f)
        )
        val affs = objects.map { CFix.aff(it.trackId, nameable = true, distinct = true) }
        val world = CFix.world(objects = objects, affordances = affs)
        val spec = g3.generate(world, world.affordances, CFix.ctx(tier = Tier.HARD), stepBudget = 2)

        assertEquals(2, spec.steps.size)
        assertEquals(RuleId.COLOR_MATCH, spec.steps[0].rule)
        assertEquals(RuleId.COLOR_MATCH, spec.steps[1].rule)
        assertTrue(spec.steps[1].mustFollowPreviousStep)
        assertEquals(listOf(0), spec.steps[0].actorIndices)
        assertEquals(listOf(1), spec.steps[1].actorIndices)
        // Two DIFFERENT colours requested.
        assertTrue(spec.steps[0].params["color"] != spec.steps[1].params["color"])
    }

    // ── G2 HARD: two objects → two zones in order (§7.1) ──────────────────────

    @Test
    fun g2_hard_emitsTwoZoneStepsInOrder() {
        val g2 = G2DropZoneGenerator()
        val objects = listOf(
            CFix.obj(1, cx = 0.95f, cy = 0.95f, w = 0.05f, h = 0.05f),
            CFix.obj(2, cx = 0.05f, cy = 0.05f, w = 0.05f, h = 0.05f)
        )
        val affs = objects.map { CFix.aff(it.trackId, movable = true, handheld = true, nameable = true) }
        val zones = listOf(CFix.zone("zA", cx = 0.25f, cy = 0.5f), CFix.zone("zB", cx = 0.75f, cy = 0.5f))
        val world = CFix.world(objects = objects, zones = zones, affordances = affs)
        val spec = g2.generate(world, world.affordances, CFix.ctx(tier = Tier.HARD), stepBudget = 2)

        assertEquals(2, spec.steps.size)
        assertEquals(RuleId.POINT_IN_ZONE, spec.steps[0].rule)
        assertEquals(RuleId.POINT_IN_ZONE, spec.steps[1].rule)
        assertTrue(spec.steps[1].mustFollowPreviousStep)
        assertEquals(listOf(0, 1), spec.steps[0].actorIndices)
        assertEquals(listOf(2, 3), spec.steps[1].actorIndices)
        assertEquals(4, spec.actors.size)
    }

    // ── TODDLER / EARLY stay single-step across a sweep (§20 invariant 17) ────

    @Test
    fun registry_clampsStepBudgetToOne_forToddlerAndEarly() {
        val registry = ChallengeRegistry.default()
        for (band in listOf(AgeBand.TODDLER, AgeBand.EARLY)) {
            for (tier in Tier.entries) {
                val ctx = CFix.ctx(ageBand = band, tier = tier)
                for (gen in registry.generators) {
                    assertEquals(
                        "budget must be 1 for $band at $tier (${gen.id})",
                        1, registry.resolveStepBudget(gen, ctx)
                    )
                }
            }
        }
    }

    // ── G6 Fetch Race (§7) ─────────────────────────────────────────────────────

    @Test
    fun g6_requirements_feasibility_ageGate() {
        val g6 = G6FetchRaceGenerator()
        assertEquals(1, g6.requires.minPlayers)
        assertEquals(2, g6.requires.minHandheld)
        assertTrue(g6.requires.needsNameable)
        assertEquals(1, g6.maxStepsForTier(Tier.HARD))
        assertEquals(0.8f, g6.feasibility(CFix.cap(handheldCount = 2), CFix.ctx()), 1e-4f)
        assertEquals(0f, g6.ageGate(AgeBand.TODDLER), 0f)
        assertEquals(0f, g6.ageGate(AgeBand.EARLY), 0f)
        assertEquals(1f, g6.ageGate(AgeBand.MIDDLE), 0f)
    }

    @Test
    fun g6_namesPlayer_andEmitsOrderedFetchSteps() {
        val g6 = G6FetchRaceGenerator()
        val objects = listOf(
            CFix.obj(1, cx = 0.3f, label = "ball", color = ColorTag.RED, w = 0.06f, h = 0.06f),
            CFix.obj(2, cx = 0.7f, label = "block", color = ColorTag.BLUE, w = 0.06f, h = 0.06f)
        )
        val affs = objects.map { CFix.aff(it.trackId, movable = true, handheld = true, nameable = true, distinct = true) }
        val zones = listOf(CFix.zone("finish", cx = 0.5f, cy = 0.85f))
        val world = CFix.world(
            objects = objects, players = listOf(CFix.player(2, confidence = 0.95f)),
            zones = zones, affordances = affs
        )
        val spec = g6.generate(world, world.affordances, CFix.ctx(ageBand = AgeBand.OLDER, tier = Tier.MEDIUM), stepBudget = 1)

        assertTrue(spec.instruction.contains("Player 2"))
        assertEquals(3, spec.steps.size)
        assertEquals(RuleId.PLAYER_HOLDS_OBJECT, spec.steps[0].rule)
        assertEquals(listOf(0, 1), spec.steps[0].actorIndices)
        assertEquals(RuleId.COLOR_MATCH, spec.steps[1].rule)
        assertEquals(listOf(1), spec.steps[1].actorIndices)
        assertTrue(spec.steps[1].mustFollowPreviousStep)
        assertEquals(RuleId.PLAYER_IN_ZONE, spec.steps[2].rule)
        assertEquals(listOf(0, 2), spec.steps[2].actorIndices)
        assertTrue(spec.steps[2].mustFollowPreviousStep)
    }

    @Test
    fun g6_withoutZone_dropsDeliveryStep() {
        val g6 = G6FetchRaceGenerator()
        val objects = listOf(
            CFix.obj(1, label = "ball", color = ColorTag.RED, w = 0.06f, h = 0.06f),
            CFix.obj(2, label = "block", color = ColorTag.BLUE, w = 0.06f, h = 0.06f)
        )
        val affs = objects.map { CFix.aff(it.trackId, movable = true, handheld = true, nameable = true) }
        val world = CFix.world(objects = objects, players = listOf(CFix.player(1)), affordances = affs)
        val spec = g6.generate(world, world.affordances, CFix.ctx(ageBand = AgeBand.OLDER, tier = Tier.MEDIUM), stepBudget = 1)
        assertEquals(2, spec.steps.size)
        assertTrue(spec.steps.none { it.rule == RuleId.PLAYER_IN_ZONE })
    }

    // ── G7 Triangle Build (§7, §7.1) ──────────────────────────────────────────

    @Test
    fun g7_feasibility_zeroBelowThree_thenFormula() {
        val g7 = G7TriangleBuildGenerator()
        assertEquals(0f, g7.feasibility(CFix.cap(movableCount = 2), CFix.ctx()), 0f)
        val f = g7.feasibility(
            CFix.cap(movableCount = 3, spread = 1f, distinctColors = setOf(ColorTag.RED, ColorTag.BLUE, ColorTag.GREEN)),
            CFix.ctx()
        )
        assertEquals(1.0f, f, 1e-4f)
        assertEquals(3, g7.requires.minMovable)
        assertEquals(1, g7.maxStepsForTier(Tier.HARD))
    }

    @Test
    fun g7_emitsSingleTriangleStepOverThreeCorners() {
        val g7 = G7TriangleBuildGenerator()
        val objects = listOf(
            CFix.obj(1, cx = 0.2f, cy = 0.2f, color = ColorTag.RED),
            CFix.obj(2, cx = 0.8f, cy = 0.3f, color = ColorTag.BLUE),
            CFix.obj(3, cx = 0.5f, cy = 0.8f, color = ColorTag.GREEN)
        )
        val affs = objects.map { CFix.aff(it.trackId, movable = true, distinct = true) }
        val world = CFix.world(objects = objects, affordances = affs)
        val spec = g7.generate(world, world.affordances, CFix.ctx(tier = Tier.HARD), stepBudget = 1)
        assertEquals(1, spec.steps.size)
        assertEquals(RuleId.NON_DEGENERATE_TRIANGLE, spec.steps[0].rule)
        assertEquals(3, spec.actors.size)
        assertNotNull(spec.steps[0].params["minAngle"])
    }

    // ── registry ships the full demo game library ─────────────────────────────

    @Test
    fun registry_shipsAllGenerators() {
        // G0–G7 base + G8–G13 demo library.
        assertEquals(14, ChallengeRegistry.default().generators.size)
    }
}
