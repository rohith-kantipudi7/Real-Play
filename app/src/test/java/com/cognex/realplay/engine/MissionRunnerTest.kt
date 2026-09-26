package com.cognex.realplay.engine

import com.cognex.realplay.challenge.ActorRef
import com.cognex.realplay.challenge.AgeBand
import com.cognex.realplay.challenge.CFix
import com.cognex.realplay.challenge.ChallengeSpec
import com.cognex.realplay.challenge.ChallengeType
import com.cognex.realplay.challenge.Tier
import com.cognex.realplay.challenge.VerificationStep
import com.cognex.realplay.verify.RuleId
import com.cognex.realplay.world.WorldState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * MissionRunner enforces step ordering and never advances on a single frame (Architecture §13 S5,
 * §8.1b). Step 0 must fire before step 1 is even evaluated (mustFollowPreviousStep).
 */
class MissionRunnerTest {

    private val actors = listOf(ActorRef.ByTrackId(1), ActorRef.ByTrackId(2))

    /** A 2-step ordered mission: first move the objects APART, then move them TOGETHER. */
    private fun twoStepSpec() = ChallengeSpec(
        id = "test-2step",
        type = ChallengeType.MOVE_CLOSE,
        tier = Tier.HARD,
        ageBand = AgeBand.OLDER,
        actors = actors,
        instruction = "apart, then together",
        steps = listOf(
            VerificationStep(RuleId.DISTANCE_GREATER_THAN, mapOf("threshold" to 0.30f), holdMs = 0L),
            VerificationStep(RuleId.DISTANCE_LESS_THAN, mapOf("threshold" to 0.20f), holdMs = 0L, mustFollowPreviousStep = true)
        ),
        timeLimitMs = null,
        baseScore = 30,
        hints = emptyList()
    )

    private fun close(ts: Long): WorldState =
        CFix.world(objects = listOf(CFix.obj(1, cx = 0.40f), CFix.obj(2, cx = 0.45f)), timestampMs = ts)

    private fun far(ts: Long): WorldState =
        CFix.world(objects = listOf(CFix.obj(1, cx = 0.10f), CFix.obj(2, cx = 0.90f)), timestampMs = ts)

    @Test
    fun outOfOrderCompletion_doesNotAdvance() {
        val runner = MissionRunner(twoStepSpec())
        // Objects are CLOSE — step 1's condition (DISTANCE_LESS_THAN) holds, but step 0 (APART)
        // does not. The runner only evaluates step 0, so it must NOT advance or complete.
        var ts = 0L
        repeat(6) {
            val tick = runner.onFrame(close(ts), ts)
            assertEquals(0, runner.currentStepIndex)
            assertFalse(tick.missionComplete)
            assertEquals(0, tick.completedSteps)
            ts += 100L
        }
    }

    @Test
    fun orderedCompletion_advancesThenFinishes() {
        val runner = MissionRunner(twoStepSpec())
        var ts = 0L

        // Phase 1: FAR → step 0 (APART) fires after ≥2 eligible frames → advance to step 1.
        repeat(3) { runner.onFrame(far(ts), ts); ts += 100L }
        assertEquals(1, runner.currentStepIndex)
        assertFalse(runner.missionComplete)

        // Phase 2: CLOSE → step 1 (TOGETHER) fires → mission complete.
        var complete = false
        repeat(3) {
            val tick = runner.onFrame(close(ts), ts)
            complete = complete || tick.missionComplete
            ts += 100L
        }
        assertTrue(complete)
        assertTrue(runner.missionComplete)
        assertEquals(2, runner.completedSteps())
    }

    @Test
    fun singleTrueFrame_doesNotComplete() {
        // A single-step mission; one satisfying frame among unsatisfying → never completes.
        val spec = ChallengeSpec(
            id = "one", type = ChallengeType.MOVE_CLOSE, tier = Tier.EASY, ageBand = AgeBand.MIDDLE,
            actors = actors,
            instruction = "close",
            steps = listOf(VerificationStep(RuleId.DISTANCE_LESS_THAN, mapOf("threshold" to 0.20f), holdMs = 300L)),
            timeLimitMs = null, baseScore = 30, hints = emptyList()
        )
        val runner = MissionRunner(spec)
        runner.onFrame(close(0L), 0L)          // one satisfying frame
        val tick = runner.onFrame(far(100L), 100L)  // then unsatisfying
        assertFalse(tick.missionComplete)
        assertFalse(runner.missionComplete)
    }

    @Test
    fun poorFrame_yieldsUnsure_neverAdvances() {
        val spec = ChallengeSpec(
            id = "one", type = ChallengeType.MOVE_CLOSE, tier = Tier.EASY, ageBand = AgeBand.MIDDLE,
            actors = actors,
            instruction = "close",
            steps = listOf(VerificationStep(RuleId.DISTANCE_LESS_THAN, mapOf("threshold" to 0.20f), holdMs = 0L)),
            timeLimitMs = null, baseScore = 30, hints = emptyList()
        )
        val runner = MissionRunner(spec)
        val badWorld: WorldState = CFix.world(
            objects = listOf(CFix.obj(1, cx = 0.40f), CFix.obj(2, cx = 0.45f)),
            good = false, timestampMs = 0L
        )
        val tick = runner.onFrame(badWorld, 0L)
        assertTrue(tick.outcome is com.cognex.realplay.verify.VerificationOutcome.Unsure)
        assertFalse(tick.missionComplete)
    }
}
