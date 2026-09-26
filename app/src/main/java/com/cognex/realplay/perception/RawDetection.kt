package com.cognex.realplay.perception

import com.cognex.realplay.world.ColorTag
import com.cognex.realplay.world.NormRect
import com.google.mediapipe.framework.image.MPImage

/**
 * One raw object detection for a single frame, BEFORE tracking (Architecture §S2).
 *
 * [box] is in NORMALIZED analysis-image space (0..1), matching every other coordinate in the
 * app. [color] is a best-effort dominant-colour tag sampled from the box (null until computed).
 */
data class RawDetection(
    val label: String,
    val confidence: Float,
    val box: NormRect,
    val color: ColorTag? = null
)

/**
 * A source of per-frame object detections. Implemented by [MediaPipeObjectDetector] (real, GPU
 * with CPU fallback) and [FakeObjectDetector] (scripted, no camera needed).
 *
 * Detection may be asynchronous (MediaPipe LIVE_STREAM), so results are delivered via the
 * `onResults` callback supplied at construction rather than returned from [detect].
 */
interface ObjectDetectorSource {
    /**
     * Submits a frame for detection with a strictly-increasing [timestampMs]. Must NOT block the
     * caller (the CameraX analyzer thread, §3.3).
     */
    fun detect(image: MPImage, timestampMs: Long)

    /** Releases native resources. */
    fun close()
}
