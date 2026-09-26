package com.cognex.realplay.verify

import com.cognex.realplay.challenge.ActorRef
import com.cognex.realplay.world.TrackedObject
import com.cognex.realplay.world.TrackedPlayer
import com.cognex.realplay.world.WorldState
import com.cognex.realplay.world.Zone

/**
 * The ONE place the §20 resolution policy lives (Architecture §S4 point 7). Pure JVM.
 *
 * Every verifier funnels its actor lookups and pre-conditions through here so the Unsure rules are
 * identical everywhere and impossible to drift:
 *   - missing actor            → Unsure
 *   - FrameQuality POOR        → Unsure
 *   - actor confidence < 0.45  → Unsure
 *   - ambiguous track/player   → Unsure
 *   - insufficient evidence    → Unsure
 *
 * Pass is emitted ONLY by a verifier's deterministic success path; Fail ONLY when evidence is
 * confidently false. Neither can originate here.
 */
object Resolution {

    const val MIN_ACTOR_CONFIDENCE = 0.45f

    // ── Outcome builders (single source of truth) ────────────────────────────

    fun unsure(hint: String): StepEvaluation =
        StepEvaluation(VerificationOutcome.Unsure(hint), confidence = 0f, evidence = emptyList(), coachingHint = hint)

    fun pass(confidence: Float, evidence: List<Evidence>): StepEvaluation =
        StepEvaluation(VerificationOutcome.Pass(confidence, evidence), confidence, evidence)

    fun fail(reason: String, evidence: List<Evidence>): StepEvaluation =
        StepEvaluation(VerificationOutcome.Fail(reason, evidence), confidence = 1f, evidence = evidence)

    // ── Pre-condition guards. Return an Unsure StepEvaluation to short-circuit, or null when OK. ──

    /** §4.2 rule 1 — never verify on a bad frame. */
    fun frameGuard(world: WorldState): StepEvaluation? =
        if (!world.quality.good) unsure(world.quality.reason ?: "Hold steady") else null

    /** Resolves a required object actor, applying the missing/low-confidence/ambiguous policy. */
    fun requireObject(ref: ActorRef?, world: WorldState, name: String): ObjectResolution {
        val obj = resolveObject(ref, world)
            ?: return ObjectResolution.Missing(unsure("Show me the $name"))
        if (obj.ambiguous) return ObjectResolution.Missing(unsure("Which $name? Separate them a little"))
        if (obj.confidence < MIN_ACTOR_CONFIDENCE)
            return ObjectResolution.Missing(unsure("Bring the $name into clearer view"))
        return ObjectResolution.Found(obj)
    }

    /** Resolves a required player actor, applying the missing/low-confidence/ambiguous policy. */
    fun requirePlayer(ref: ActorRef?, world: WorldState): PlayerResolution {
        val player = resolvePlayer(ref, world)
            ?: return PlayerResolution.Missing(unsure("Step into the frame"))
        if (player.ambiguous) return PlayerResolution.Missing(unsure("Only one player at a time"))
        if (player.confidence < MIN_ACTOR_CONFIDENCE)
            return PlayerResolution.Missing(unsure("Move so I can see you fully"))
        return PlayerResolution.Found(player)
    }

    /** Resolves a required zone actor. */
    fun requireZone(ref: ActorRef?, world: WorldState): ZoneResolution {
        val zone = resolveZone(ref, world)
            ?: return ZoneResolution.Missing(unsure("The target area isn't set up"))
        if (zone.polygon.size < 3) return ZoneResolution.Missing(unsure("The target area isn't set up"))
        return ZoneResolution.Found(zone)
    }

    // ── Raw resolvers (no policy) ────────────────────────────────────────────

    fun resolveObject(ref: ActorRef?, world: WorldState): TrackedObject? = when (ref) {
        is ActorRef.ByTrackId -> world.objects.firstOrNull { it.trackId == ref.trackId }
        is ActorRef.ByLabel -> world.objects
            .filter { it.label.equals(ref.label, ignoreCase = true) }
            .maxByOrNull { it.confidence }
        else -> null
    }

    fun resolvePlayer(ref: ActorRef?, world: WorldState): TrackedPlayer? = when (ref) {
        is ActorRef.ByPlayer -> world.players.firstOrNull { it.playerId == ref.playerId }
        null -> world.players.firstOrNull()
        else -> null
    }

    fun resolveZone(ref: ActorRef?, world: WorldState): Zone? = when (ref) {
        is ActorRef.ByZone -> world.zones.firstOrNull { it.zoneId == ref.zoneId }
        else -> null
    }

    sealed interface ObjectResolution {
        data class Found(val obj: TrackedObject) : ObjectResolution
        data class Missing(val eval: StepEvaluation) : ObjectResolution
    }

    sealed interface PlayerResolution {
        data class Found(val player: TrackedPlayer) : PlayerResolution
        data class Missing(val eval: StepEvaluation) : PlayerResolution
    }

    sealed interface ZoneResolution {
        data class Found(val zone: Zone) : ZoneResolution
        data class Missing(val eval: StepEvaluation) : ZoneResolution
    }
}
