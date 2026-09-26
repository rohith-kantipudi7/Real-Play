package com.cognex.realplay.verify.verifiers

import com.cognex.realplay.challenge.ChallengeSpec
import com.cognex.realplay.challenge.VerificationStep
import com.cognex.realplay.verify.Resolution
import com.cognex.realplay.verify.RuleId
import com.cognex.realplay.verify.StepEvaluation
import com.cognex.realplay.verify.VerificationBaseline
import com.cognex.realplay.verify.Verifier
import com.cognex.realplay.verify.actorAt
import com.cognex.realplay.verify.evidence
import com.cognex.realplay.verify.p
import com.cognex.realplay.world.WorldState

/**
 * MOTION_BELOW (Architecture §4.1 — "statue" / red-light). actors[0] = player. The player's
 * [com.cognex.realplay.world.TrackedPlayer.motionEnergy] must be below `threshold` (default 0.05).
 */
internal class MotionBelowVerifier : Verifier {
    override val rule = RuleId.MOTION_BELOW
    override fun evaluate(
        step: VerificationStep, spec: ChallengeSpec, world: WorldState, baseline: VerificationBaseline
    ): StepEvaluation {
        Resolution.frameGuard(world)?.let { return it }
        val player = when (val r = Resolution.requirePlayer(spec.actorAt(0), world)) {
            is Resolution.PlayerResolution.Found -> r.player
            is Resolution.PlayerResolution.Missing -> return r.eval
        }
        val threshold = step.p("threshold", 0.05f)
        val motion = player.motionEnergy
        val satisfied = motion < threshold
        val ev = listOf(evidence("motion", motion, threshold, "<", satisfied))
        return if (satisfied) Resolution.pass(player.confidence, ev)
        else Resolution.fail("Freeze — hold very still", ev)
    }
}

/**
 * MOTION_ABOVE (Architecture §4.1 — "keep dancing"). actors[0] = player. Motion energy must exceed
 * `threshold` (default 0.15).
 */
internal class MotionAboveVerifier : Verifier {
    override val rule = RuleId.MOTION_ABOVE
    override fun evaluate(
        step: VerificationStep, spec: ChallengeSpec, world: WorldState, baseline: VerificationBaseline
    ): StepEvaluation {
        Resolution.frameGuard(world)?.let { return it }
        val player = when (val r = Resolution.requirePlayer(spec.actorAt(0), world)) {
            is Resolution.PlayerResolution.Found -> r.player
            is Resolution.PlayerResolution.Missing -> return r.eval
        }
        val threshold = step.p("threshold", 0.15f)
        val motion = player.motionEnergy
        val satisfied = motion > threshold
        val ev = listOf(evidence("motion", motion, threshold, ">", satisfied))
        return if (satisfied) Resolution.pass(player.confidence, ev)
        else Resolution.fail("Keep moving!", ev)
    }
}
