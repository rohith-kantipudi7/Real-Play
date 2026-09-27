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
import kotlin.math.min

/**
 * G7 · Triangle Build (Architecture §7, §7.1). Pure JVM.
 *
 * "Arrange these three into a triangle." A single `NON_DEGENERATE_TRIANGLE` step over three movable
 * objects' centres — the verifier rejects collinear points, tiny-area triangles and an over-skewed
 * shape (a large-enough smallest angle and a bounded side ratio). Explicitly 2D camera-space unless
 * `planarSurfaceAvailable` (§7.1); the thresholds tighten with tier.
 *
 * [maxStepsForTier] stays 1 at every tier — the game is already geometrically hard and does NOT
 * chain structural copies (§7). The live triangle overlay (each edge green when its constraint holds,
 * amber when not, with area + smallest angle printed) is the demo centrepiece, drawn by the cue
 * track from this same spec (§26).
 */
class G7TriangleBuildGenerator : ChallengeGenerator {

    override val type = ChallengeType.TRIANGLE_BUILD
    override val id = "G7"
    override val proven = true
    override val requires = Requirement(minMovable = 3)

    /** 0 when fewer than three movable; else 0.5 + 0.3·spread + 0.2·distinctRatio (§7). */
    override fun feasibility(cap: SceneCapability, ctx: GenerationContext): Float {
        if (cap.movableCount < 3) return 0f
        val spread = cap.spread.coerceIn(0f, 1f)
        val distinctRatio = min(1f, cap.distinctColors.size / 3f)
        return (0.5f + 0.3f * spread + 0.2f * distinctRatio).coerceIn(0f, 1f)
    }

    /** Already geometrically hard — never chains extra structural copies (§7). */
    override fun maxStepsForTier(tier: Tier): Int = 1

    /** Offered from MIDDLE upward (§10) — needs three placed objects, not a toddler/early game. */
    override fun ageGate(band: AgeBand): Float =
        if (band == AgeBand.MIDDLE || band == AgeBand.OLDER) 1f else 0f

    override fun generate(
        world: WorldState,
        aff: List<Affordance>,
        ctx: GenerationContext,
        stepBudget: Int
    ): ChallengeSpec {
        val affById = aff.associateBy { it.trackId }
        val corners = pickThree(world, affById)

        // Tier-scaled geometry thresholds: a HARD triangle must be fuller (bigger min angle, less
        // skew) than an EASY one. Camera-space (2D) thresholds; a calibrated planar surface would
        // scale these into metric space in a later stage.
        val (minArea, minAngle, maxRatio) = when (ctx.effectiveTier) {
            Tier.EASY -> Triple(0.014f, 15f, 6f)
            Tier.MEDIUM -> Triple(0.018f, 18f, 5f)
            Tier.HARD -> Triple(0.024f, 22f, 4f)
        }

        return ChallengeSpec(
            id = "G7-${corners.joinToString("-") { it.trackId.toString() }}",
            type = type,
            tier = ctx.effectiveTier,
            ageBand = ctx.ageBand,
            actors = corners.map { ActorRef.ByTrackId(it.trackId) },
            instruction = "Arrange these three into a nice, open triangle.",
            steps = listOf(
                VerificationStep(
                    rule = RuleId.NON_DEGENERATE_TRIANGLE,
                    params = mapOf("minArea" to minArea, "minAngle" to minAngle, "maxRatio" to maxRatio),
                    holdMs = ctx.knobs.holdMs
                )
            ),
            timeLimitMs = ctx.knobs.timeLimitMs,
            baseScore = 50,
            hints = listOf("Spread them apart", "Don't let them line up", "Make it fuller")
        )
    }

    /**
     * Picks three corner objects. Prefers movable + stable, then distinctly-coloured, then the three
     * most spread apart so the starting shape is already close to a real triangle. Always returns
     * three (padding from any objects) because feasibility already guaranteed movableCount ≥ 3.
     */
    private fun pickThree(world: WorldState, affById: Map<Int, Affordance>): List<TrackedObject> {
        val movable = world.objects.filter { affById[it.trackId]?.movable == true }
        val pool = (if (movable.size >= 3) movable else world.objects)
            .sortedWith(
                compareByDescending<TrackedObject> { affById[it.trackId]?.distinct == true }
                    .thenByDescending { it.color != null && it.color != ColorTag.UNKNOWN }
                    .thenByDescending { it.box.area }
            )
        return pool.take(3)
    }
}
