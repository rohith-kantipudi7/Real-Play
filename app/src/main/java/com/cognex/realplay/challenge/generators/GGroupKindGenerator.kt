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
import com.cognex.realplay.verify.RuleId
import com.cognex.realplay.world.Affordance
import com.cognex.realplay.world.SceneCapability
import com.cognex.realplay.world.TrackedObject
import com.cognex.realplay.world.WorldState

/**
 * G12 · Group by kind (demo game library). Pure JVM.
 *
 * "Put all the cups together, away from the rest." The same mechanic as G11 but grouped by object
 * LABEL instead of colour: it finds the most common nameable kind with at least two instances and
 * makes those the group of one `GROUP_CLUSTERED` step. Kept as a separate game so the demo can test
 * both grouping cues on the real kit and keep whichever detects better (§4 notes). Never chains.
 */
class GGroupKindGenerator : ChallengeGenerator {

    override val type = ChallengeType.GROUP_KIND
    override val id = "G12"
    override val proven = true
    override val requires = Requirement(minMovable = 2, needsNameable = true)

    /** 0.6 when a kind with ≥2 instances exists, else scored out at generate/selection time. */
    override fun feasibility(cap: SceneCapability, ctx: GenerationContext): Float =
        if (cap.movableCount < 2 || cap.nameableCount < 2) 0f else 0.6f

    override fun maxStepsForTier(tier: Tier): Int = 1

    /** Offered from EARLY upward (§10) — not a toddler basic. */
    override fun ageGate(band: AgeBand): Float = if (band == AgeBand.TODDLER) 0f else 1f

    override fun generate(
        world: WorldState,
        aff: List<Affordance>,
        ctx: GenerationContext,
        stepBudget: Int
    ): ChallengeSpec {
        val group = pickKindGroup(world)
        val kind = group.firstOrNull()?.label?.takeIf { it.isNotBlank() } ?: "matching"
        val (clusterRadius, separation) = thresholds(ctx.effectiveTier)
        return ChallengeSpec(
            id = "G12-$kind-${group.joinToString("-") { it.trackId.toString() }}",
            type = type,
            tier = ctx.effectiveTier,
            ageBand = ctx.ageBand,
            actors = group.map { ActorRef.ByTrackId(it.trackId) },
            instruction = "Put all the ${kind}s together, away from the rest.",
            steps = listOf(
                VerificationStep(
                    rule = RuleId.GROUP_CLUSTERED,
                    params = mapOf("clusterRadius" to clusterRadius, "separation" to separation),
                    holdMs = ctx.knobs.holdMs
                )
            ),
            timeLimitMs = ctx.knobs.timeLimitMs,
            baseScore = 45,
            hints = listOf("Gather the ${kind}s", "Keep the others away")
        )
    }

    /** The largest same-label group (≥2); falls back to any two objects to stay total. */
    private fun pickKindGroup(world: WorldState): List<TrackedObject> {
        val byLabel = world.objects.filter { it.label.isNotBlank() }.groupBy { it.label.lowercase() }
        val best = byLabel.values.filter { it.size >= 2 }.maxByOrNull { it.size }
        return best ?: world.objects.take(2)
    }

    private fun thresholds(tier: Tier): Pair<Float, Float> = when (tier) {
        Tier.EASY -> 0.32f to 0.26f
        Tier.MEDIUM -> 0.28f to 0.3f
        Tier.HARD -> 0.24f to 0.34f
    }
}
