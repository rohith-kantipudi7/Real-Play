package com.cognex.realplay.challenge.generators

import com.cognex.realplay.challenge.CFix
import com.cognex.realplay.challenge.ChallengeType
import com.cognex.realplay.challenge.Tier
import com.cognex.realplay.verify.PoseLibrary
import com.cognex.realplay.verify.PoseMath
import com.cognex.realplay.verify.RuleId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/** JVM tests for the human-only games G4 / G5 and the pose library (Architecture §7, S8). */
class PoseGamesTest {

    // ── PoseLibrary ──────────────────────────────────────────────────────────

    @Test
    fun `the library ships eight distinct target poses`() {
        assertEquals(8, PoseLibrary.poses.size)
        val ids = PoseLibrary.poses.map { it.id }
        assertEquals("ids are unique", ids.toSet().size, ids.size)
    }

    @Test
    fun `each pose matches itself and differs from the others`() {
        for (a in PoseLibrary.poses) {
            assertEquals("self-distance is zero", 0f, PoseMath.poseDistance(a.vector, a.vector), 1e-4f)
        }
        // At least one clearly different pair (T-pose vs arms crossed) is well separated.
        val tPose = PoseLibrary.byId(0).vector
        val crossed = PoseLibrary.byId(5).vector
        assertTrue(PoseMath.poseDistance(tPose, crossed) > 0.3f)
    }

    @Test
    fun `baselineFor carries the requested pose as the reference`() {
        val baseline = PoseLibrary.baselineFor(3)
        assertEquals(PoseLibrary.byId(3).vector, baseline.referencePose)
    }

    // ── G4 Statue Match ──────────────────────────────────────────────────────

    @Test
    fun `G4 requires a player and scores a flat 0-85`() {
        val g4 = G4StatueMatchGenerator()
        assertEquals(1, g4.requires.minPlayers)
        assertEquals(0.85f, g4.feasibility(CFix.cap(playerCount = 1), CFix.ctx()), 1e-4f)
    }

    @Test
    fun `G4 emits one POSE_MATCH at EASY and two chained at HARD`() {
        val g4 = G4StatueMatchGenerator()
        val world = CFix.world(players = listOf(CFix.player(1)))

        val easy = g4.generate(world, emptyList(), CFix.ctx(tier = Tier.EASY), stepBudget = 1)
        assertEquals(1, easy.steps.size)
        assertEquals(RuleId.POSE_MATCH, easy.steps[0].rule)
        assertTrue("EASY step carries a poseId", easy.steps[0].params.containsKey("poseId"))

        val hard = g4.generate(world, emptyList(), CFix.ctx(tier = Tier.HARD), stepBudget = 2)
        assertEquals(2, hard.steps.size)
        assertTrue("second pose must follow the first", hard.steps[1].mustFollowPreviousStep)
        assertNotEquals(
            "two distinct poses",
            hard.steps[0].params["poseId"], hard.steps[1].params["poseId"]
        )
    }

    @Test
    fun `G4 maxStepsForTier is two only at HARD`() {
        val g4 = G4StatueMatchGenerator()
        assertEquals(1, g4.maxStepsForTier(Tier.EASY))
        assertEquals(1, g4.maxStepsForTier(Tier.MEDIUM))
        assertEquals(2, g4.maxStepsForTier(Tier.HARD))
    }

    // ── G5 Red Light / Green Light ───────────────────────────────────────────

    @Test
    fun `G5 feasibility is 0-775 at one player and 0-85 at two`() {
        val g5 = G5RedLightGreenLightGenerator()
        assertEquals(0.775f, g5.feasibility(CFix.cap(playerCount = 1), CFix.ctx()), 1e-4f)
        assertEquals(0.85f, g5.feasibility(CFix.cap(playerCount = 2), CFix.ctx()), 1e-4f)
    }

    @Test
    fun `G5 emits a single held MOTION_BELOW and never chains`() {
        val g5 = G5RedLightGreenLightGenerator()
        val world = CFix.world(players = listOf(CFix.player(1)))
        val spec = g5.generate(world, emptyList(), CFix.ctx(tier = Tier.MEDIUM), stepBudget = 1)
        assertEquals(1, spec.steps.size)
        assertEquals(RuleId.MOTION_BELOW, spec.steps[0].rule)
        assertTrue("red window is a real hold", spec.steps[0].holdMs > 0L)
        assertEquals(1, g5.maxStepsForTier(Tier.HARD))
    }

    // ── The v3.4 balance guarantee ───────────────────────────────────────────

    @Test
    fun `on a single-player human-only scene G4 and G5 both score above zero within 0-15`() {
        val g4 = G4StatueMatchGenerator()
        val g5 = G5RedLightGreenLightGenerator()
        val cap = CFix.cap(playerCount = 1) // human only: no objects
        val ctx = CFix.ctx()
        val f4 = g4.feasibility(cap, ctx)
        val f5 = g5.feasibility(cap, ctx)
        assertTrue("G4 > 0", f4 > 0f)
        assertTrue("G5 > 0", f5 > 0f)
        assertTrue("competitive within 0.15", abs(f4 - f5) <= 0.15f)
    }

    @Test
    fun `neither game is offered to toddlers`() {
        assertEquals(0f, G4StatueMatchGenerator().ageGate(com.cognex.realplay.challenge.AgeBand.TODDLER), 0f)
        assertEquals(0f, G5RedLightGreenLightGenerator().ageGate(com.cognex.realplay.challenge.AgeBand.TODDLER), 0f)
    }
}
