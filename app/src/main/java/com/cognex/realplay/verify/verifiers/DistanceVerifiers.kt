package com.cognex.realplay.verify.verifiers

import com.cognex.realplay.challenge.ChallengeSpec
import com.cognex.realplay.challenge.VerificationStep
import com.cognex.realplay.verify.Evidence
import com.cognex.realplay.verify.Resolution
import com.cognex.realplay.verify.RuleId
import com.cognex.realplay.verify.StepEvaluation
import com.cognex.realplay.verify.VerificationBaseline
import com.cognex.realplay.verify.Verifier
import com.cognex.realplay.verify.actorAt
import com.cognex.realplay.verify.distanceTo
import com.cognex.realplay.verify.evidence
import com.cognex.realplay.verify.p
import com.cognex.realplay.world.WorldState
import kotlin.math.min

/**
 * Distance verifiers (Architecture §4.1). actors[0] = subject, actors[1] = reference; both
 * objects. `threshold` is the NORMALIZED distance to compare against.
 */
internal abstract class DistanceVerifier(final override val rule: RuleId) : Verifier {
    final override fun evaluate(
        step: VerificationStep, spec: ChallengeSpec, world: WorldState, baseline: VerificationBaseline
    ): StepEvaluation {
        Resolution.frameGuard(world)?.let { return it }
        val a = when (val r = Resolution.requireObject(spec.actorAt(0), world, "first object")) {
            is Resolution.ObjectResolution.Found -> r.obj
            is Resolution.ObjectResolution.Missing -> return r.eval
        }
        val b = when (val r = Resolution.requireObject(spec.actorAt(1), world, "second object")) {
            is Resolution.ObjectResolution.Found -> r.obj
            is Resolution.ObjectResolution.Missing -> return r.eval
        }
        val threshold = step.p("threshold", 0.2f)
        val measured = a.center.distanceTo(b.center)
        val satisfied = decide(measured, threshold)
        val conf = min(a.confidence, b.confidence)
        val ev = listOf(evidence("distance", measured, threshold, comparator, satisfied))
        return if (satisfied) Resolution.pass(conf, ev)
        else Resolution.fail(failReason(measured, threshold), ev)
    }

    protected abstract val comparator: String
    protected abstract fun decide(measured: Float, threshold: Float): Boolean
    protected abstract fun failReason(measured: Float, threshold: Float): String
}

internal class DistanceLessThanVerifier : DistanceVerifier(RuleId.DISTANCE_LESS_THAN) {
    override val comparator = "<"
    override fun decide(measured: Float, threshold: Float) = measured < threshold
    override fun failReason(measured: Float, threshold: Float) =
        "Still ${fmt(measured)} apart — get them under ${fmt(threshold)}"
}

internal class DistanceGreaterThanVerifier : DistanceVerifier(RuleId.DISTANCE_GREATER_THAN) {
    override val comparator = ">"
    override fun decide(measured: Float, threshold: Float) = measured > threshold
    override fun failReason(measured: Float, threshold: Float) =
        "Only ${fmt(measured)} apart — move them past ${fmt(threshold)}"
}

private fun fmt(v: Float) = String.format("%.2f", v)
