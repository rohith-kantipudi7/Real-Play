package com.cognex.realplay.perception

import com.cognex.realplay.util.RpLog
import com.cognex.realplay.world.ColorTag
import com.cognex.realplay.world.NormRect
import com.google.mediapipe.framework.image.MPImage

/**
 * A scripted [ObjectDetectorSource] that replays a fixed list of detections on every frame, so the
 * entire app runs with no camera and no model (Architecture §S2). Selected via the Settings
 * "use fake detector" toggle.
 *
 * The default script places a few stable objects across the frame — enough to exercise tracking,
 * affordances and the registry downstream.
 */
class FakeObjectDetector(
    private val onResults: (List<RawDetection>) -> Unit,
    private val script: List<RawDetection> = DEFAULT_SCRIPT
) : ObjectDetectorSource {

    override fun detect(image: MPImage, timestampMs: Long) {
        // Deterministic and synchronous — the fake never touches the image.
        onResults(script)
    }

    override fun close() {
        RpLog.i(RpLog.Tag.PERCEPTION, "FakeObjectDetector closed")
    }

    companion object {
        val DEFAULT_SCRIPT: List<RawDetection> = listOf(
            RawDetection("bottle", 0.92f, NormRect(0.10f, 0.30f, 0.28f, 0.80f), ColorTag.BLUE),
            RawDetection("book", 0.88f, NormRect(0.40f, 0.45f, 0.62f, 0.75f), ColorTag.RED),
            RawDetection("cup", 0.81f, NormRect(0.72f, 0.50f, 0.86f, 0.78f), ColorTag.GREEN)
        )
    }
}
