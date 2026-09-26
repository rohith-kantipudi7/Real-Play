package com.cognex.realplay.perception

import android.content.Context
import android.graphics.Bitmap
import com.cognex.realplay.camera.CameraFrame
import com.cognex.realplay.util.RpLog
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

    private val mediaPipe: MediaPipeObjectDetector?
    private val detector: ObjectDetectorSource

    init {
        val onResults: (List<RawDetection>) -> Unit = { _detections.value = it }
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
        mediaPipe?.setFrameBitmap(frame.bitmap)
        val mpImage = BitmapImageBuilder(frame.bitmap).build()
        detector.detect(mpImage, frame.timestampMs)
    }

    fun close() {
        detector.close()
        _detections.value = emptyList()
    }
}
