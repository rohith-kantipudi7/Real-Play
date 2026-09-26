package com.cognex.realplay.verify.verifiers

import com.cognex.realplay.challenge.ActorRef
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
import com.cognex.realplay.world.ColorTag
import com.cognex.realplay.world.WorldState
import kotlin.math.abs

/**
 * OBJECT_PRESENT (Architecture §4.1). actors[0] = object. Pass when the object is resolvable with
 * enough confidence; missing/ambiguous/low-confidence → Unsure (never Fail — absence of evidence
 * is not evidence of absence for this rule).
 *
 * Optional `minArea` param (§6.4 / §7 — "bring it close"): when > 0, the object must also fill at
 * least that fraction of the frame, so "bring the glowing object close to the camera" only passes
 * once it is genuinely large. Below the area it FAILS (the object is present but too far), which is
 * confidently-false evidence, not uncertainty.
 */
internal class ObjectPresentVerifier : Verifier {
    override val rule = RuleId.OBJECT_PRESENT
    override fun evaluate(
        step: VerificationStep, spec: ChallengeSpec, world: WorldState, baseline: VerificationBaseline
    ): StepEvaluation {
        Resolution.frameGuard(world)?.let { return it }
        val obj = when (val r = Resolution.requireObject(spec.actorAt(0), world, "object")) {
            is Resolution.ObjectResolution.Found -> r.obj
            is Resolution.ObjectResolution.Missing -> return r.eval
        }
        val minArea = step.p("minArea", 0f)
        if (minArea > 0f) {
            val area = obj.box.area
            val satisfied = area >= minArea
            val ev = listOf(evidence("area", area, minArea, ">=", satisfied))
            return if (satisfied) Resolution.pass(obj.confidence, ev)
            else Resolution.fail("Bring it closer to the camera", ev)
        }
        val ev = listOf(evidence("present", 1f, 1f, "==", true))
        return Resolution.pass(obj.confidence, ev)
    }
}

/**
 * OBJECT_ABSENT (Architecture §4.1). actors[0] = object. Pass when the object is confidently gone
 * on a GOOD frame; still detected → Fail. Uses the raw resolver so a missing object is a PASS (the
 * whole point of this rule), while the frame guard still forces Unsure on a bad frame.
 */
internal class ObjectAbsentVerifier : Verifier {
    override val rule = RuleId.OBJECT_ABSENT
    override fun evaluate(
        step: VerificationStep, spec: ChallengeSpec, world: WorldState, baseline: VerificationBaseline
    ): StepEvaluation {
        Resolution.frameGuard(world)?.let { return it }
        val present = Resolution.resolveObject(spec.actorAt(0), world) != null
        val ev = listOf(evidence("present", if (present) 1f else 0f, 0f, "==", !present))
        return if (!present) Resolution.pass(1f, ev)
        else Resolution.fail("It should be out of view", ev)
    }
}

/**
 * COLOR_MATCH (Architecture §4.1). actors[0] = object; `color` param = target [ColorTag] ordinal.
 * A null/unknown detected colour is insufficient evidence → Unsure.
 */
internal class ColorMatchVerifier : Verifier {
    override val rule = RuleId.COLOR_MATCH
    override fun evaluate(
        step: VerificationStep, spec: ChallengeSpec, world: WorldState, baseline: VerificationBaseline
    ): StepEvaluation {
        Resolution.frameGuard(world)?.let { return it }
        val obj = when (val r = Resolution.requireObject(spec.actorAt(0), world, "object")) {
            is Resolution.ObjectResolution.Found -> r.obj
            is Resolution.ObjectResolution.Missing -> return r.eval
        }
        val targetOrdinal = step.p("color", -1f).toInt()
        val target = ColorTag.entries.getOrNull(targetOrdinal)
            ?: return Resolution.unsure("No target colour set")
        val actual = obj.color
        if (actual == null || actual == ColorTag.UNKNOWN)
            return Resolution.unsure("Bring it into better light so I can see its colour")
        val satisfied = actual == target
        val ev = listOf(evidence("color", actual.ordinal.toFloat(), target.ordinal.toFloat(), "==", satisfied))
        return if (satisfied) Resolution.pass(obj.confidence, ev)
        else Resolution.fail("That's ${actual.name.lowercase()}, find a ${target.name.lowercase()} one", ev)
    }
}

/**
 * SHAPE_MATCH (Architecture §4.1). actors[0] = object; `aspect` = target width/height, `tolerance`
 * default 0.35. A coarse box-aspect proxy for shape (§7.1: full shape templates are deferred).
 */
internal class ShapeMatchVerifier : Verifier {
    override val rule = RuleId.SHAPE_MATCH
    override fun evaluate(
        step: VerificationStep, spec: ChallengeSpec, world: WorldState, baseline: VerificationBaseline
    ): StepEvaluation {
        Resolution.frameGuard(world)?.let { return it }
        val obj = when (val r = Resolution.requireObject(spec.actorAt(0), world, "object")) {
            is Resolution.ObjectResolution.Found -> r.obj
            is Resolution.ObjectResolution.Missing -> return r.eval
        }
        val h = obj.box.height
        if (h <= 0f) return Resolution.unsure("Show me its full shape")
        val aspect = obj.box.width / h
        val target = step.p("aspect", 1f)
        val tolerance = step.p("tolerance", 0.35f)
        val delta = abs(aspect - target)
        val satisfied = delta <= tolerance
        val ev = listOf(evidence("aspect", aspect, target, "~=", satisfied))
        return if (satisfied) Resolution.pass(obj.confidence, ev)
        else Resolution.fail("That's not the right shape", ev)
    }
}

/**
 * COUNT_EQUALS (Architecture §4.1). actors[0] = [ActorRef.ByLabel]; `count` = required number of
 * objects with that label. A non-label actor is a spec error → Unsure.
 */
internal class CountEqualsVerifier : Verifier {
    override val rule = RuleId.COUNT_EQUALS
    override fun evaluate(
        step: VerificationStep, spec: ChallengeSpec, world: WorldState, baseline: VerificationBaseline
    ): StepEvaluation {
        Resolution.frameGuard(world)?.let { return it }
        val ref = spec.actorAt(0) as? ActorRef.ByLabel
            ?: return Resolution.unsure("Nothing to count")
        val required = step.p("count", 0f).toInt()
        val count = world.objects.count {
            it.label.equals(ref.label, ignoreCase = true) &&
                it.confidence >= Resolution.MIN_ACTOR_CONFIDENCE
        }
        val satisfied = count == required
        val ev = listOf(evidence("count", count.toFloat(), required.toFloat(), "==", satisfied))
        return if (satisfied) Resolution.pass(1f, ev)
        else Resolution.fail("I count $count, need $required", ev)
    }
}
