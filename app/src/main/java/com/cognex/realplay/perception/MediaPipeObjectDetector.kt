package com.cognex.realplay.perception

import android.content.Context
import android.graphics.Bitmap
import com.cognex.realplay.util.RpLog
import com.cognex.realplay.world.NormRect
import com.google.mediapipe.framework.image.MPImage
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.core.Delegate
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.objectdetector.ObjectDetector
import com.google.mediapipe.tasks.vision.objectdetector.ObjectDetectorResult
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Real object detector backed by MediaPipe Tasks Vision (EfficientDet-Lite0), running in
 * LIVE_STREAM mode with a GPU delegate that automatically falls back to CPU (Architecture §S2).
 *
 * MODEL RESOLUTION ORDER (§1.2):
 *   1. Tier-B `/sdcard/realplay/models/realplay_props.tflite` if present (your fine-tune).
 *   2. Bundled Tier-A `assets/models/efficientdet_lite0.tflite`.
 * The tier and path that won are logged loudly.
 *
 * Results arrive on a MediaPipe thread via the result listener; boxes are converted from the
 * model's input-pixel space to NORMALIZED analysis-image space, colour-tagged from the current
 * frame bitmap, and delivered through [onResults].
 */
class MediaPipeObjectDetector private constructor(
    private val detector: ObjectDetector,
    private val onResults: (List<RawDetection>) -> Unit
) : ObjectDetectorSource {

    /** The bitmap for the frame currently being detected, used for colour sampling in results. */
    @Volatile
    private var currentBitmap: Bitmap? = null

    fun setFrameBitmap(bitmap: Bitmap) { currentBitmap = bitmap }

    override fun detect(image: MPImage, timestampMs: Long) {
        try {
            detector.detectAsync(image, timestampMs)
        } catch (t: Throwable) {
            RpLog.e(RpLog.Tag.PERCEPTION, "detectAsync failed", t)
        }
    }

    override fun close() {
        try {
            detector.close()
        } catch (t: Throwable) {
            RpLog.e(RpLog.Tag.PERCEPTION, "detector.close failed", t)
        }
    }

    private fun handleResult(result: ObjectDetectorResult, inputImage: MPImage) {
        val imgW = inputImage.width.toFloat()
        val imgH = inputImage.height.toFloat()
        if (imgW <= 0f || imgH <= 0f) {
            onResults(emptyList())
            return
        }
        val bmp = currentBitmap
        val detections = result.detections().mapNotNull { det ->
            val category = det.categories().maxByOrNull { it.score() } ?: return@mapNotNull null
            val bb = det.boundingBox()
            val box = NormRect(
                left = (bb.left / imgW).coerceIn(0f, 1f),
                top = (bb.top / imgH).coerceIn(0f, 1f),
                right = (bb.right / imgW).coerceIn(0f, 1f),
                bottom = (bb.bottom / imgH).coerceIn(0f, 1f)
            )
            val color = bmp?.let {
                runCatching {
                    ColorTagger.sampleCentral(it, box.left, box.top, box.right, box.bottom)
                }.getOrNull()
            }
            // Confidence-gate the NAME: below the threshold we keep tracking the object but blank the
            // label so downstream naming falls back to highlight colour (§3.5, §7.1). Geometry is
            // untouched. Above it, present a short child-friendly name (§S2).
            val friendly =
                if (LabelVocabulary.isConfidentName(category.score())) LabelVocabulary.friendly(category.categoryName())
                else ""
            RawDetection(
                label = friendly,
                confidence = category.score(),
                box = box,
                color = color
            )
        }
        onResults(detections)
    }

    companion object {
        private const val TIER_B_PROPS = "/sdcard/realplay/models/realplay_props.tflite"
        // Primary: float32 efficientdet_lite2 (accurate, GPU-capable). Fallback: int8 lite0 (CPU).
        private const val TIER_A_FLOAT = "models/efficientdet_lite2.tflite"
        private const val TIER_A_INT8 = "models/efficientdet_lite0.tflite"
        // Permissive detector gate: the tracker's temporal voting + coasting reject spurious hits,
        // so a low threshold maximises recall (more objects seen) without hurting steadiness (§S2).
        private const val SCORE_THRESHOLD = 0.2f
        private const val MAX_RESULTS = 25

        /**
         * Creates the detector, resolving the model tier and trying GPU first, then CPU. Returns
         * null if neither delegate can be created.
         */
        fun create(
            context: Context,
            onResults: (List<RawDetection>) -> Unit
        ): MediaPipeObjectDetector? {
            val tierBFile = File(TIER_B_PROPS)
            val useTierB = tierBFile.exists() && tierBFile.length() > 0L
            // The float32 lite2 model is the accurate default; int8 lite0 is the CPU-only fallback.
            val assetPath = if (useTierB) null else TIER_A_FLOAT
            RpLog.i(
                RpLog.Tag.MODEL,
                if (useTierB) "Object model: TIER-B ${tierBFile.absolutePath}"
                else "Object model: TIER-A asset/$TIER_A_FLOAT"
            )

            var holder: MediaPipeObjectDetector? = null

            fun baseOptions(delegate: Delegate, path: String?): BaseOptions {
                val b = BaseOptions.builder().setDelegate(delegate)
                if (useTierB) {
                    b.setModelAssetBuffer(readFileToDirectBuffer(tierBFile))
                } else {
                    b.setModelAssetPath(path ?: TIER_A_FLOAT)
                }
                return b.build()
            }

            fun build(delegate: Delegate, path: String?): ObjectDetector {
                val options = ObjectDetector.ObjectDetectorOptions.builder()
                    .setBaseOptions(baseOptions(delegate, path))
                    .setRunningMode(RunningMode.LIVE_STREAM)
                    .setScoreThreshold(SCORE_THRESHOLD)
                    .setMaxResults(MAX_RESULTS)
                    .setResultListener { result, inputImage ->
                        holder?.handleResult(result, inputImage)
                    }
                    .setErrorListener { e ->
                        RpLog.e(RpLog.Tag.PERCEPTION, "ObjectDetector error: ${e.message}")
                    }
                    .build()
                return ObjectDetector.createFromOptions(context, options)
            }

            // The float32 lite2 model runs on the GPU delegate (faster + accurate). Try GPU first,
            // then CPU with the same float model, then finally the int8 lite0 on CPU as a last resort.
            // (A Tier-B .tflite of unknown quantisation always uses CPU to be safe.)
            if (!useTierB) {
                runCatching {
                    val d = build(Delegate.GPU, TIER_A_FLOAT)
                    RpLog.i(RpLog.Tag.PERCEPTION, "ObjectDetector: lite2 float on GPU delegate")
                    return MediaPipeObjectDetector(d, onResults).also { holder = it }
                }.onFailure { RpLog.w(RpLog.Tag.PERCEPTION, "lite2 GPU failed (${it.message}); trying CPU") }
            }

            return try {
                val detector = build(Delegate.CPU, assetPath)
                RpLog.i(RpLog.Tag.PERCEPTION, "ObjectDetector created on CPU delegate")
                MediaPipeObjectDetector(detector, onResults).also { holder = it }
            } catch (cpuError: Throwable) {
                RpLog.w(RpLog.Tag.PERCEPTION, "CPU delegate failed (${cpuError.message}); trying int8 lite0 CPU")
                try {
                    val detector = build(Delegate.CPU, TIER_A_INT8)
                    RpLog.i(RpLog.Tag.PERCEPTION, "ObjectDetector fell back to int8 lite0 on CPU")
                    MediaPipeObjectDetector(detector, onResults).also { holder = it }
                } catch (fallbackError: Throwable) {
                    RpLog.e(RpLog.Tag.PERCEPTION, "All delegates failed; no real detector", fallbackError)
                    null
                }
            }
        }

        private fun readFileToDirectBuffer(file: File): ByteBuffer {
            val bytes = file.readBytes()
            return ByteBuffer.allocateDirect(bytes.size).apply {
                order(ByteOrder.nativeOrder())
                put(bytes)
                rewind()
            }
        }
    }
}
