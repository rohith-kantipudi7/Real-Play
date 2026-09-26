package com.cognex.realplay.challenge

import com.cognex.realplay.verify.RuleId

/**
 * Shared challenge data model (Architecture §4). Pure JVM — no Android.
 *
 * These types are the contract between the challenge layer (generators, registry — S5) and the
 * verification layer (S4). S4 defines them here because the [com.cognex.realplay.verify.Verifier]
 * signature needs them; S5 builds generators, `Requirement`, the registry and safety filter on
 * top. Nothing here depends on how a spec was produced.
 */

/** The eight shipped games (Architecture §7). */
enum class ChallengeType {
    LAST_RESORT,        // G0
    MOVE_CLOSE,         // G1
    DROP_ZONE,          // G2
    FIND_COLOR,         // G3
    STATUE_MATCH,       // G4
    RED_LIGHT_GREEN_LIGHT, // G5
    FETCH_RACE,         // G6
    TRIANGLE_BUILD      // G7
}

/** Difficulty tier (Architecture §8). `effectiveTier = min(skill, scene, age)`. */
enum class Tier { EASY, MEDIUM, HARD }

/** Age band (Architecture §8, §20 invariant 17 — step count is always 1 in TODDLER/EARLY). */
enum class AgeBand { TODDLER, EARLY, MIDDLE, OLDER }

/**
 * A reference to a scene actor (Architecture §4). Resolved against the live [WorldState] at
 * verification time — an actor that no longer exists yields Unsure, never a crash.
 */
sealed interface ActorRef {
    data class ByLabel(val label: String) : ActorRef
    data class ByTrackId(val trackId: Int) : ActorRef
    data class ByZone(val zoneId: String) : ActorRef
    data class ByPlayer(val playerId: Int) : ActorRef
}

/**
 * One verification step (Architecture §4). [params] carries only numeric thresholds (floats);
 * actor references live on the owning [ChallengeSpec.actors] and are consumed positionally by
 * each verifier. [holdMs] is the minimum continuous hold; 0 for instantaneous rules (which the
 * [com.cognex.realplay.verify.TemporalGate] still never passes on a single frame).
 * [mustFollowPreviousStep] enforces ordering in multi-step missions (§7.1, honoured by the
 * MissionRunner in S5).
 */
data class VerificationStep(
    val rule: RuleId,
    val params: Map<String, Float>,
    val holdMs: Long,
    val mustFollowPreviousStep: Boolean = false
)

/**
 * A fully-formed, verifiable challenge (Architecture §4). [actors] is the ordered actor list the
 * steps reference positionally. [timeLimitMs] is null for untimed challenges.
 */
data class ChallengeSpec(
    val id: String,
    val type: ChallengeType,
    val tier: Tier,
    val ageBand: AgeBand,
    val actors: List<ActorRef>,
    val instruction: String,
    val steps: List<VerificationStep>,
    val timeLimitMs: Long?,
    val baseScore: Int,
    val hints: List<String>
)
