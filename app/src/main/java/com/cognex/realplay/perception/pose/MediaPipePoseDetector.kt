package com.cognex.realplay.perception.pose

import android.content.Context
import android.graphics.Bitmap
import com.cognex.realplay.perception.ColorTagger
import com.cognex.realplay.util.RpLog
import com.cognex.realplay.world.ColorTag
import com.cognex.realplay.world.Landmark
import com.cognex.realplay.world.NormPoint
import com.google.mediapipe.framework.image.MPImage
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.core.Delegate
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarker
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarkerResult

/**
 * Real pose detector backed by MediaPipe Pose Landmarker (Architecture §5, §S4, S8 prompt step 1).
 * Runs `pose_landmarker_lite.task` from bundled assets in LIVE_STREAM mode, detecting up to
 * [MAX_POSES] people, on a GPU delegate that automatically falls back to CPU; on CPU it also falls
 * back to `pose_landmarker_full.task`.
 *
 * Results arrive on a MediaPipe thread via the result listener; each pose's 33 landmarks are read in
 * NORMALIZED analysis-image space (the same space as everything in world/) and packaged as
 * [RawPose] with a best-effort dominant torso [ColorTag] band, then delivered through [onResults].
 */
class MediaPipePoseDetector private constructor(
    private val landmarker: PoseLandmarker,
    private val onResults: (List<RawPose>) -> Unit
) : PoseDetectorSource {

    /** The bitmap for the frame currently being detected, used for torso colour sampling. */
    @Volatile
    private var currentBitmap: Bitmap? = null

    fun setFrameBitmap(bitmap: Bitmap) { currentBitmap = bitmap }

    override fun detect(image: MPImage, timestampMs: Long) {
        try {
            landmarker.detectAsync(image, timestampMs)
        } catch (t: Throwable) {
            RpLog.e(RpLog.Tag.PERCEPTION, "pose detectAsync failed", t)
        }
    }

    override fun close() {
        try {
            landmarker.close()
        } catch (t: Throwable) {
            RpLog.e(RpLog.Tag.PERCEPTION, "pose landmarker close failed", t)
        }
    }

    private fun handleResult(result: PoseLandmarkerResult) {
        val bmp = currentBitmap
        val poses = result.landmarks().map { one ->
            val landmarks = one.map { lm ->
                Landmark(
                    point = NormPoint(lm.x().coerceIn(0f, 1f), lm.y().coerceIn(0f, 1f)),
                    visibility = lm.visibility().orElse(0f)
                )
            }
            RawPose(
                landmarks = landmarks,
                presenceConfidence = presence(landmarks),
                colorBand = bmp?.let { torsoBand(it, landmarks) }
            )
        }
        onResults(poses)
    }

    /** Overall confidence this is a real, framed person: mean visibility of the torso joints. */
    private fun presence(landmarks: List<Landmark>): Float {
        val vis = listOf(11, 12, 23, 24).mapNotNull { landmarks.getOrNull(it)?.visibility }
        return if (vis.isEmpty()) 0f else vis.average().toFloat()
    }

    /** Best-effort dominant colour of the torso box (shoulders→hips), for crossing disambiguation. */
    private fun torsoBand(bitmap: Bitmap, landmarks: List<Landmark>): ColorTag? {
        val pts = listOf(11, 12, 23, 24).mapNotNull { landmarks.getOrNull(it)?.point }
        if (pts.size < 4) return null
        val left = pts.minOf { it.x }; val right = pts.maxOf { it.x }
        val top = pts.minOf { it.y }; val bottom = pts.maxOf { it.y }
        if (right <= left || bottom <= top) return null
        return runCatching { ColorTagger.sampleCentral(bitmap, left, top, right, bottom) }.getOrNull()
    }

    companion object {
        private const val MAX_POSES = 2
        private const val LITE = "models/pose_landmarker_lite.task"
        private const val FULL = "models/pose_landmarker_full.task"
        private const val MIN_POSE_DETECTION_CONFIDENCE = 0.5f
        private const val MIN_POSE_PRESENCE_CONFIDENCE = 0.5f
        private const val MIN_TRACKING_CONFIDENCE = 0.5f

        /**
         * Creates the detector, trying the lite model on GPU, then lite on CPU, then the full model
         * on CPU. Returns null if none can be created (the object-only path stays fully functional —
         * §20 invariant 12).
         */
        fun create(
            context: Context,
            onResults: (List<RawPose>) -> Unit
        ): MediaPipePoseDetector? {
            var holder: MediaPipePoseDetector? = null

            fun build(delegate: Delegate, assetPath: String): PoseLandmarker {
                val base = BaseOptions.builder().setDelegate(delegate).setModelAssetPath(assetPath).build()
                val options = PoseLandmarker.PoseLandmarkerOptions.builder()
                    .setBaseOptions(base)
                    .setRunningMode(RunningMode.LIVE_STREAM)
                    .setNumPoses(MAX_POSES)
                    .setMinPoseDetectionConfidence(MIN_POSE_DETECTION_CONFIDENCE)
                    .setMinPosePresenceConfidence(MIN_POSE_PRESENCE_CONFIDENCE)
                    .setMinTrackingConfidence(MIN_TRACKING_CONFIDENCE)
                    .setResultListener { result, _ -> holder?.handleResult(result) }
                    .setErrorListener { e -> RpLog.e(RpLog.Tag.PERCEPTION, "PoseLandmarker error: ${e.message}") }
                    .build()
                return PoseLandmarker.createFromOptions(context, options)
            }

            runCatching {
                val lm = build(Delegate.GPU, LITE)
                RpLog.i(RpLog.Tag.PERCEPTION, "PoseLandmarker: lite on GPU delegate")
                return MediaPipePoseDetector(lm, onResults).also { holder = it }
            }.onFailure { RpLog.w(RpLog.Tag.PERCEPTION, "pose lite GPU failed (${it.message}); trying CPU") }

            return try {
                val lm = build(Delegate.CPU, LITE)
                RpLog.i(RpLog.Tag.PERCEPTION, "PoseLandmarker: lite on CPU delegate")
                MediaPipePoseDetector(lm, onResults).also { holder = it }
            } catch (cpuError: Throwable) {
                RpLog.w(RpLog.Tag.PERCEPTION, "pose lite CPU failed (${cpuError.message}); trying full CPU")
                try {
                    val lm = build(Delegate.CPU, FULL)
                    RpLog.i(RpLog.Tag.PERCEPTION, "PoseLandmarker fell back to full on CPU")
                    MediaPipePoseDetector(lm, onResults).also { holder = it }
                } catch (fullError: Throwable) {
                    RpLog.e(RpLog.Tag.PERCEPTION, "All pose delegates failed; pose disabled", fullError)
                    null
                }
            }
        }
    }
}
