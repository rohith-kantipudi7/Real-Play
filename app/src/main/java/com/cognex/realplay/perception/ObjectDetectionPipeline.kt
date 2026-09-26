package com.cognex.realplay.perception

import android.content.Context
import android.graphics.Bitmap
import com.cognex.realplay.camera.CameraFrame
import com.cognex.realplay.engine.AppSettings
import com.cognex.realplay.util.RpLog
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
 * the detector. Results (async for the real detector) are published on [detections].
 *
 * Selecting fake vs real is a one-line swap via [useFake]; the app runs end-to-end either way.
 */
class ObjectDetectionPipeline(
    context: Context,
    useFake: Boolean
) {
    private val _detections = MutableStateFlow<List<RawDetection>>(emptyList())
    val detections: StateFlow<List<RawDetection>> = _detections.asStateFlow()

    /** The assembled world model (tracked objects + stability + frame quality), S3. */
    private val world = WorldStateBuilder()
    val worldState: StateFlow<WorldState> = world.state

    /** Live capability + richness breakdown for the dev overlay (§S3.5.4). */
    val capabilityReport = world.capabilityReport

    private val mediaPipe: MediaPipeObjectDetector?
    private val detector: ObjectDetectorSource

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
                trackOnlyMode = AppSettings.forceTrackOnly.value
            )
        }
        if (useFake) {
            mediaPipe = null
            detector = FakeObjectDetector(onResults)
            RpLog.i(RpLog.Tag.PERCEPTION, "Detection pipeline: FAKE detector")
        } else {
            val real = MediaPipeObjectDetector.create(context, onResults)
            if (real != null) {
                mediaPipe = real
                detector = real
            } else {
                // Real detector could not be created — degrade to fake so the app still runs.
                mediaPipe = null
                detector = FakeObjectDetector(onResults)
                RpLog.w(RpLog.Tag.PERCEPTION, "Real detector unavailable; using FAKE detector")
            }
        }
    }

    /** Called per frame on the analyzer thread. Non-blocking. */
    fun onFrame(frame: CameraFrame) {
        lastTimestampMs = frame.timestampMs
        // Sample a luminance grid for frame-quality every 5th frame (§12 cadence).
        if (frameCount++ % 5 == 0) {
            lastLumaGrid = FrameQualityAnalyzer.sampleLuma(frame.bitmap)
        }
        mediaPipe?.setFrameBitmap(frame.bitmap)
        val mpImage = BitmapImageBuilder(frame.bitmap).build()
        detector.detect(mpImage, frame.timestampMs)
    }

    fun close() {
        detector.close()
        _detections.value = emptyList()
    }
}
