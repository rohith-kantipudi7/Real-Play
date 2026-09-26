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
 * G3 · Find the Colour (Architecture §7, §7.1). Pure JVM.
 *
 * "Show me something red." Picks a chromatic colour that IS present in the scene but is NOT the
 * colour of the largest object in frame (§7.1) — so the answer is never the one thing already
 * dominating the view. The target actor is a specific object currently wearing that colour; the
 * single verification step is `COLOR_MATCH`, which itself requires the object to resolve (implicit
 * presence) and returns Unsure — never Fail — on an uncertain colour (§20 invariant 2). The area
 * component of §7's `OBJECT_PRESENT (area ≥4%)` is folded into selection: a prominent object of the
 * target colour is preferred so the child genuinely has to present it.
 *
 * maxStepsForTier returns 2 at HARD (the chained "…then something blue" form arrives in S9);
 * generate() emits the single-step form for now but respects [stepBudget], exactly as G1/G2 do.
 */
class G3FindColorGenerator : ChallengeGenerator {

    override val type = ChallengeType.FIND_COLOR
    override val id = "G3"
    override val proven = true
    override val requires = Requirement(minDistinctColors = 2)

    /** 0.4 + 0.15·n, capped at 0.95 (§7), where n = number of distinct colours. */
    override fun feasibility(cap: SceneCapability, ctx: GenerationContext): Float =
        min(0.95f, 0.4f + 0.15f * cap.distinctColors.size)

    /** Chains to 2 at HARD (§8.1b) — two colours in order; single-step otherwise. */
    override fun maxStepsForTier(tier: Tier): Int = if (tier == Tier.HARD) 2 else 1

    /** Offered in every age band, including TODDLER (§10). */
    override fun ageGate(band: AgeBand): Float = 1f

    override fun generate(
        world: WorldState,
        aff: List<Affordance>,
        ctx: GenerationContext,
        stepBudget: Int
    ): ChallengeSpec {
        val affById = aff.associateBy { it.trackId }
        val largest = world.objects.maxByOrNull { it.box.area }
        val excludedColor = largest?.color?.takeIf { it != ColorTag.UNKNOWN }

        // Candidates: chromatic objects that are neither the largest object nor its colour (§7.1).
        val candidates = world.objects.filter { obj ->
            val c = obj.color
            c != null && c != ColorTag.UNKNOWN && c != excludedColor && obj.trackId != largest?.trackId
        }

        val target = candidates
            .sortedWith(
                compareByDescending<TrackedObject> { affById[it.trackId]?.handheld == true }
                    .thenByDescending { it.box.area }
            )
            .firstOrNull()
        // Fallback: any chromatic object at all, then any object — keeps generate() total.
            ?: world.objects.firstOrNull { it.color != null && it.color != ColorTag.UNKNOWN }
            ?: world.objects.first()

        val color = target.color ?: ColorTag.UNKNOWN
        val word = colorWord(color)
        return ChallengeSpec(
            id = "G3-${target.trackId}-${color.name}",
            type = type,
            tier = ctx.effectiveTier,
            ageBand = ctx.ageBand,
            actors = listOf(ActorRef.ByTrackId(target.trackId)),
            instruction = "Show me something $word.",
            steps = listOf(
                VerificationStep(
                    rule = RuleId.COLOR_MATCH,
                    params = mapOf("color" to color.ordinal.toFloat()),
                    holdMs = ctx.knobs.holdMs
                )
            ),
            timeLimitMs = ctx.knobs.timeLimitMs,
            baseScore = 35,
            hints = listOf("Find the $word one", "Hold it up for me")
        )
    }

    private fun colorWord(color: ColorTag): String =
        if (color == ColorTag.UNKNOWN) "colourful" else color.name.lowercase()
}
