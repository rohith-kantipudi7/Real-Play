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
    private val affordanceEngine = AffordanceEngine()

    private val _state = MutableStateFlow(WorldState.EMPTY)
    val state: StateFlow<WorldState> = _state.asStateFlow()

    private val _capabilityReport = MutableStateFlow(
        SceneCapabilityReport(SceneCapability.EMPTY, EMPTY_RICHNESS, 0f, 0f, 0f)
    )
    /** Full capability + richness breakdown for the dev overlay (§S3.5.4). */
    val capabilityReport: StateFlow<SceneCapabilityReport> = _capabilityReport.asStateFlow()

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

        // Provisional world (players/zones empty until the pose/zone stages) for affordance
        // derivation and capability scoring (§6, S3.5).
        val provisional = WorldState(
            frameId = frameId,
            timestampMs = timestampMs,
            objects = objects,
            players = emptyList(),
            zones = emptyList(),
            quality = lastQuality,
            affordances = emptyList(),
            capability = SceneCapability.EMPTY
        )
        val affordances = affordanceEngine.derive(provisional)
        val report = SceneCapability.report(
            world = provisional,
            affordances = affordances,
            dynamics = PlayerDynamics.EMPTY,
            forceTrackOnly = trackOnlyMode
        )
        _capabilityReport.value = report

        val world = provisional.copy(affordances = affordances, capability = report.capability)
        _state.value = world
        return world
    }

    companion object {
        const val QUALITY_EVERY_N = 5L
        private val EMPTY_RICHNESS =
            RichnessBreakdown(0f, RichnessBranch.A_NO_PLAYERS, LinkedHashMap())
    }
}

