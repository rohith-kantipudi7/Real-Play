package com.cognex.realplay.verify.verifiers

import com.cognex.realplay.challenge.ChallengeSpec
import com.cognex.realplay.challenge.VerificationStep
import com.cognex.realplay.verify.PoseJoint
import com.cognex.realplay.verify.Resolution
import com.cognex.realplay.verify.RuleId
import com.cognex.realplay.verify.StepEvaluation
import com.cognex.realplay.verify.VerificationBaseline
import com.cognex.realplay.verify.Verifier
import com.cognex.realplay.verify.actorAt
import com.cognex.realplay.verify.distanceTo
import com.cognex.realplay.verify.evidence
import com.cognex.realplay.verify.p
import com.cognex.realplay.world.SpatialRelations
import com.cognex.realplay.world.WorldState
import kotlin.math.min

/**
 * PLAYER_NEAR_OBJECT (Architecture §4.1). actors[0] = player, actors[1] = object. The player's
 * torso centre must be within `threshold` (default 0.25) of the object centre.
 */
internal class PlayerNearObjectVerifier : Verifier {
    override val rule = RuleId.PLAYER_NEAR_OBJECT
    override fun evaluate(
        step: VerificationStep, spec: ChallengeSpec, world: WorldState, baseline: VerificationBaseline
    ): StepEvaluation {
        Resolution.frameGuard(world)?.let { return it }
        val player = when (val r = Resolution.requirePlayer(spec.actorAt(0), world)) {
            is Resolution.PlayerResolution.Found -> r.player
            is Resolution.PlayerResolution.Missing -> return r.eval
        }
        val obj = when (val r = Resolution.requireObject(spec.actorAt(1), world, "object")) {
            is Resolution.ObjectResolution.Found -> r.obj
            is Resolution.ObjectResolution.Missing -> return r.eval
        }
        val threshold = step.p("threshold", 0.25f)
        val distance = player.torsoBox.center.distanceTo(obj.center)
        val satisfied = distance < threshold
        val conf = min(player.confidence, obj.confidence)
        val ev = listOf(evidence("distance", distance, threshold, "<", satisfied))
        return if (satisfied) Resolution.pass(conf, ev)
        else Resolution.fail("Get closer to it", ev)
    }
}

/**
 * PLAYER_HOLDS_OBJECT (Architecture §4.1). actors[0] = player, actors[1] = object. The object must
 * be within `threshold` (default 0.12) of at least one visible wrist. No visible wrist → Unsure.
 */
internal class PlayerHoldsObjectVerifier : Verifier {
    override val rule = RuleId.PLAYER_HOLDS_OBJECT
    override fun evaluate(
        step: VerificationStep, spec: ChallengeSpec, world: WorldState, baseline: VerificationBaseline
    ): StepEvaluation {
        Resolution.frameGuard(world)?.let { return it }
        val player = when (val r = Resolution.requirePlayer(spec.actorAt(0), world)) {
            is Resolution.PlayerResolution.Found -> r.player
            is Resolution.PlayerResolution.Missing -> return r.eval
        }
        val obj = when (val r = Resolution.requireObject(spec.actorAt(1), world, "object")) {
            is Resolution.ObjectResolution.Found -> r.obj
            is Resolution.ObjectResolution.Missing -> return r.eval
        }
        val wrists = listOf(PoseJoint.LEFT_WRIST.index, PoseJoint.RIGHT_WRIST.index)
            .mapNotNull { player.landmarks.getOrNull(it) }
            .filter { it.visibility >= VISIBILITY_FLOOR }
        if (wrists.isEmpty()) return Resolution.unsure("Hold it where I can see your hands")
        val threshold = step.p("threshold", 0.12f)
        val distance = wrists.minOf { it.point.distanceTo(obj.center) }
        val satisfied = distance < threshold
        val conf = min(player.confidence, obj.confidence)
        val ev = listOf(evidence("handDistance", distance, threshold, "<", satisfied))
        return if (satisfied) Resolution.pass(conf, ev)
        else Resolution.fail("Pick it up", ev)
    }
}

/**
 * PLAYER_IN_ZONE (Architecture §4.1). actors[0] = player, actors[1] = zone. The player's torso
 * centre must lie inside the zone polygon.
 */
internal class PlayerInZoneVerifier : Verifier {
    override val rule = RuleId.PLAYER_IN_ZONE
    override fun evaluate(
        step: VerificationStep, spec: ChallengeSpec, world: WorldState, baseline: VerificationBaseline
    ): StepEvaluation {
        Resolution.frameGuard(world)?.let { return it }
        val player = when (val r = Resolution.requirePlayer(spec.actorAt(0), world)) {
            is Resolution.PlayerResolution.Found -> r.player
            is Resolution.PlayerResolution.Missing -> return r.eval
        }
        val zone = when (val r = Resolution.requireZone(spec.actorAt(1), world)) {
            is Resolution.ZoneResolution.Found -> r.zone
            is Resolution.ZoneResolution.Missing -> return r.eval
        }
        val inside = SpatialRelations.pointInPolygon(player.torsoBox.center, zone.polygon)
        val ev = listOf(evidence("inZone", if (inside) 1f else 0f, 1f, "==", inside))
        return if (inside) Resolution.pass(player.confidence, ev)
        else Resolution.fail("Step into the area", ev)
    }
}

private const val VISIBILITY_FLOOR = 0.5f
