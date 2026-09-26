package com.cognex.realplay.world

import kotlin.math.min

/**
 * The scene capability plus its richness breakdown and the three player-derived terms, kept
 * together so the dev overlay (§S3.5.4) can render everything that fed the score. Pure JVM.
 */
data class SceneCapabilityReport(
    val capability: SceneCapability,
    val richness: RichnessBreakdown,
    val poseVariety: Float,
    val motionRange: Float,
    val frameCoverage: Float
)

/**
 * Builds [SceneCapability] from a [WorldState] and its derived [Affordance]s (Architecture §6.2,
 * S3.5). Pure JVM — no Android.
 *
 * Object-derived terms come straight from this frame. The three player-derived terms follow §6.2:
 *  - [PlayerDynamics.poseVariety] and [PlayerDynamics.motionRange] are session-accumulated and are
 *    passed in (0 when there is no player).
 *  - `frameCoverage` = largest torso-box area ÷ 0.25, clamped — computable from this frame alone.
 *
 * The §3.5 capability flags: [SceneCapability.semanticLabelsAvailable] is false when the mean
 * confidence of nameable objects drops below 0.5 or when the dev force-track-only toggle is on;
 * [SceneCapability.trackOnlyMode] is its inverse; planar-surface metric calibration is deferred
 * (§24) so [SceneCapability.planarSurfaceAvailable] is always false for now.
 */
fun SceneCapability.Companion.report(
    world: WorldState,
    affordances: List<Affordance>,
    dynamics: PlayerDynamics = PlayerDynamics.EMPTY,
    forceTrackOnly: Boolean = false
): SceneCapabilityReport {
    val objects = world.objects
    val players = world.players

    val movableCount = affordances.count { it.movable }
    val handheldCount = affordances.count { it.handheld }
    val containerCount = affordances.count { it.container }
    val landmarkCount = affordances.count { it.landmark }
    val nameableAff = affordances.filter { it.nameable }
    val nameableCount = nameableAff.size

    // Colours come from object tags AND player colour bands ("clothing/background", §6.2).
    val distinctColors = buildSet {
        objects.forEach { o -> o.color?.let { add(it) } }
        players.forEach { p -> p.colorBand?.let { add(it) } }
    }

    val spread = meanPairwiseCentroidDistance(objects)
    val stability = presenceStability(objects, players)
    val frameCoverage = players.maxOfOrNull { min(1f, it.torsoBox.area / 0.25f) } ?: 0f

    // §3.5 capability flags.
    val nameableIds = nameableAff.mapTo(HashSet()) { it.trackId }
    val nameableConfs = objects.filter { it.trackId in nameableIds }.map { it.confidence }
    val meanNameableConf = if (nameableConfs.isEmpty()) 1f
        else nameableConfs.average().toFloat()
    val semanticLabelsAvailable = !forceTrackOnly &&
        meanNameableConf >= AffordanceThresholds.SEMANTIC_MIN_MEAN_CONF
    val trackOnlyMode = !semanticLabelsAvailable

    val poseVariety = if (players.isEmpty()) 0f else dynamics.poseVariety.coerceIn(0f, 1f)
    val motionRange = if (players.isEmpty()) 0f else dynamics.motionRange.coerceIn(0f, 1f)
    val coverage = if (players.isEmpty()) 0f else frameCoverage

    val breakdown = Richness.compute(
        movableCount = movableCount,
        distinctColorCount = distinctColors.size,
        playerCount = players.size,
        containerOrZoneCount = containerCount + world.zones.size,
        spread = spread,
        stability = stability,
        poseVariety = poseVariety,
        motionRange = motionRange,
        frameCoverage = coverage
    )

    val capability = SceneCapability(
        movableCount = movableCount,
        handheldCount = handheldCount,
        containerCount = containerCount,
        landmarkCount = landmarkCount,
        nameableCount = nameableCount,
        distinctColors = distinctColors,
        playerCount = players.size,
        zoneCount = world.zones.size,
        spread = spread,
        stability = stability,
        poseVariety = poseVariety,
        motionRange = motionRange,
        frameCoverage = coverage,
        richness = breakdown.total,
        semanticLabelsAvailable = semanticLabelsAvailable,
        trackOnlyMode = trackOnlyMode,
        planarSurfaceAvailable = false
    )

    return SceneCapabilityReport(capability, breakdown, poseVariety, motionRange, coverage)
}

/** Convenience: just the [SceneCapability] (Architecture §6.2). */
fun SceneCapability.Companion.from(
    world: WorldState,
    affordances: List<Affordance>,
    dynamics: PlayerDynamics = PlayerDynamics.EMPTY,
    forceTrackOnly: Boolean = false
): SceneCapability = report(world, affordances, dynamics, forceTrackOnly).capability

/** Mean pairwise normalized centroid distance across objects (0 for < 2 objects). */
private fun meanPairwiseCentroidDistance(objects: List<TrackedObject>): Float {
    if (objects.size < 2) return 0f
    var sum = 0f
    var count = 0
    for (i in objects.indices) {
        for (j in i + 1 until objects.size) {
            sum += SpatialRelations.distance(objects[i].center, objects[j].center)
            count++
        }
    }
    return if (count == 0) 0f else sum / count
}

/**
 * Fraction of present "tracks" that are settled. Object tracks count when their `stable` flag is
 * set; player tracks count when confidently present (conf ≥ 0.5). With no tracks at all → 0.
 */
private fun presenceStability(objects: List<TrackedObject>, players: List<TrackedPlayer>): Float {
    val total = objects.size + players.size
    if (total == 0) return 0f
    val settled = objects.count { it.stable } + players.count { it.confidence >= 0.5f }
    return settled.toFloat() / total
}
