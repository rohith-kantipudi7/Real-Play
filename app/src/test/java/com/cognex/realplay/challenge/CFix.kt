package com.cognex.realplay.challenge

import com.cognex.realplay.world.Affordance
import com.cognex.realplay.world.ColorTag
import com.cognex.realplay.world.FrameQuality
import com.cognex.realplay.world.Landmark
import com.cognex.realplay.world.NormPoint
import com.cognex.realplay.world.NormRect
import com.cognex.realplay.world.SceneCapability
import com.cognex.realplay.world.TrackedObject
import com.cognex.realplay.world.TrackedPlayer
import com.cognex.realplay.world.WorldState
import com.cognex.realplay.world.Zone

/** Shared builders for the S5 challenge/engine JVM tests. Pure, deterministic. */
object CFix {

    fun obj(
        trackId: Int,
        label: String = "obj$trackId",
        cx: Float = 0.5f,
        cy: Float = 0.5f,
        w: Float = 0.1f,
        h: Float = 0.1f,
        confidence: Float = 0.9f,
        color: ColorTag? = ColorTag.BLUE,
        stable: Boolean = true,
        ambiguous: Boolean = false
    ): TrackedObject = TrackedObject(
        trackId = trackId,
        label = label,
        confidence = confidence,
        box = NormRect(cx - w / 2, cy - h / 2, cx + w / 2, cy + h / 2),
        center = NormPoint(cx, cy),
        color = color,
        ageFrames = 20,
        lastSeenMs = 0L,
        velocity = NormPoint(0f, 0f),
        stable = stable,
        stale = false,
        ambiguous = ambiguous
    )

    fun player(
        playerId: Int = 1,
        confidence: Float = 0.9f,
        cx: Float = 0.5f,
        cy: Float = 0.5f,
        landmarks: List<Landmark> = emptyList(),
        motionEnergy: Float = 0f,
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

    fun aff(
        trackId: Int,
        movable: Boolean = false,
        handheld: Boolean = false,
        container: Boolean = false,
        landmark: Boolean = false,
        colorful: Boolean = true,
        distinct: Boolean = true,
        nameable: Boolean = true
    ): Affordance = Affordance(trackId, movable, handheld, container, landmark, colorful, distinct, nameable)

    /** An axis-aligned square zone centred at (cx, cy) with half-size [half]. */
    fun zone(
        zoneId: String = "z0",
        cx: Float = 0.5f,
        cy: Float = 0.5f,
        half: Float = 0.1f,
        color: ColorTag = ColorTag.RED,
        source: com.cognex.realplay.world.ZoneSource = com.cognex.realplay.world.ZoneSource.DETECTED
    ): Zone = Zone(
        zoneId = zoneId,
        polygon = listOf(
            NormPoint(cx - half, cy - half),
            NormPoint(cx + half, cy - half),
            NormPoint(cx + half, cy + half),
            NormPoint(cx - half, cy + half)
        ),
        color = color,
        source = source
    )

    fun world(
        objects: List<TrackedObject> = emptyList(),
        players: List<TrackedPlayer> = emptyList(),
        zones: List<Zone> = emptyList(),
        affordances: List<Affordance> = emptyList(),
        good: Boolean = true,
        timestampMs: Long = 0L
    ): WorldState = WorldState.EMPTY.copy(
        frameId = 1L,
        timestampMs = timestampMs,
        objects = objects,
        players = players,
        zones = zones,
        affordances = affordances,
        quality = FrameQuality(good, if (good) null else "Move to better light")
    )

    fun cap(
        movableCount: Int = 0,
        handheldCount: Int = 0,
        containerCount: Int = 0,
        landmarkCount: Int = 0,
        nameableCount: Int = 0,
        distinctColors: Set<ColorTag> = emptySet(),
        playerCount: Int = 0,
        zoneCount: Int = 0,
        spread: Float = 0.5f,
        stability: Float = 1f,
        richness: Float = 0.5f,
        semanticLabelsAvailable: Boolean = true,
        trackOnlyMode: Boolean = false,
        planarSurfaceAvailable: Boolean = false
    ): SceneCapability = SceneCapability(
        movableCount = movableCount,
        handheldCount = handheldCount,
        containerCount = containerCount,
        landmarkCount = landmarkCount,
        nameableCount = nameableCount,
        distinctColors = distinctColors,
        playerCount = playerCount,
        zoneCount = zoneCount,
        spread = spread,
        stability = stability,
        poseVariety = 0f,
        motionRange = 0f,
        frameCoverage = 0f,
        richness = richness,
        semanticLabelsAvailable = semanticLabelsAvailable,
        trackOnlyMode = trackOnlyMode,
        planarSurfaceAvailable = planarSurfaceAvailable
    )

    fun ctx(
        ageBand: AgeBand = AgeBand.MIDDLE,
        tier: Tier = Tier.EASY,
        trackOnlyMode: Boolean = false,
        recentTypes: List<ChallengeType> = emptyList(),
        seed: Long = 0L,
        spread: Float = 0.5f,
        stability: Float = 1f
    ): GenerationContext = GenerationContext(
        ageBand = ageBand,
        effectiveTier = tier,
        knobs = DifficultyKnobs.forTier(tier, spread, stability),
        trackOnlyMode = trackOnlyMode,
        recentTypes = recentTypes,
        seed = seed
    )
}
