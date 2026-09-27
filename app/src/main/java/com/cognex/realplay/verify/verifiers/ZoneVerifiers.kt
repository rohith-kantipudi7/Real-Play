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
import com.cognex.realplay.world.SpatialRelations
import com.cognex.realplay.world.WorldState
import kotlin.math.min

/**
 * POINT_IN_ZONE (Architecture §4.1). actors[0] = object, actors[1] = zone. The object's centre
 * must lie inside the zone polygon.
 */
internal class PointInZoneVerifier : Verifier {
    override val rule = RuleId.POINT_IN_ZONE
    override fun evaluate(
        step: VerificationStep, spec: ChallengeSpec, world: WorldState, baseline: VerificationBaseline
    ): StepEvaluation {
        Resolution.frameGuard(world)?.let { return it }
        val obj = when (val r = Resolution.requireObject(spec.actorAt(0), world, "object")) {
            is Resolution.ObjectResolution.Found -> r.obj
            is Resolution.ObjectResolution.Missing -> return r.eval
        }
        val zone = when (val r = Resolution.requireZone(spec.actorAt(1), world)) {
            is Resolution.ZoneResolution.Found -> r.zone
            is Resolution.ZoneResolution.Missing -> return r.eval
        }
        val inside = SpatialRelations.pointInPolygon(obj.center, zone.polygon)
        val ev = listOf(evidence("inZone", if (inside) 1f else 0f, 1f, "==", inside))
        return if (inside) Resolution.pass(obj.confidence, ev)
        else Resolution.fail("Move it into the target area", ev)
    }
}

/**
 * OVERLAP_RATIO_ABOVE (Architecture §4.1). actors[0] & actors[1] = objects. Overlap as a fraction
 * of the SMALLER box must exceed `threshold` (default 0.5).
 */
internal class OverlapRatioAboveVerifier : Verifier {
    override val rule = RuleId.OVERLAP_RATIO_ABOVE
    override fun evaluate(
        step: VerificationStep, spec: ChallengeSpec, world: WorldState, baseline: VerificationBaseline
    ): StepEvaluation {
        Resolution.frameGuard(world)?.let { return it }
        val a = when (val r = Resolution.requireObject(spec.actorAt(0), world, "object")) {
            is Resolution.ObjectResolution.Found -> r.obj
            is Resolution.ObjectResolution.Missing -> return r.eval
        }
        val b = when (val r = Resolution.requireObject(spec.actorAt(1), world, "other object")) {
            is Resolution.ObjectResolution.Found -> r.obj
            is Resolution.ObjectResolution.Missing -> return r.eval
        }
        val threshold = step.p("threshold", 0.5f)
        val overlap = SpatialRelations.overlapRatio(a.box, b.box)
        val satisfied = overlap > threshold
        val conf = min(a.confidence, b.confidence)
        val ev = listOf(evidence("overlap", overlap, threshold, ">", satisfied))
        return if (satisfied) Resolution.pass(conf, ev)
        else Resolution.fail("Overlap them more", ev)
    }
}

/**
 * OBJECT_VANISHED_IN_ZONE (Architecture §4.1). actors[0] = target object, actors[1] = zone,
 * actors[2] = container that must still be present (e.g. the box it was dropped into).
 *
 * The confident PASS story:
 *   - the target is NO LONGER detected,
 *   - it was last seen INSIDE the zone (not merely leaving the frame),
 *   - it has been undetected for at least `frames` frames,
 *   - and the container is still present.
 * Any weaker evidence — target still visible, exited at a frame edge, no vanish history, or missing
 * container — is Unsure, never Fail (§20 invariant 2). This rule never Fails: an object we can still
 * see simply hasn't vanished yet.
 */
internal class ObjectVanishedInZoneVerifier : Verifier {
    override val rule = RuleId.OBJECT_VANISHED_IN_ZONE
    override fun evaluate(
        step: VerificationStep, spec: ChallengeSpec, world: WorldState, baseline: VerificationBaseline
    ): StepEvaluation {
        Resolution.frameGuard(world)?.let { return it }

        // Target must be GONE. If we can still resolve it, the drop hasn't happened yet.
        if (Resolution.resolveObject(spec.actorAt(0), world) != null)
            return Resolution.unsure("Now hide it away")

        val zone = when (val r = Resolution.requireZone(spec.actorAt(1), world)) {
            is Resolution.ZoneResolution.Found -> r.zone
            is Resolution.ZoneResolution.Missing -> return r.eval
        }
        // Container must still be present (guards against "everything left the frame").
        when (val r = Resolution.requireObject(spec.actorAt(2), world, "container")) {
            is Resolution.ObjectResolution.Found -> Unit
            is Resolution.ObjectResolution.Missing -> return r.eval
        }

        val vanished = baseline.vanished ?: return Resolution.unsure("Show me where it went")
        if (vanished.exitedAtEdge) return Resolution.unsure("Keep it in view while you hide it")

        val requiredFrames = step.p("frames", 3f)
        val lastInZone = SpatialRelations.pointInPolygon(vanished.lastCenter, zone.polygon)
        val longEnough = vanished.framesUndetected >= requiredFrames
        val satisfied = lastInZone && longEnough
        val ev = listOf(
            evidence("lastInZone", if (lastInZone) 1f else 0f, 1f, "==", lastInZone),
            evidence("framesGone", vanished.framesUndetected.toFloat(), requiredFrames, ">=", longEnough)
        )
        return if (satisfied) Resolution.pass(1f, ev) else Resolution.unsure("Make sure it's hidden in the box")
    }
}
