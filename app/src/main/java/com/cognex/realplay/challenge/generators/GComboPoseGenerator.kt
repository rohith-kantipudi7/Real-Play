package com.cognex.realplay.challenge.generators

import com.cognex.realplay.challenge.ActorRef
import com.cognex.realplay.challenge.AgeBand
import com.cognex.realplay.challenge.ChallengeGenerator
import com.cognex.realplay.challenge.ChallengeSpec
import com.cognex.realplay.challenge.ChallengeType
import com.cognex.realplay.challenge.GenerationContext
import com.cognex.realplay.challenge.Requirement
import com.cognex.realplay.challenge.Tier
import com.cognex.realplay.challenge.VerificationStep
import com.cognex.realplay.verify.PoseLibrary
import com.cognex.realplay.verify.RuleId
import com.cognex.realplay.world.Affordance
import com.cognex.realplay.world.SceneCapability
import com.cognex.realplay.world.TrackedObject
import com.cognex.realplay.world.TrackedPlayer
import com.cognex.realplay.world.WorldState
import kotlin.random.Random

/**
 * G13 · Hold & Pose combo (demo game library — the finale). Pure JVM.
 *
 * "Hold the bottle, then strike the star pose!" Chains two existing primitives over one player and
 * one handheld object: `PLAYER_HOLDS_OBJECT` (the object must reach a wrist) then `POSE_MATCH` (the
 * body must match a target pose), the second `mustFollowPreviousStep` so the pose only counts after
 * the grab. The POSE_MATCH target rides in the `poseId` param, resolved to a reference pose by the
 * baseline provider (§4.1, §7) — no engine change. An expert, human+object game; OLDER (Pro) only.
 */
class GComboPoseGenerator : ChallengeGenerator {

    override val type = ChallengeType.COMBO_POSE
    override val id = "G13"
    override val proven = true
    override val requires = Requirement(minPlayers = 1, minHandheld = 1)

    /** Flat 0.8 (§7) — reliable whenever a player and a handheld object are both present. */
    override fun feasibility(cap: SceneCapability, ctx: GenerationContext): Float = 0.8f

    /** Always the two-step grab-then-pose combo — the chaining IS the game. */
    override fun maxStepsForTier(tier: Tier): Int = 2

    /** Offered to OLDER (Pro) only (§10 / audience control) — the expert finale. */
    override fun ageGate(band: AgeBand): Float = if (band == AgeBand.OLDER) 1f else 0f

    override fun generate(
        world: WorldState,
        aff: List<Affordance>,
        ctx: GenerationContext,
        stepBudget: Int
    ): ChallengeSpec {
        val affById = aff.associateBy { it.trackId }
        val player = activePlayer(world)
        val obj = pickHandheld(world, affById)
        val poseId = PoseLibrary.poses.map { it.id }.shuffled(Random(ctx.seed)).first()
        val poseName = PoseLibrary.byId(poseId).name
        val toleranceRad = Math.toRadians(ctx.knobs.poseToleranceDeg.toDouble()).toFloat()
        val objName = obj?.label?.takeIf { it.isNotBlank() } ?: "object"

        return ChallengeSpec(
            id = "G13-${obj?.trackId ?: 0}-$poseId",
            type = type,
            tier = ctx.effectiveTier,
            ageBand = ctx.ageBand,
            actors = listOf(ActorRef.ByPlayer(player?.playerId ?: 0), ActorRef.ByTrackId(obj?.trackId ?: 0)),
            instruction = "Grab the $objName, then strike the $poseName pose!",
            steps = listOf(
                VerificationStep(
                    rule = RuleId.PLAYER_HOLDS_OBJECT,
                    params = emptyMap(),
                    holdMs = ctx.knobs.holdMs,
                    actorIndices = listOf(0, 1)
                ),
                VerificationStep(
                    rule = RuleId.POSE_MATCH,
                    params = mapOf("poseId" to poseId.toFloat(), "tolerance" to toleranceRad),
                    holdMs = ctx.knobs.holdMs,
                    mustFollowPreviousStep = true,
                    actorIndices = listOf(0)
                )
            ),
            timeLimitMs = ctx.knobs.timeLimitMs,
            baseScore = 75,
            hints = listOf("Pick the $objName up first", "Now hold the $poseName pose", "Keep holding it")
        )
    }

    private fun activePlayer(world: WorldState): TrackedPlayer? =
        world.players.filter { !it.ambiguous }.maxByOrNull { it.confidence }

    /** Prefers a handheld, nameable object; falls back to the smallest object, then any. */
    private fun pickHandheld(world: WorldState, affById: Map<Int, Affordance>): TrackedObject? =
        world.objects
            .sortedWith(
                compareByDescending<TrackedObject> { affById[it.trackId]?.handheld == true }
                    .thenByDescending { affById[it.trackId]?.nameable == true }
                    .thenBy { it.box.area }
            )
            .firstOrNull()
}
