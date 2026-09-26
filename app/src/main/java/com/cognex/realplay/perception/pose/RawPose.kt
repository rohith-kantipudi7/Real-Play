package com.cognex.realplay.perception.pose

import com.cognex.realplay.world.ColorTag
import com.cognex.realplay.world.Landmark
import com.google.mediapipe.framework.image.MPImage

/**
 * One raw detected pose for a single frame, BEFORE identity assignment (Architecture §S4, §5). Pure
 * data — no Android. [landmarks] is the 33-element MediaPipe pose in NORMALIZED analysis-image space
 * (the same space as everything in world/); [presenceConfidence] is the detector's overall
 * confidence this is a real person this frame. [colorBand] is a best-effort dominant torso colour
 * (null until sampled) used only to disambiguate two players who cross (§5).
 */
data class RawPose(
    val landmarks: List<Landmark>,
    val presenceConfidence: Float,
    val colorBand: ColorTag? = null
)

/**
 * A source of per-frame human poses (Architecture §5, §S4). Implemented by
 * [MediaPipePoseDetector] (real, GPU→CPU fallback) and the swap-in
 * [MoveNetPoseDetector] stub behind the same seam — so the detector can be changed without touching
 * the identity/verification layers (§20 invariant 12: object-only path must keep working with pose
 * fully disabled).
 *
 * Detection is asynchronous (MediaPipe LIVE_STREAM), so results are delivered via the `onResults`
 * callback supplied at construction rather than returned from [detect].
 */
interface PoseDetectorSource {
    /**
     * Submits a frame for pose detection with a strictly-increasing [timestampMs]. Must NOT block
     * the caller (the CameraX analyzer thread, §3.3).
     */
    fun detect(image: MPImage, timestampMs: Long)

    /** Releases native resources. */
    fun close()
}
