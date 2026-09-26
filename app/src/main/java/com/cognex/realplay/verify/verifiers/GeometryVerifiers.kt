package com.cognex.realplay.verify.verifiers

import com.cognex.realplay.challenge.ChallengeSpec
import com.cognex.realplay.challenge.VerificationStep
import com.cognex.realplay.verify.ReferenceObject
import com.cognex.realplay.verify.Resolution
import com.cognex.realplay.verify.RuleId
import com.cognex.realplay.verify.StepEvaluation
import com.cognex.realplay.verify.VerificationBaseline
import com.cognex.realplay.verify.VerifyGeometry
import com.cognex.realplay.verify.Verifier
import com.cognex.realplay.verify.actorAt
import com.cognex.realplay.verify.distanceTo
import com.cognex.realplay.verify.evidence
import com.cognex.realplay.verify.p
import com.cognex.realplay.world.NormPoint
import com.cognex.realplay.world.TrackedObject
import com.cognex.realplay.world.WorldState
import kotlin.math.min

/**
 * NON_DEGENERATE_TRIANGLE (Architecture §4.1). actors[0..2] = three objects. Passes only when the
 * three centres form a "real" triangle: enough area, a large-enough smallest angle, and a bounded
 * longest/shortest side ratio. Collinear points (area ≈ 0, tiny min angle) FAIL — the whole point
 * of the rule. Camera-space only (§7.1: 2D unless a planar surface is calibrated).
 */
internal class NonDegenerateTriangleVerifier : Verifier {
    override val rule = RuleId.NON_DEGENERATE_TRIANGLE
    override fun evaluate(
        step: VerificationStep, spec: ChallengeSpec, world: WorldState, baseline: VerificationBaseline
    ): StepEvaluation {
        Resolution.frameGuard(world)?.let { return it }
        val objs = arrayOfNulls<TrackedObject>(3)
        var conf = 1f
        for (i in 0..2) {
            when (val r = Resolution.requireObject(spec.actorAt(i), world, "corner ${i + 1}")) {
                is Resolution.ObjectResolution.Found -> { objs[i] = r.obj; conf = min(conf, r.obj.confidence) }
                is Resolution.ObjectResolution.Missing -> return r.eval
            }
        }
        val m = VerifyGeometry.triangleMetrics(objs[0]!!.center, objs[1]!!.center, objs[2]!!.center)
        val minArea = step.p("minArea", 0.02f)
        val minAngle = step.p("minAngle", 20f)
        val maxRatio = step.p("maxRatio", 4f)
        val areaOk = m.area >= minArea
        val angleOk = m.minAngleDeg >= minAngle
        val ratioOk = m.sideRatio <= maxRatio
        val satisfied = areaOk && angleOk && ratioOk
        val ev = listOf(
            evidence("area", m.area, minArea, ">=", areaOk),
            evidence("minAngleDeg", m.minAngleDeg, minAngle, ">=", angleOk),
            evidence("sideRatio", m.sideRatio, maxRatio, "<=", ratioOk)
        )
        return if (satisfied) Resolution.pass(conf, ev)
        else Resolution.fail("Spread them into a fuller triangle", ev)
    }
}

/**
 * ARRANGEMENT_MATCH (Architecture §4.1). actors = the objects in the arrangement;
 * [VerificationBaseline.referenceObjects] holds the captured target. Both the current and reference
 * point sets are centroid-subtracted and RMS-normalised, so the match is invariant to translation
 * and uniform scale (a 2× larger, shifted copy still matches). The worst per-object displacement
 * must stay within `tolerance` (default 0.25). Fewer than two matchable objects → Unsure.
 */
internal class ArrangementMatchVerifier : Verifier {
    override val rule = RuleId.ARRANGEMENT_MATCH
    override fun evaluate(
        step: VerificationStep, spec: ChallengeSpec, world: WorldState, baseline: VerificationBaseline
    ): StepEvaluation {
        Resolution.frameGuard(world)?.let { return it }
        val reference = baseline.referenceObjects
        if (reference.size < 2) return Resolution.unsure("No arrangement to match yet")

        val currentPts = ArrayList<NormPoint>(reference.size)
        val referencePts = ArrayList<NormPoint>(reference.size)
        for (ref in reference) {
            val obj = matchReference(ref, world) ?: return Resolution.unsure("Show me all the pieces")
            currentPts.add(obj.center)
            referencePts.add(ref.center)
        }

        val normCurrent = VerifyGeometry.normalizeArrangement(currentPts)
        val normReference = VerifyGeometry.normalizeArrangement(referencePts)
        var worst = 0f
        for (i in normCurrent.indices) {
            val d = normCurrent[i].distanceTo(normReference[i])
            if (d > worst) worst = d
        }
        val tolerance = step.p("tolerance", 0.25f)
        val satisfied = worst <= tolerance
        val ev = listOf(evidence("maxDisplacement", worst, tolerance, "<=", satisfied))
        return if (satisfied) Resolution.pass(1f, ev)
        else Resolution.fail("Match the target arrangement more closely", ev)
    }

    private fun matchReference(ref: ReferenceObject, world: WorldState): TrackedObject? {
        val trackPrefix = "track:"
        return if (ref.key.startsWith(trackPrefix)) {
            val id = ref.key.removePrefix(trackPrefix).toIntOrNull() ?: return null
            world.objects.firstOrNull { it.trackId == id }
        } else {
            world.objects
                .filter { it.label.equals(ref.key, ignoreCase = true) && it.confidence >= Resolution.MIN_ACTOR_CONFIDENCE }
                .maxByOrNull { it.confidence }
        }
    }
}
