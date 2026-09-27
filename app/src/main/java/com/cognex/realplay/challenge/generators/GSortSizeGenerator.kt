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
import kotlin.random.Random

/**
 * G10 · Sort by size (demo game library). Pure JVM.
 *
 * "Line them up from smallest to biggest." A single `SIZE_ORDER` step over three movable objects:
 * the verifier sorts them left→right by centre-x and checks each adjacent box area strictly follows
 * the asked direction. The direction (ascending / descending) is randomised per round from the seed
 * so replays vary. A Pro-tier ordering game — never chains.
 */
class GSortSizeGenerator : ChallengeGenerator {

    override val type = ChallengeType.SORT_SIZE
    override val id = "G10"
    override val proven = true
    override val requires = Requirement(minMovable = 3)

    /** 0.5 + 0.3·spread (§7) — three movable objects with room to reorder. */
    override fun feasibility(cap: SceneCapability, ctx: GenerationContext): Float {
        if (cap.movableCount < 3) return 0f
        return (0.5f + 0.3f * cap.spread.coerceIn(0f, 1f)).coerceIn(0f, 1f)
    }

    override fun maxStepsForTier(tier: Tier): Int = 1

    /** Offered to OLDER (Pro) only (§10 / audience control) — the hardest ordering game. */
    override fun ageGate(band: AgeBand): Float = if (band == AgeBand.OLDER) 1f else 0f

    override fun generate(
        world: WorldState,
        aff: List<Affordance>,
        ctx: GenerationContext,
        stepBudget: Int
    ): ChallengeSpec {
        val affById = aff.associateBy { it.trackId }
        val pieces = pickPieces(world, affById)
        val ascending = Random(ctx.seed).nextBoolean()
        val direction = if (ascending) 1f else -1f
        val phrase = if (ascending) "smallest to biggest" else "biggest to smallest"
        return ChallengeSpec(
            id = "G10-${if (ascending) "up" else "down"}-${pieces.joinToString("-") { it.trackId.toString() }}",
            type = type,
            tier = ctx.effectiveTier,
            ageBand = ctx.ageBand,
            actors = pieces.map { ActorRef.ByTrackId(it.trackId) },
            instruction = "Line them up $phrase, left to right.",
            steps = listOf(
                VerificationStep(
                    rule = RuleId.SIZE_ORDER,
                    params = mapOf("direction" to direction, "margin" to 0.002f),
                    holdMs = ctx.knobs.holdMs
                )
            ),
            timeLimitMs = ctx.knobs.timeLimitMs,
            baseScore = 60,
            hints = listOf("Compare their sizes", "Order them $phrase", "Left to right")
        )
    }

    /** Three movable objects with the most size spread first; always ≥3 (feasibility guaranteed). */
    private fun pickPieces(world: WorldState, affById: Map<Int, Affordance>): List<TrackedObject> {
        val movable = world.objects.filter { affById[it.trackId]?.movable == true }
        val pool = if (movable.size >= 3) movable else world.objects
        return pool.sortedByDescending { it.box.area }.take(3)
    }
}
