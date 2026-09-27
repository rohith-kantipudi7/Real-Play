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
import com.cognex.realplay.world.SpatialRelations
import com.cognex.realplay.world.TrackedObject
import com.cognex.realplay.world.WorldState
import com.cognex.realplay.world.Zone
import com.cognex.realplay.world.ZoneSource

/**
 * G2 · Drop Zone (Architecture §7, §7.1). Pure JVM.
 *
 * Asks the player to drop a movable object into a target. The target is preferentially a DETECTED
 * zone (taped square / mat) verified with `POINT_IN_ZONE`; when no zone exists it falls back to an
 * object-container verified with `OVERLAP_RATIO_ABOVE 0.5`.
 *
 * TWO CRITICAL GUARDS (§7.1):
 *   (a) the source `trackId` must NEVER equal the target `trackId` — a cup is both `handheld` and
 *       `container`, and without this guard the game can ask the player to put the cup inside
 *       itself;
 *   (b) prefer a detected zone over an object-container when both exist, and never target an object
 *       (or zone) the subject is already inside.
 *
 * maxStepsForTier returns 2 at HARD; the chained "two objects into two zones, in order" form is
 * emitted when the budget is 2 and a distinct second zone + object exist (§8.1b, §7.1).
 */
class G2DropZoneGenerator : ChallengeGenerator {

    override val type = ChallengeType.DROP_ZONE
    override val id = "G2"
    override val proven = true
    override val requires = Requirement(minMovable = 1, minContainerOrZone = 1)

    /** 0.9 when a detected zone is available, 0.6 for the object-container path (§7). */
    override fun feasibility(cap: SceneCapability, ctx: GenerationContext): Float =
        if (cap.zoneCount >= 1) 0.9f else 0.6f

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
        val pool = world.objects.filter { affById[it.trackId]?.movable == true }
            .ifEmpty { world.objects }

        // (b) Prefer a DETECTED zone over an object-container when both exist.
        val zone = pickZone(world.zones)
        if (zone != null) {
            // Never target a subject already inside the zone.
            val subject = pool.firstOrNull { !SpatialRelations.pointInPolygon(it.center, zone.polygon) }
                ?: pool.first()

            // STRUCTURAL axis (§8.1b, §7.1): at HARD with a 2-step budget, chain a second object into
            // a second zone, in order. Both steps are POINT_IN_ZONE but each acts on its OWN
            // (object, zone) pair via actorIndices. Falls back to the single drop when the budget is 1
            // or the scene lacks a distinct second zone + object.
            val secondZone = pickSecondZone(world.zones, zone)
            val secondSubject = pool.firstOrNull {
                it.trackId != subject.trackId && secondZone != null &&
                    !SpatialRelations.pointInPolygon(it.center, secondZone.polygon)
            }
            if (stepBudget >= 2 && secondZone != null && secondSubject != null) {
                val name1 = phrase(subject, affById[subject.trackId], ctx.trackOnlyMode)
                val name2 = phrase(secondSubject, affById[secondSubject.trackId], ctx.trackOnlyMode)
                return ChallengeSpec(
                    id = "G2-${subject.trackId}-Z${zone.zoneId}-${secondSubject.trackId}-Z${secondZone.zoneId}",
                    type = type,
                    tier = ctx.effectiveTier,
                    ageBand = ctx.ageBand,
                    actors = listOf(
                        ActorRef.ByTrackId(subject.trackId),
                        ActorRef.ByZone(zone.zoneId),
                        ActorRef.ByTrackId(secondSubject.trackId),
                        ActorRef.ByZone(secondZone.zoneId)
                    ),
                    instruction = "Put $name1 into ${zoneName(zone)}, then put $name2 into ${zoneName(secondZone)}.",
                    steps = listOf(
                        VerificationStep(RuleId.POINT_IN_ZONE, emptyMap(), ctx.knobs.holdMs, actorIndices = listOf(0, 1)),
                        VerificationStep(
                            RuleId.POINT_IN_ZONE, emptyMap(), ctx.knobs.holdMs,
                            mustFollowPreviousStep = true, actorIndices = listOf(2, 3)
                        )
                    ),
                    timeLimitMs = ctx.knobs.timeLimitMs,
                    baseScore = 40,
                    hints = listOf("Drop the first one in", "Now the second into its area")
                )
            }

            val name = phrase(subject, affById[subject.trackId], ctx.trackOnlyMode)
            return ChallengeSpec(
                id = "G2-${subject.trackId}-Z${zone.zoneId}",
                type = type,
                tier = ctx.effectiveTier,
                ageBand = ctx.ageBand,
                actors = listOf(ActorRef.ByTrackId(subject.trackId), ActorRef.ByZone(zone.zoneId)),
                instruction = "Put $name into ${zoneName(zone)}.",
                steps = listOf(VerificationStep(RuleId.POINT_IN_ZONE, emptyMap(), ctx.knobs.holdMs)),
                timeLimitMs = ctx.knobs.timeLimitMs,
                baseScore = 40,
                hints = listOf("Slide it over the target", "Right in the middle now")
            )
        }

