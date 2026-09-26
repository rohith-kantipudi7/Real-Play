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
import com.cognex.realplay.world.TrackedPlayer
import com.cognex.realplay.world.WorldState
import kotlin.random.Random

/**
 * G4 · Statue Match (Architecture §7, S8 prompt step 6). Pure JVM.
 *
 * Asks the active player to strike one of [PoseLibrary]'s eight target poses and hold it. Each step
 * is a `POSE_MATCH` whose target is carried per-step in the `poseId` param (read by the
 * baseline provider) and whose tolerance comes from the tier's `poseToleranceDeg` knob.
 *
 * STRUCTURAL axis (§8.1b): [maxStepsForTier] returns 2 at HARD — two poses in sequence, the second
 * `mustFollowPreviousStep` so it is only counted after the first fires. TODDLER/EARLY are still
 * hard-capped to one step by the registry (§20 invariant 17), so the two-pose form only appears for
 * MIDDLE/OLDER at HARD.
 */
class G4StatueMatchGenerator : ChallengeGenerator {

    override val type = ChallengeType.STATUE_MATCH
    override val id = "G4"
    override val proven = true
    override val requires = Requirement(minPlayers = 1)

    /** Flat 0.85 (§7) — a reliable human-only game whenever a player is present. */
    override fun feasibility(cap: SceneCapability, ctx: GenerationContext): Float = 0.85f

    /** Chains to 2 at HARD — two poses held in sequence (§8.1b); single pose otherwise. */
    override fun maxStepsForTier(tier: Tier): Int = if (tier == Tier.HARD) 2 else 1

    /** Offered from EARLY upward when pose is on (§10); never a toddler game. */
    override fun ageGate(band: AgeBand): Float = if (band == AgeBand.TODDLER) 0f else 1f

    override fun generate(
        world: WorldState,
        aff: List<Affordance>,
        ctx: GenerationContext,
        stepBudget: Int
    ): ChallengeSpec {
        val player = activePlayer(world)
        val toleranceRad = Math.toRadians(ctx.knobs.poseToleranceDeg.toDouble()).toFloat()

        val rng = Random(ctx.seed)
        val count = stepBudget.coerceIn(1, 2)
        val chosen = pickDistinctPoses(rng, count)

        val steps = chosen.mapIndexed { i, poseId ->
            VerificationStep(
                rule = RuleId.POSE_MATCH,
                params = mapOf("poseId" to poseId.toFloat(), "tolerance" to toleranceRad),
                holdMs = ctx.knobs.holdMs,
                mustFollowPreviousStep = i > 0
            )
        }

        val names = chosen.map { PoseLibrary.byId(it).name }
        val instruction = if (names.size == 1) {
            "Strike this pose: ${names[0]}! Hold it still."
        } else {
            "Strike the ${names[0]} pose, then the ${names[1]} pose!"
        }

        return ChallengeSpec(
            id = "G4-${chosen.joinToString("-")}",
            type = type,
            tier = ctx.effectiveTier,
            ageBand = ctx.ageBand,
            actors = listOf(ActorRef.ByPlayer(player?.playerId ?: 0)),
            instruction = instruction,
            steps = steps,
            timeLimitMs = ctx.knobs.timeLimitMs,
            baseScore = 40,
            hints = listOf("Match the silhouette beside the picture", "Hold really still like a statue")
        )
    }

    /** Highest-confidence, unambiguous player, or null when none is yet confirmed. */
    private fun activePlayer(world: WorldState): TrackedPlayer? =
        world.players.filter { !it.ambiguous }.maxByOrNull { it.confidence }

    /** Picks [count] distinct pose ids deterministically from [rng]. */
    private fun pickDistinctPoses(rng: Random, count: Int): List<Int> {
        val ids = PoseLibrary.poses.map { it.id }.toMutableList()
        ids.shuffle(rng)
        return ids.take(count)
    }
}
