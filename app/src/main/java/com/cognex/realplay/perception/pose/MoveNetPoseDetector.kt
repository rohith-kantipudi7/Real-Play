package com.cognex.realplay.perception.pose

import com.cognex.realplay.util.RpLog
import com.google.mediapipe.framework.image.MPImage

/**
 * MoveNet pose-detector STUB behind the same [PoseDetectorSource] seam (Architecture §5, S8 prompt
 * step 1 — "MoveNetPoseDetector as a stub behind the same interface — I want the seam now so I can
 * swap in 10 minutes").
 *
 * It intentionally produces no poses: it exists so the detector backend can be swapped from
 * MediaPipe to MoveNet without touching the identity, dynamics, verification or UI layers. When a
 * real MoveNet (SinglePose Lightning / MultiPose) is wired in, only [detect] changes; everything
 * downstream already consumes [RawPose] through [onResults].
 */
class MoveNetPoseDetector(
    private val onResults: (List<RawPose>) -> Unit
) : PoseDetectorSource {

    override fun detect(image: MPImage, timestampMs: Long) {
        // No backend yet — emit nothing so the object-only path is entirely unaffected.
        onResults(emptyList())
    }

    override fun close() { /* nothing to release */ }

    companion object {
        /** Symmetry with [MediaPipePoseDetector.create]; always succeeds (it is a stub). */
        fun create(onResults: (List<RawPose>) -> Unit): MoveNetPoseDetector {
            RpLog.i(RpLog.Tag.PERCEPTION, "PoseDetector: MoveNet stub (no poses emitted)")
            return MoveNetPoseDetector(onResults)
        }
    }
}
