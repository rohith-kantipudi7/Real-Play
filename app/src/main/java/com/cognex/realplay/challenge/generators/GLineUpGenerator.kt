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
 * G9 · Line-up (demo game library). Pure JVM.
 *
 * "Put them all in one straight row." A single `COLLINEAR` step over three-or-more movable objects:
 * the verifier fits a line to their centres and passes only when every one sits close to it AND the
 * row is long enough (a tight blob fails). Rotation-invariant, so any straight orientation counts.
 * The perpendicular tolerance tightens with tier. A hard, precise placement game — never chains.
 */
class GLineUpGenerator : ChallengeGenerator {

    override val type = ChallengeType.LINE_UP
    override val id = "G9"
    override val proven = true
    override val requires = Requirement(minMovable = 3)

    /** 0.5 + 0.35·spread (§7) — more separated objects make a cleaner starting row. */
    override fun feasibility(cap: SceneCapability, ctx: GenerationContext): Float {
        if (cap.movableCount < 3) return 0f
        return (0.5f + 0.35f * cap.spread.coerceIn(0f, 1f)).coerceIn(0f, 1f)
    }

    override fun maxStepsForTier(tier: Tier): Int = 1

    /** Offered from MIDDLE upward (§10) — a precise placement game, not for toddlers/early. */
    override fun ageGate(band: AgeBand): Float =
        if (band == AgeBand.MIDDLE || band == AgeBand.OLDER) 1f else 0f

    override fun generate(
        world: WorldState,
        aff: List<Affordance>,
        ctx: GenerationContext,
        stepBudget: Int
    ): ChallengeSpec {
        val affById = aff.associateBy { it.trackId }
        val pieces = pickPieces(world, affById)
        val tolerance = when (ctx.effectiveTier) {
            Tier.EASY -> 0.12f
            Tier.MEDIUM -> 0.10f
            Tier.HARD -> 0.08f
        }
        return ChallengeSpec(
            id = "G9-${pieces.joinToString("-") { it.trackId.toString() }}",
            type = type,
            tier = ctx.effectiveTier,
            ageBand = ctx.ageBand,
            actors = pieces.map { ActorRef.ByTrackId(it.trackId) },
            instruction = "Line them all up in one straight row.",
            steps = listOf(
                VerificationStep(
                    rule = RuleId.COLLINEAR,
                    params = mapOf("tolerance" to tolerance, "minSpread" to 0.20f),
                    holdMs = ctx.knobs.holdMs
                )
            ),
            timeLimitMs = ctx.knobs.timeLimitMs,
            baseScore = 55,
            hints = listOf("Make one straight line", "Even them out", "No zig-zags")
        )
    }

    /** Up to four movable objects, most spread first; always ≥3 (feasibility guaranteed movable ≥ 3). */
    private fun pickPieces(world: WorldState, affById: Map<Int, Affordance>): List<TrackedObject> {
        val movable = world.objects.filter { affById[it.trackId]?.movable == true }
        val pool = if (movable.size >= 3) movable else world.objects
        return pool.sortedByDescending { it.box.area }.take(4)
    }
}
