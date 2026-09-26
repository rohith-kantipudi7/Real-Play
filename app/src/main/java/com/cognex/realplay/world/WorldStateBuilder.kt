package com.cognex.realplay.world

import com.cognex.realplay.perception.FrameQualityAnalyzer
import com.cognex.realplay.perception.RawDetection
import com.cognex.realplay.perception.Tracker
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Assembles the per-frame [WorldState] from raw detections (Architecture §4, S3). No Android
 * imports — the only pixel-dependent step (frame quality) is delegated to
 * [FrameQualityAnalyzer.analyze], which takes a pre-sampled luminance grid.
 *
 * Owns the [Tracker] and [StabilityDetector]. Frame quality is evaluated on every
 * [QUALITY_EVERY_N]th frame and cached in between (§12). Players, zones and affordances are empty
 * in S3 and are filled by S3.5 / S4; [SceneCapability] carries the object-derived terms and the
 * capability-mode flags that already matter for the challenge pre-filter (§3.5).
 */
class WorldStateBuilder {

    private val tracker = Tracker()
    private val stability = StabilityDetector()

    private val _state = MutableStateFlow(WorldState.EMPTY)
    val state: StateFlow<WorldState> = _state.asStateFlow()

    private var frameCounter = 0L
    private var lastQuality = FrameQuality(good = true, reason = null)

    /**
     * Advances the world by one frame and publishes the new [WorldState].
     *
     * @param lumaGrid optional downscaled luminance grid (0..255) for frame-quality scoring; when
     *   non-null and this is a sampled frame, quality is recomputed, otherwise the last value is
     *   reused.
     */
    fun onFrame(
        detections: List<RawDetection>,
        timestampMs: Long,
        lumaGrid: IntArray? = null,
        gridWidth: Int = 0,
        gridHeight: Int = 0,
        trackOnlyMode: Boolean = false
    ): WorldState {
        val frameId = frameCounter++

        val tracked = tracker.update(detections, timestampMs)
        val stableIds = stability.update(tracked, timestampMs)
        val objects = tracked.map { it.copy(stable = it.trackId in stableIds) }

        if (frameId % QUALITY_EVERY_N == 0L && lumaGrid != null) {
            lastQuality = FrameQualityAnalyzer.analyze(lumaGrid, gridWidth, gridHeight)
        }

        val capability = buildCapability(objects, trackOnlyMode)
        val world = WorldState(
            frameId = frameId,
            timestampMs = timestampMs,
            objects = objects,
            players = emptyList(),
            zones = emptyList(),
            quality = lastQuality,
            affordances = emptyList(),
            capability = capability
        )
        _state.value = world
        return world
    }

    /**
     * Object-derived capability terms available in S3. Affordance counts and player/richness terms
     * are placeholders (filled in S3.5 / S4). [spread] is the mean pairwise centroid distance and
     * [SceneCapability.stability] is the fraction of objects currently stable.
     */
    private fun buildCapability(objects: List<TrackedObject>, trackOnlyMode: Boolean): SceneCapability {
        val distinctColors = objects.mapNotNull { it.color }.toSet()
        val stableFraction = if (objects.isEmpty()) 0f
            else objects.count { it.stable }.toFloat() / objects.size
        val spread = meanPairwiseDistance(objects)
        return SceneCapability.EMPTY.copy(
            distinctColors = distinctColors,
            spread = spread,
            stability = stableFraction,
            trackOnlyMode = trackOnlyMode
        )
    }

    private fun meanPairwiseDistance(objects: List<TrackedObject>): Float {
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

    companion object {
        const val QUALITY_EVERY_N = 5L
    }
}
