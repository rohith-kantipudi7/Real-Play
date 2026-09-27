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
import kotlin.math.min

/**
 * G8 · Grab / Spotlight (demo game library). Pure JVM.
 *
 * "Show me the cup." Names one nameable object in the scene and asks the player to present it to the
 * camera — the single `OBJECT_PRESENT` step with a `minArea` so the object must genuinely be brought
 * forward, not just glimpsed. The proof-of-vision opener of the demo arc (§5): it shows the camera
 * really knows what each thing is. A BASIC game, so it is offered to TODDLER too.
 */
class GShowNamedGenerator : ChallengeGenerator {

    override val type = ChallengeType.GRAB
    override val id = "G8"
    override val proven = true
    override val requires = Requirement(minMovable = 1, needsNameable = true)

    /** 0.5 + 0.1·nameable, capped 0.9 (§7) — a reliable warm-up whenever a named object exists. */
    override fun feasibility(cap: SceneCapability, ctx: GenerationContext): Float =
        if (cap.nameableCount < 1) 0f else min(0.9f, 0.5f + 0.1f * cap.nameableCount)

    /** Always a single "show it" step — never chains (§7). */
    override fun maxStepsForTier(tier: Tier): Int = 1

    /** Offered in every band, including TODDLER (a basic find game, §10 / audience control). */
    override fun ageGate(band: AgeBand): Float = 1f

    override fun generate(
        world: WorldState,
        aff: List<Affordance>,
        ctx: GenerationContext,
        stepBudget: Int
    ): ChallengeSpec {
        val affById = aff.associateBy { it.trackId }
        val target = pickNamed(world, affById)
        // Tier scales how large the object must appear: an easy round accepts a distant glimpse, a
        // hard one demands it be held right up. Toddlers keep the gentle EASY threshold.
        val minArea = when (ctx.effectiveTier) {
            Tier.EASY -> 0.05f
            Tier.MEDIUM -> 0.08f
            Tier.HARD -> 0.11f
        }
        val name = target.label.ifBlank { "glowing object" }
        return ChallengeSpec(
            id = "G8-${target.trackId}",
            type = type,
            tier = ctx.effectiveTier,
            ageBand = ctx.ageBand,
            actors = listOf(ActorRef.ByTrackId(target.trackId)),
            instruction = "Show me the $name!",
            steps = listOf(
                VerificationStep(
                    rule = RuleId.OBJECT_PRESENT,
                    params = mapOf("minArea" to minArea),
                    holdMs = ctx.knobs.holdMs
                )
            ),
            timeLimitMs = ctx.knobs.timeLimitMs,
            baseScore = 30,
            hints = listOf("Find the $name", "Hold it up close for me")
        )
    }

    /** Prefers a nameable, handheld, prominent object; always returns one (feasibility guaranteed ≥1). */
    private fun pickNamed(world: WorldState, affById: Map<Int, Affordance>): TrackedObject =
        world.objects
            .sortedWith(
                compareByDescending<TrackedObject> { affById[it.trackId]?.nameable == true && it.label.isNotBlank() }
                    .thenByDescending { affById[it.trackId]?.handheld == true }
                    .thenByDescending { it.box.area }
            )
            .first()
}
