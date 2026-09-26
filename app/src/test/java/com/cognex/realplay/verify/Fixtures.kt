package com.cognex.realplay.verify

import com.cognex.realplay.challenge.ActorRef
import com.cognex.realplay.challenge.AgeBand
import com.cognex.realplay.challenge.ChallengeSpec
import com.cognex.realplay.challenge.ChallengeType
import com.cognex.realplay.challenge.Tier
import com.cognex.realplay.challenge.VerificationStep
import com.cognex.realplay.world.ColorTag
import com.cognex.realplay.world.FrameQuality
import com.cognex.realplay.world.Landmark
import com.cognex.realplay.world.NormPoint
import com.cognex.realplay.world.NormRect
import com.cognex.realplay.world.TrackedObject
import com.cognex.realplay.world.TrackedPlayer
import com.cognex.realplay.world.WorldState
import com.cognex.realplay.world.Zone
import com.cognex.realplay.world.ZoneSource

/** Shared builders for the verifier JVM tests. Pure, deterministic. */
object Fixtures {

    fun obj(
        trackId: Int,
        label: String = "obj$trackId",
        cx: Float = 0.5f,
        cy: Float = 0.5f,
        w: Float = 0.1f,
        h: Float = 0.1f,
        confidence: Float = 0.9f,
        color: ColorTag? = null,
        ambiguous: Boolean = false
    ): TrackedObject = TrackedObject(
        trackId = trackId,
        label = label,
        confidence = confidence,
        box = NormRect(cx - w / 2, cy - h / 2, cx + w / 2, cy + h / 2),
        center = NormPoint(cx, cy),
        color = color,
        ageFrames = 10,
        lastSeenMs = 0L,
        velocity = NormPoint(0f, 0f),
        stable = true,
        stale = false,
        ambiguous = ambiguous
    )

    fun player(
        playerId: Int = 1,
        landmarks: List<Landmark> = emptyList(),
        cx: Float = 0.5f,
        cy: Float = 0.5f,
        motionEnergy: Float = 0f,
        confidence: Float = 0.9f,
        ambiguous: Boolean = false
    ): TrackedPlayer = TrackedPlayer(
        playerId = playerId,
        landmarks = landmarks,
        torsoBox = NormRect(cx - 0.1f, cy - 0.15f, cx + 0.1f, cy + 0.15f),
        colorBand = null,
        motionEnergy = motionEnergy,
        confidence = confidence,
        ambiguous = ambiguous
    )

    /** A 33-landmark pose list with the given overrides; all others at origin, full visibility. */
    fun landmarks(vararg overrides: Pair<Int, Landmark>): List<Landmark> {
        val base = MutableList(33) { Landmark(NormPoint(0f, 0f), 1f) }
        for ((i, lm) in overrides) base[i] = lm
        return base
    }

    fun zone(id: String = "z", polygon: List<NormPoint>): Zone =
        Zone(id, polygon, ColorTag.UNKNOWN, ZoneSource.DERIVED)

    /** A rectangular zone. */
    fun boxZone(id: String = "z", left: Float, top: Float, right: Float, bottom: Float): Zone =
        zone(id, listOf(NormPoint(left, top), NormPoint(right, top), NormPoint(right, bottom), NormPoint(left, bottom)))

    fun world(
        objects: List<TrackedObject> = emptyList(),
        players: List<TrackedPlayer> = emptyList(),
        zones: List<Zone> = emptyList(),
        good: Boolean = true,
        reason: String? = null,
        timestampMs: Long = 0L
    ): WorldState = WorldState.EMPTY.copy(
        frameId = 1L,
        timestampMs = timestampMs,
        objects = objects,
        players = players,
        zones = zones,
        quality = FrameQuality(good, reason)
    )

    fun spec(vararg actors: ActorRef, steps: List<VerificationStep> = emptyList()): ChallengeSpec =
        ChallengeSpec(
            id = "test",
            type = ChallengeType.LAST_RESORT,
            tier = Tier.EASY,
            ageBand = AgeBand.MIDDLE,
            actors = actors.toList(),
            instruction = "",
            steps = steps,
            timeLimitMs = null,
            baseScore = 10,
            hints = emptyList()
        )

    fun step(rule: RuleId, vararg params: Pair<String, Float>, holdMs: Long = 0L): VerificationStep =
        VerificationStep(rule, params.toMap(), holdMs)
}
