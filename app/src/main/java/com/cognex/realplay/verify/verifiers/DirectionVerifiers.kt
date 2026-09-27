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
import com.cognex.realplay.world.TrackedObject
import com.cognex.realplay.world.WorldState
import kotlin.math.min

/**
 * Direction verifiers (Architecture §4.1). actors[0] = subject, actors[1] = reference; both
 * objects. Image convention: x grows right, y grows DOWN. The signed centre separation along the
 * relevant axis must EXCEED `tolerance` (default 0.02) — brushing past counts as Unsure-ish Fail,
 * not a lucky Pass, and a hard boundary can never sneak through.
 */
internal abstract class DirectionVerifier(final override val rule: RuleId) : Verifier {
    final override fun evaluate(
        step: VerificationStep, spec: ChallengeSpec, world: WorldState, baseline: VerificationBaseline
    ): StepEvaluation {
        Resolution.frameGuard(world)?.let { return it }
        val subject = when (val r = Resolution.requireObject(spec.actorAt(0), world, "object")) {
            is Resolution.ObjectResolution.Found -> r.obj
            is Resolution.ObjectResolution.Missing -> return r.eval
        }
        val reference = when (val r = Resolution.requireObject(spec.actorAt(1), world, "other object")) {
            is Resolution.ObjectResolution.Found -> r.obj
            is Resolution.ObjectResolution.Missing -> return r.eval
        }
        val tolerance = step.p("tolerance", 0.02f)
        val separation = signedSeparation(subject, reference)
        val satisfied = separation > tolerance
        val conf = min(subject.confidence, reference.confidence)
        val ev = listOf(evidence(label, separation, tolerance, ">", satisfied))
        return if (satisfied) Resolution.pass(conf, ev)
        else Resolution.fail(failHint, ev)
    }

    /** Positive when subject is on the correct side of reference by [signedSeparation] units. */
    protected abstract fun signedSeparation(subject: TrackedObject, reference: TrackedObject): Float
    protected abstract val label: String
    protected abstract val failHint: String
}

internal class LeftOfVerifier : DirectionVerifier(RuleId.LEFT_OF) {
    override fun signedSeparation(subject: TrackedObject, reference: TrackedObject) =
        reference.center.x - subject.center.x
    override val label = "leftGap"
    override val failHint = "Move it further to the left"
}

internal class RightOfVerifier : DirectionVerifier(RuleId.RIGHT_OF) {
    override fun signedSeparation(subject: TrackedObject, reference: TrackedObject) =
        subject.center.x - reference.center.x
    override val label = "rightGap"
    override val failHint = "Move it further to the right"
}

internal class AboveVerifier : DirectionVerifier(RuleId.ABOVE) {
    // "above" = smaller y, so subject.y must be below reference.y by the tolerance.
    override fun signedSeparation(subject: TrackedObject, reference: TrackedObject) =
        reference.center.y - subject.center.y
    override val label = "aboveGap"
    override val failHint = "Lift it higher"
}

internal class BelowVerifier : DirectionVerifier(RuleId.BELOW) {
    override fun signedSeparation(subject: TrackedObject, reference: TrackedObject) =
        subject.center.y - reference.center.y
    override val label = "belowGap"
    override val failHint = "Move it lower"
}
