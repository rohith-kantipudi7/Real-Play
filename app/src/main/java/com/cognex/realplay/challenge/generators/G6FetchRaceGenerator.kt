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
import com.cognex.realplay.world.TrackedPlayer
import com.cognex.realplay.world.WorldState
import com.cognex.realplay.world.Zone
import com.cognex.realplay.world.ZoneSource
import kotlin.math.min

/**
 * G6 · Fetch Race (Architecture §7). Pure JVM.
 *
 * Turn-based: names the active player and sends them to fetch a specific object by an attribute the
 * scene ACTUALLY contains — never an attribute no present object satisfies (§7). The mission is an
 * ORDERED sequence of the closed primitives:
 *
 *   1. `PLAYER_HOLDS_OBJECT`  — the named player picks up the target (either wrist near it).
 *   2. `COLOR_MATCH` / `SHAPE_MATCH` — confirms it is the RIGHT one (the requested attribute).
 *   3. `PLAYER_IN_ZONE`       — carry it into the finish area (only when a zone exists).
 *
 * G6 is already multi-step by nature, so [maxStepsForTier] stays 1 at every tier — it does NOT chain
 * additional structural copies; its ordered steps ARE its definition, each honouring
 * `mustFollowPreviousStep`. Each step is projected onto its own actors via `actorIndices` so the
 * existing verifiers read `actors[0], actors[1]…` unchanged.
 */
class G6FetchRaceGenerator : ChallengeGenerator {

    override val type = ChallengeType.FETCH_RACE
    override val id = "G6"
    override val proven = true
    override val requires = Requirement(minPlayers = 1, minHandheld = 2, needsNameable = true)

    /** 0.6 + 0.3·min(hh,3)/3 (§7) — scales with how many handheld objects there are to fetch. */
    override fun feasibility(cap: SceneCapability, ctx: GenerationContext): Float =
        0.6f + 0.3f * (min(cap.handheldCount, 3) / 3f)

    /** Already multi-step by nature — never chains extra structural copies (§7). */
    override fun maxStepsForTier(tier: Tier): Int = 1

    /** Offered from MIDDLE upward when pose is on (§10); never a toddler/early game. */
    override fun ageGate(band: AgeBand): Float =
        if (band == AgeBand.MIDDLE || band == AgeBand.OLDER) 1f else 0f

    override fun generate(
        world: WorldState,
        aff: List<Affordance>,
        ctx: GenerationContext,
        stepBudget: Int
    ): ChallengeSpec {
        val affById = aff.associateBy { it.trackId }
        val player = activePlayer(world)
        val playerId = player?.playerId ?: 0

        val target = pickTarget(world, affById)
        val holdThreshold = 0.12f

        // Choose an attribute the target ACTUALLY has (§7): prefer a chromatic colour, else its box
        // aspect (a coarse shape proxy, matching SHAPE_MATCH). Never request an attribute no object
        // satisfies — the target itself is the guarantee.
        val color = target?.color?.takeIf { it != ColorTag.UNKNOWN }
        val zone = pickZone(world.zones)

        val actors = buildList {
            add(ActorRef.ByPlayer(playerId))
            if (target != null) add(ActorRef.ByTrackId(target.trackId)) else add(ActorRef.ByPlayer(playerId))
            if (zone != null) add(ActorRef.ByZone(zone.zoneId))
        }

        val steps = buildList {
            // 1. Pick it up.
            add(
                VerificationStep(
                    rule = RuleId.PLAYER_HOLDS_OBJECT,
                    params = mapOf("threshold" to holdThreshold),
                    holdMs = ctx.knobs.holdMs,
                    actorIndices = listOf(0, 1)
                )
            )
            // 2. Confirm the requested attribute on the held object.
            if (color != null) {
                add(
                    VerificationStep(
                        rule = RuleId.COLOR_MATCH,
                        params = mapOf("color" to color.ordinal.toFloat()),
                        holdMs = ctx.knobs.holdMs,
                        mustFollowPreviousStep = true,
                        actorIndices = listOf(1)
                    )
                )
            } else if (target != null) {
                val aspect = if (target.box.height > 0f) target.box.width / target.box.height else 1f
                add(
                    VerificationStep(
                        rule = RuleId.SHAPE_MATCH,
                        params = mapOf("aspect" to aspect, "tolerance" to 0.35f),
                        holdMs = ctx.knobs.holdMs,
                        mustFollowPreviousStep = true,
                        actorIndices = listOf(1)
                    )
                )
            }
            // 3. Deliver to the finish area, when one exists.
            if (zone != null) {
                add(
                    VerificationStep(
                        rule = RuleId.PLAYER_IN_ZONE,
                        params = emptyMap(),
                        holdMs = ctx.knobs.holdMs,
                        mustFollowPreviousStep = true,
                        actorIndices = listOf(0, 2)
                    )
                )
            }
        }

        val what = requestPhrase(target, color, affById)
        val deliver = if (zone != null) " and bring it to ${zoneName(zone)}" else ""
        val instruction = "Player $playerId, fetch $what$deliver!"

        return ChallengeSpec(
            id = "G6-P$playerId-${target?.trackId ?: 0}",
            type = type,
            tier = ctx.effectiveTier,
            ageBand = ctx.ageBand,
            actors = actors,
            instruction = instruction,
            steps = steps,
            timeLimitMs = ctx.knobs.timeLimitMs,
            baseScore = 45,
            hints = listOf("Grab it first", "Now carry it over")
        )
    }

    /** Highest-confidence, unambiguous player — the current turn's fetcher. */
    private fun activePlayer(world: WorldState): TrackedPlayer? =
        world.players.filter { !it.ambiguous }.maxByOrNull { it.confidence }

    /**
     * The object to fetch: a nameable + handheld one, preferring a distinctly-coloured object so the
     * requested attribute is unambiguous. Falls back to any handheld, then any object — always total.
     */
    private fun pickTarget(world: WorldState, affById: Map<Int, Affordance>): TrackedObject? {
        val handheld = world.objects.filter { affById[it.trackId]?.handheld == true }
        return handheld
            .sortedWith(
                compareByDescending<TrackedObject> { affById[it.trackId]?.distinct == true }
                    .thenByDescending { it.color != null && it.color != ColorTag.UNKNOWN }
                    .thenByDescending { it.confidence }
            )
            .firstOrNull()
            ?: world.objects.firstOrNull()
    }

    private fun pickZone(zones: List<Zone>): Zone? =
        zones.sortedByDescending { it.source == ZoneSource.DETECTED }.firstOrNull()

    /** Human phrasing for the requested object + attribute (§7 — only attributes the object has). */
    private fun requestPhrase(target: TrackedObject?, color: ColorTag?, affById: Map<Int, Affordance>): String {
        if (target == null) return "an object"
        val nameable = affById[target.trackId]?.nameable == true && target.label.isNotBlank()
        val name = if (nameable) target.label.lowercase() else "object"
        val colorWord = color?.name?.lowercase()
        return when {
            colorWord != null && nameable -> "the $colorWord $name"
            colorWord != null -> "the $colorWord one"
            else -> "the $name"
        }
    }

    private fun zoneName(zone: Zone): String {
        val c = zone.color.takeIf { it != ColorTag.UNKNOWN }?.name?.lowercase()
        return if (c != null) "the $c area" else "the finish area"
    }
}
