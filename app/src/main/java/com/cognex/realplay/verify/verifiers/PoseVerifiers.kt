package com.cognex.realplay.verify.verifiers

import com.cognex.realplay.challenge.ChallengeSpec
import com.cognex.realplay.challenge.VerificationStep
import com.cognex.realplay.verify.PoseJoint
import com.cognex.realplay.verify.PoseMath
import com.cognex.realplay.verify.Resolution
import com.cognex.realplay.verify.RuleId
import com.cognex.realplay.verify.StepEvaluation
import com.cognex.realplay.verify.VerificationBaseline
import com.cognex.realplay.verify.Verifier
import com.cognex.realplay.verify.actorAt
import com.cognex.realplay.verify.evidence
import com.cognex.realplay.verify.p
import com.cognex.realplay.world.Landmark
import com.cognex.realplay.world.TrackedPlayer
import com.cognex.realplay.world.WorldState

/**
 * POSE_MATCH (Architecture §4.1). actors[0] = player; [VerificationBaseline.referencePose] is the
 * captured target. The angular [PoseMath.poseDistance] is invariant to camera distance, so the same
 * pose at 1 m and 2 m matches. No reference pose or no reliably-visible joints → Unsure.
 */
internal class PoseMatchVerifier : Verifier {
    override val rule = RuleId.POSE_MATCH
    override fun evaluate(
        step: VerificationStep, spec: ChallengeSpec, world: WorldState, baseline: VerificationBaseline
    ): StepEvaluation {
        Resolution.frameGuard(world)?.let { return it }
        val player = when (val r = Resolution.requirePlayer(spec.actorAt(0), world)) {
            is Resolution.PlayerResolution.Found -> r.player
            is Resolution.PlayerResolution.Missing -> return r.eval
        }
        val reference = baseline.referencePose ?: return Resolution.unsure("No pose to copy yet")
        val current = PoseMath.poseFeatureVector(player.landmarks)
        val distance = PoseMath.poseDistance(current, reference)
        if (distance == Float.MAX_VALUE) return Resolution.unsure("Turn so I can see your whole body")
        val tolerance = step.p("tolerance", 0.4f)
        val satisfied = distance <= tolerance
        val ev = listOf(evidence("poseDistance", distance, tolerance, "<=", satisfied))
        return if (satisfied) Resolution.pass(player.confidence, ev)
        else Resolution.fail("Match the pose more closely", ev)
    }
}

/**
 * JOINT_ANGLE_WITHIN (Architecture §4.1). actors[0] = player; `joint` = index into
 * [PoseMath.ANGLE_NAMES], `target` = target angle (radians), `tolerance` default 0.35 rad. A joint
 * below the visibility floor → Unsure.
 */
internal class JointAngleWithinVerifier : Verifier {
    override val rule = RuleId.JOINT_ANGLE_WITHIN
    override fun evaluate(
        step: VerificationStep, spec: ChallengeSpec, world: WorldState, baseline: VerificationBaseline
    ): StepEvaluation {
        Resolution.frameGuard(world)?.let { return it }
        val player = when (val r = Resolution.requirePlayer(spec.actorAt(0), world)) {
            is Resolution.PlayerResolution.Found -> r.player
            is Resolution.PlayerResolution.Missing -> return r.eval
        }
        val jointIndex = step.p("joint", -1f).toInt()
        if (jointIndex !in PoseMath.ANGLE_NAMES.indices) return Resolution.unsure("No joint to check")
        val vector = PoseMath.poseFeatureVector(player.landmarks)
        if (vector.visibilities[jointIndex] < VISIBILITY_FLOOR)
            return Resolution.unsure("Turn so I can see your ${PoseMath.ANGLE_NAMES[jointIndex]}")
        val target = step.p("target", 0f)
        val tolerance = step.p("tolerance", 0.35f)
        val measured = vector.angles[jointIndex]
        val satisfied = kotlin.math.abs(measured - target) <= tolerance
        val ev = listOf(evidence(PoseMath.ANGLE_NAMES[jointIndex], measured, target, "~=", satisfied))
        return if (satisfied) Resolution.pass(player.confidence, ev)
        else Resolution.fail("Adjust your ${PoseMath.ANGLE_NAMES[jointIndex]}", ev)
    }
}

/**
 * LIMB_RAISED (Architecture §4.1). actors[0] = player; `limb` 0 = left arm, 1 = right arm (default
 * 0); `margin` default 0.05. The wrist must be above the shoulder (smaller y) by at least `margin`.
 * Low-visibility wrist/shoulder → Unsure.
 */
internal class LimbRaisedVerifier : Verifier {
    override val rule = RuleId.LIMB_RAISED
    override fun evaluate(
        step: VerificationStep, spec: ChallengeSpec, world: WorldState, baseline: VerificationBaseline
    ): StepEvaluation {
        Resolution.frameGuard(world)?.let { return it }
        val player = when (val r = Resolution.requirePlayer(spec.actorAt(0), world)) {
            is Resolution.PlayerResolution.Found -> r.player
            is Resolution.PlayerResolution.Missing -> return r.eval
        }
        val rightArm = step.p("limb", 0f).toInt() == 1
        val wristJoint = if (rightArm) PoseJoint.RIGHT_WRIST else PoseJoint.LEFT_WRIST
        val shoulderJoint = if (rightArm) PoseJoint.RIGHT_SHOULDER else PoseJoint.LEFT_SHOULDER
        val wrist = landmark(player, wristJoint.index) ?: return Resolution.unsure("Raise your hand where I can see it")
        val shoulder = landmark(player, shoulderJoint.index) ?: return Resolution.unsure("Turn so I can see your shoulder")
        if (wrist.visibility < VISIBILITY_FLOOR || shoulder.visibility < VISIBILITY_FLOOR)
            return Resolution.unsure("Raise your hand where I can see it")
        val margin = step.p("margin", 0.05f)
        val lift = shoulder.point.y - wrist.point.y   // >0 when wrist is above shoulder (y grows down)
        val satisfied = lift > margin
        val ev = listOf(evidence("lift", lift, margin, ">", satisfied))
        return if (satisfied) Resolution.pass(player.confidence, ev)
        else Resolution.fail("Raise your hand higher", ev)
    }

    private fun landmark(player: TrackedPlayer, index: Int): Landmark? = player.landmarks.getOrNull(index)
}

private const val VISIBILITY_FLOOR = 0.5f
