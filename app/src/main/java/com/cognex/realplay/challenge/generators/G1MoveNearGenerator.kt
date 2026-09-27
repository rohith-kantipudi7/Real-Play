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
import kotlin.math.hypot
import kotlin.math.min

/**
 * G1 · Move it Close (Architecture §7, §7.1). Pure JVM.
 *
 * Picks two movable, stable objects currently separated by more than 1.8× the target threshold, so
 * the player must genuinely move something. Prefers a distinct + nameable pair and ALWAYS names the
 * objects ("the cup", "the book"); when an object has no reliable name it falls back to a colour-free
 * glow reference ("the glowing one") — a move target is never named by its colour (§7.1, product call).
 *
 * maxStepsForTier returns 2 at HARD; the chained "…then move the cup away from the book" form is
 * emitted when the budget is 2 and a distinct third movable object exists (§8.1b, §7.1).
 */
class G1MoveNearGenerator : ChallengeGenerator {

    override val type = ChallengeType.MOVE_CLOSE
    override val id = "G1"
    override val proven = true
    override val requires = Requirement(minMovable = 2)

    /** 0.5 + 0.2·min(mv,4)/4 + 0.3·spread (§7). */
    override fun feasibility(cap: SceneCapability, ctx: GenerationContext): Float =
        0.5f + 0.2f * (min(cap.movableCount, 4) / 4f) + 0.3f * cap.spread.coerceIn(0f, 1f)

    /** Chains to 2 at HARD (§8.1b); single-step otherwise. */
    override fun maxStepsForTier(tier: Tier): Int = if (tier == Tier.HARD) 2 else 1

    /** Offered from EARLY upward (§10) — not a toddler game. */
    override fun ageGate(band: AgeBand): Float = if (band == AgeBand.TODDLER) 0f else 1f

    override fun generate(
        world: WorldState,
        aff: List<Affordance>,
        ctx: GenerationContext,
        stepBudget: Int
    ): ChallengeSpec {
        val affById = aff.associateBy { it.trackId }
        val threshold = ctx.knobs.distanceThreshold

        val (subject, reference) = pickPair(world, affById, threshold)

        val subjName = phrase(subject, affById[subject.trackId], ctx.trackOnlyMode)
        val refName = phrase(reference, affById[reference.trackId], ctx.trackOnlyMode)

        // STRUCTURAL axis (§8.1b, §7.1): at HARD with a 2-step budget, chain a second ordered step —
        // "…then move the cup away from the book". The second step is DISTANCE_GREATER_THAN with
        // mustFollowPreviousStep, and it operates on a DIFFERENT object (the cup) via actorIndices,
        // so both steps read their own actors. Falls back to the single-step form when the budget is
        // 1 or the scene lacks a distinct third movable object.
        val third = pickThird(world, affById, subject, reference)
        if (stepBudget >= 2 && third != null) {
            val thirdName = phrase(third, affById[third.trackId], ctx.trackOnlyMode)
            val awayThreshold = 1.8f * threshold
            return ChallengeSpec(
                id = "G1-${subject.trackId}-${reference.trackId}-${third.trackId}",
                type = type,
                tier = ctx.effectiveTier,
                ageBand = ctx.ageBand,
                actors = listOf(
                    ActorRef.ByTrackId(subject.trackId),
                    ActorRef.ByTrackId(reference.trackId),
                    ActorRef.ByTrackId(third.trackId)
                ),
                instruction = "Move $subjName next to $refName, then move $thirdName far from $refName.",
                steps = listOf(
                    VerificationStep(
                        rule = RuleId.DISTANCE_LESS_THAN,
                        params = mapOf("threshold" to threshold),
                        holdMs = ctx.knobs.holdMs,
                        actorIndices = listOf(0, 1)
                    ),
                    VerificationStep(
                        rule = RuleId.DISTANCE_GREATER_THAN,
                        params = mapOf("threshold" to awayThreshold),
                        holdMs = ctx.knobs.holdMs,
                        mustFollowPreviousStep = true,
                        actorIndices = listOf(2, 1)
                    )
                ),
                timeLimitMs = ctx.knobs.timeLimitMs,
                baseScore = 30,
                hints = listOf("First slide it close", "Now move the other one away")
            )
        }

        val steps = listOf(
            VerificationStep(
                rule = RuleId.DISTANCE_LESS_THAN,
                params = mapOf("threshold" to threshold),
                holdMs = ctx.knobs.holdMs
            )
        )

        return ChallengeSpec(
            id = "G1-${subject.trackId}-${reference.trackId}",
            type = type,
            tier = ctx.effectiveTier,
            ageBand = ctx.ageBand,
            actors = listOf(ActorRef.ByTrackId(subject.trackId), ActorRef.ByTrackId(reference.trackId)),
            instruction = "Move $subjName next to $refName.",
            steps = steps,
            timeLimitMs = ctx.knobs.timeLimitMs,
            baseScore = 30,
            hints = listOf("Slide it closer", "Almost touching now")
        )
    }

