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
import com.cognex.realplay.world.ColorTag
import com.cognex.realplay.world.SceneCapability
import com.cognex.realplay.world.TrackedObject
import com.cognex.realplay.world.WorldState

/**
 * G11 · Group by colour (demo game library). Pure JVM.
 *
 * "Put all the red things together, away from the rest." Finds the most common chromatic colour with
 * at least two objects, and makes those the group of one `GROUP_CLUSTERED` step — the verifier
 * passes when the group is drawn tight AND kept clear of every other object. The cluster/separation
 * thresholds tighten with tier. A Kids-and-up game; never chains.
 */
class GGroupColourGenerator : ChallengeGenerator {

    override val type = ChallengeType.GROUP_COLOR
    override val id = "G11"
    override val proven = true
    override val requires = Requirement(minMovable = 2, minDistinctColors = 2)

    /** 0.5 + 0.1·colours, capped 0.85 (§7) — needs a colour group plus something to separate from. */
    override fun feasibility(cap: SceneCapability, ctx: GenerationContext): Float {
        if (cap.movableCount < 2 || cap.distinctColors.size < 2) return 0f
        return (0.5f + 0.1f * cap.distinctColors.size).coerceIn(0f, 0.85f)
    }

    override fun maxStepsForTier(tier: Tier): Int = 1

    /** Offered from EARLY upward (§10) — not a toddler basic (grouping needs spatial reasoning). */
    override fun ageGate(band: AgeBand): Float = if (band == AgeBand.TODDLER) 0f else 1f

    override fun generate(
        world: WorldState,
        aff: List<Affordance>,
        ctx: GenerationContext,
        stepBudget: Int
    ): ChallengeSpec {
        val group = pickColourGroup(world)
        val color = group.firstOrNull()?.color ?: ColorTag.UNKNOWN
        val word = if (color == ColorTag.UNKNOWN) "matching" else color.name.lowercase()
        val (clusterRadius, separation) = thresholds(ctx.effectiveTier)
        return ChallengeSpec(
            id = "G11-${color.name}-${group.joinToString("-") { it.trackId.toString() }}",
            type = type,
            tier = ctx.effectiveTier,
            ageBand = ctx.ageBand,
            actors = group.map { ActorRef.ByTrackId(it.trackId) },
            instruction = "Put all the $word things together, away from the rest.",
            steps = listOf(
                VerificationStep(
                    rule = RuleId.GROUP_CLUSTERED,
                    params = mapOf("clusterRadius" to clusterRadius, "separation" to separation),
                    holdMs = ctx.knobs.holdMs
                )
            ),
            timeLimitMs = ctx.knobs.timeLimitMs,
            baseScore = 45,
            hints = listOf("Gather the $word ones", "Keep the others away")
        )
    }

    /** The largest same-chromatic-colour group (≥2); falls back to any two objects to stay total. */
    private fun pickColourGroup(world: WorldState): List<TrackedObject> {
        val byColor = world.objects
            .filter { it.color != null && it.color != ColorTag.UNKNOWN }
            .groupBy { it.color }
        val best = byColor.values.filter { it.size >= 2 }.maxByOrNull { it.size }
        return best ?: world.objects.take(2)
    }

    private fun thresholds(tier: Tier): Pair<Float, Float> = when (tier) {
        Tier.EASY -> 0.40f to 0.20f
        Tier.MEDIUM -> 0.36f to 0.24f
        Tier.HARD -> 0.32f to 0.28f
    }
}
