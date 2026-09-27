package com.cognex.realplay.perception

import android.content.Context
import android.graphics.Bitmap
import com.cognex.realplay.camera.CameraFrame
import com.cognex.realplay.engine.AppSettings
import com.cognex.realplay.perception.pose.MediaPipePoseDetector
import com.cognex.realplay.perception.pose.PlayerTracker
import com.cognex.realplay.perception.pose.PoseDetectorSource
import com.cognex.realplay.perception.pose.RawPose
import com.cognex.realplay.perception.zone.ZoneDetector
import com.cognex.realplay.util.RpLog
import com.cognex.realplay.verify.PlayerDynamicsTracker
import com.cognex.realplay.world.PlayerDynamics
import com.cognex.realplay.world.TrackedPlayer
import com.cognex.realplay.world.WorldState
import com.cognex.realplay.world.WorldStateBuilder
import com.google.mediapipe.framework.image.BitmapImageBuilder
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Owns the active [ObjectDetectorSource] and turns decoded [CameraFrame]s into detections.
 *
 * The [com.cognex.realplay.camera.FrameAnalyzer] calls [onFrame] on the analyzer thread; this
 * builds an MPImage from the frame bitmap (the fewest-copies path — the bitmap already holds
 * RGBA pixels, so BitmapImageBuilder wraps it without a colour-space conversion) and hands it to
 * the real MediaPipe detector. Results (async) are published on [detections].
 */
class ObjectDetectionPipeline(
    context: Context
) {
    private val _detections = MutableStateFlow<List<RawDetection>>(emptyList())
    val detections: StateFlow<List<RawDetection>> = _detections.asStateFlow()

    /** The assembled world model (tracked objects + stability + frame quality), S3. */
    private val world = WorldStateBuilder()
    val worldState: StateFlow<WorldState> = world.state

    /** Colour-region zone detector (S7). Runs off the frame bitmap every 3rd frame. */
    private val zoneDetector = ZoneDetector()

    /** Live capability + richness breakdown for the dev overlay (§S3.5.4). */
    val capabilityReport = world.capabilityReport

    private val mediaPipe: MediaPipeObjectDetector?
    private val detector: ObjectDetectorSource?

    /** Pose stack (S8). Null when no pose backend could be created — the object-only path is
     *  entirely unaffected (§20 invariant 12). Enabled only when a Body/Mixed challenge needs it. */
    private val poseDetector: MediaPipePoseDetector?
    private val playerTracker = PlayerTracker()
    private val dynamicsTracker = PlayerDynamicsTracker()

    @Volatile
    private var poseActive = false

    @Volatile
    private var latestPlayers: List<TrackedPlayer> = emptyList()

    @Volatile
    private var latestDynamics: PlayerDynamics = PlayerDynamics.EMPTY

    private var frameCount = 0

    @Volatile
    private var lastTimestampMs = 0L

    @Volatile
    private var lastLumaGrid: IntArray? = null

    init {
        val onResults: (List<RawDetection>) -> Unit = { results ->
            _detections.value = results
            // Advance the world model on the detector's result thread (single-threaded).
            world.onFrame(
                detections = results,
                timestampMs = lastTimestampMs,
                lumaGrid = lastLumaGrid,
                gridWidth = FrameQualityAnalyzer.GRID_W,
                gridHeight = FrameQualityAnalyzer.GRID_H,
                zones = if (AppSettings.zonesEnabled.value) zoneDetector.latestZones() else emptyList(),
                trackOnlyMode = AppSettings.forceTrackOnly.value,
                players = if (poseActive) latestPlayers else emptyList(),
                dynamics = if (poseActive) latestDynamics else PlayerDynamics.EMPTY
            )
        }
        // YOLO (ONNX) disabled — it under-performed the bundled EfficientDet on the demo kit. The
        // YoloOnnxDetector class is kept for future opt-in but is not used here (§S2).
        val real = MediaPipeObjectDetector.create(context, onResults)
        mediaPipe = real
        detector = real
        if (real != null) {
            RpLog.i(RpLog.Tag.PERCEPTION, "Detection pipeline: real MediaPipe detector")
        } else {
            RpLog.w(RpLog.Tag.PERCEPTION, "No detector could be created; no detections will be produced")
        }

        // Pose results (async, own MP thread): assign identities and accumulate dynamics, then stash
        // the latest for the object result thread to fold into the world (§5, §6.2).
        val onPoseResults: (List<RawPose>) -> Unit = { poses ->
            val players = playerTracker.update(poses, lastTimestampMs)
            dynamicsTracker.observe(players, lastTimestampMs)
            latestPlayers = players
            latestDynamics = dynamicsTracker.dynamics()
        }
        poseDetector = MediaPipePoseDetector.create(context, onPoseResults)
        if (poseDetector != null) {
            RpLog.i(RpLog.Tag.PERCEPTION, "Pose pipeline: real MediaPipe pose landmarker")
        } else {
            RpLog.w(RpLog.Tag.PERCEPTION, "Pose detector unavailable; object-only path remains fully functional")
        }
    }

    /** Whether a real pose backend exists (Body/Mixed mode can be offered). */
    val poseAvailable: Boolean get() = poseDetector != null

    /**
     * Turns pose detection on/off for the active challenge (§12 PerceptionScheduler). When turned
     * off, players are cleared and the object-only path continues untouched. Re-anchors identities
     * whenever pose is (re)enabled (§5).
     */
    fun setPoseActive(active: Boolean) {
        if (active == poseActive) return
        poseActive = active
        if (active) {
            playerTracker.reset()
            dynamicsTracker.reset()
        } else {
            latestPlayers = emptyList()
            latestDynamics = PlayerDynamics.EMPTY
        }
        RpLog.i(RpLog.Tag.PERCEPTION, "Pose detection ${if (active) "ENABLED" else "disabled"}")
    }

    /** Called per frame on the analyzer thread. Non-blocking. */
    fun onFrame(frame: CameraFrame) {
        val d = detector ?: return
        lastTimestampMs = frame.timestampMs
        // Sample a luminance grid for frame-quality every 5th frame (§12 cadence).
        if (frameCount++ % 5 == 0) {
            lastLumaGrid = FrameQualityAnalyzer.sampleLuma(frame.bitmap)
        }
        // Detect colour zones at most every 3rd frame (§S7 cadence) — only when zones are enabled;
        // off by default so a coloured object is never mistaken for a "target region".
        if (AppSettings.zonesEnabled.value && frameCount % ZoneDetector.RUN_EVERY_N == 0) {
            zoneDetector.onFrame(frame.bitmap)
        }
        d.setFrameBitmap(frame.bitmap)
        val mpImage = BitmapImageBuilder(frame.bitmap).build()
        d.detect(mpImage, frame.timestampMs)

        // Pose runs on its own MediaPipe thread; feed it the same frame only when active (§12).
        if (poseActive) {
            poseDetector?.let { pose ->
                pose.setFrameBitmap(frame.bitmap)
                pose.detect(BitmapImageBuilder(frame.bitmap).build(), frame.timestampMs)
            }
        }
    }

    fun close() {
        detector?.close()
        poseDetector?.close()
        _detections.value = emptyList()
    }
}