    /** A distinct movable third object (the "cup" in the chained form), or null when none exists. */
    private fun pickThird(
        world: WorldState,
        affById: Map<Int, Affordance>,
        subject: TrackedObject,
        reference: TrackedObject
    ): TrackedObject? {
        val used = setOf(subject.trackId, reference.trackId)
        return world.objects
            .filter { it.trackId !in used && affById[it.trackId]?.movable == true }
            .ifEmpty { world.objects.filter { it.trackId !in used } }
            .minByOrNull { it.box.area }   // prefer the most handheld one
    }


    /**
     * Chooses (subject, reference). Candidates are movable + stable objects; falls back to movable
     * (ignoring stability) then to any two objects so a spec is always produced. Prefers the pair
     * with the largest separation (guaranteeing real movement), and prefers a distinct + nameable
     * pair when one is separated by more than 1.8× the threshold. The subject is the smaller (more
     * handheld) of the pair, the reference the larger (more anchor-like).
     */
    private fun pickPair(
        world: WorldState,
        affById: Map<Int, Affordance>,
        threshold: Float
    ): Pair<TrackedObject, TrackedObject> {
        val movableStable = world.objects.filter { affById[it.trackId]?.movable == true && it.stable }
        val movable = world.objects.filter { affById[it.trackId]?.movable == true }
        val pool = when {
            movableStable.size >= 2 -> movableStable
            movable.size >= 2 -> movable
            else -> world.objects
        }

        val target = 1.8f * threshold
        var best: Pair<TrackedObject, TrackedObject>? = null
        var bestDist = -1f
        var bestNameableDist = -1f
        var bestNameable: Pair<TrackedObject, TrackedObject>? = null
        for (i in pool.indices) {
            for (j in i + 1 until pool.size) {
                val a = pool[i]; val b = pool[j]
                val d = distance(a, b)
                if (d > bestDist) { bestDist = d; best = a to b }
                val bothNameableDistinct =
                    affById[a.trackId]?.let { it.nameable && it.distinct } == true &&
                        affById[b.trackId]?.let { it.nameable && it.distinct } == true
                if (bothNameableDistinct && d > target && d > bestNameableDist) {
                    bestNameableDist = d; bestNameable = a to b
                }
            }
        }
        val chosen = bestNameable ?: best ?: (pool[0] to pool[1])
        // subject = smaller area (easier to pick up); reference = larger (the anchor).
        return if (chosen.first.box.area <= chosen.second.box.area) chosen
        else chosen.second to chosen.first
    }

    /** The object's NAME when we have one, else a colour-free reference to its on-screen glow —
     *  a move target is never identified by a colour word (product call). */
    private fun phrase(obj: TrackedObject, aff: Affordance?, trackOnly: Boolean): String {
        val nameable = aff?.nameable == true
        if (!trackOnly && nameable && obj.label.isNotBlank()) return "the ${obj.label.lowercase()}"
        return "the glowing one"
    }

    private fun distance(a: TrackedObject, b: TrackedObject): Float =
        hypot(a.center.x - b.center.x, a.center.y - b.center.y)
}