        // Object-container path, honouring guard (a): source trackId != target trackId.
        val pair = pickObjectContainer(world, affById, pool)
        if (pair != null) {
            val (subject, container) = pair
            val name = phrase(subject, affById[subject.trackId], ctx.trackOnlyMode)
            val target = phrase(container, affById[container.trackId], ctx.trackOnlyMode)
            return ChallengeSpec(
                id = "G2-${subject.trackId}-C${container.trackId}",
                type = type,
                tier = ctx.effectiveTier,
                ageBand = ctx.ageBand,
                actors = listOf(
                    ActorRef.ByTrackId(subject.trackId),
                    ActorRef.ByTrackId(container.trackId)
                ),
                instruction = "Put $name into $target.",
                steps = listOf(
                    VerificationStep(RuleId.OVERLAP_RATIO_ABOVE, mapOf("threshold" to 0.5f), ctx.knobs.holdMs)
                ),
                timeLimitMs = ctx.knobs.timeLimitMs,
                baseScore = 40,
                hints = listOf("Move it on top", "Cover it more")
            )
        }

        // Ultimate safe fallback: no distinct source/target pair exists (e.g. the ONLY object is
        // both movable and a container). Degrade to "bring it close" rather than ever emit a spec
        // whose source == target (§7.1). Total by construction — never crashes.
        val only = pool.first()
        val name = phrase(only, affById[only.trackId], ctx.trackOnlyMode)
        return ChallengeSpec(
            id = "G2-solo-${only.trackId}",
            type = type,
            tier = ctx.effectiveTier,
            ageBand = ctx.ageBand,
            actors = listOf(ActorRef.ByTrackId(only.trackId)),
            instruction = "Bring $name close to the camera.",
            steps = listOf(VerificationStep(RuleId.OBJECT_PRESENT, mapOf("minArea" to 0.06f), ctx.knobs.holdMs)),
            timeLimitMs = ctx.knobs.timeLimitMs,
            baseScore = 30,
            hints = listOf("Hold it up", "A little closer")
        )
    }

    /** The best target zone: a DETECTED one first, else any zone. Null when there are none. */
    private fun pickZone(zones: List<Zone>): Zone? =
        zones.sortedByDescending { it.source == ZoneSource.DETECTED }.firstOrNull()

    /** A second, distinct target zone for the chained HARD form (§8.1b). Null when only one exists. */
    private fun pickSecondZone(zones: List<Zone>, first: Zone): Zone? =
        zones.filter { it.zoneId != first.zoneId }
            .sortedByDescending { it.source == ZoneSource.DETECTED }
            .firstOrNull()

    /**
     * Chooses (subject, container) with subject.trackId != container.trackId (guard a) and where the
     * subject is NOT already inside the container (overlap ≤ 0.5). Prefers the largest container.
     * Returns null when no distinct, not-already-inside pair exists.
     */
    private fun pickObjectContainer(
        world: WorldState,
        affById: Map<Int, Affordance>,
        pool: List<TrackedObject>
    ): Pair<TrackedObject, TrackedObject>? {
        val containers = world.objects
            .filter { affById[it.trackId]?.container == true }
            .sortedByDescending { it.box.area }
        for (c in containers) {
            val s = pool.firstOrNull {
                it.trackId != c.trackId && SpatialRelations.overlapRatio(it.box, c.box) <= 0.5f
            }
            if (s != null) return s to c
        }
        return null
    }

    /** Nameable label, or automatic highlight-colour phrasing (§7.1). Mirrors G1. */
    private fun phrase(obj: TrackedObject, aff: Affordance?, trackOnly: Boolean): String {
        val nameable = aff?.nameable == true
        if (!trackOnly && nameable && obj.label.isNotBlank()) return "the ${obj.label.lowercase()}"
        // No reliable name → reference the on-screen glow, never a colour word (product call).
        return "the glowing one"
    }

    /** Human phrasing for the zone target. */
    private fun zoneName(zone: Zone): String {
        val c = zone.color.takeIf { it != ColorTag.UNKNOWN }?.name?.lowercase()
        return if (c != null) "the $c area" else "the marked area"
    }
}
