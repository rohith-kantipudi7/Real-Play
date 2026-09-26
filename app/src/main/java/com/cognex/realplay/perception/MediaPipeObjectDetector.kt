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
            RawDetection(
                label = category.categoryName(),
                confidence = category.score(),
                box = box,
                color = color
            )
        }
        onResults(detections)
    }

    companion object {
        private const val TIER_B_PROPS = "/sdcard/realplay/models/realplay_props.tflite"
        private const val TIER_A_ASSET = "models/efficientdet_lite0.tflite"

        /**
         * Creates the detector, resolving the model tier and trying GPU first, then CPU. Returns
         * null if neither delegate can be created (caller should fall back to [FakeObjectDetector]).
         */
        fun create(
            context: Context,
            onResults: (List<RawDetection>) -> Unit
        ): MediaPipeObjectDetector? {
            val tierBFile = File(TIER_B_PROPS)
            val useTierB = tierBFile.exists() && tierBFile.length() > 0L
            RpLog.i(
                RpLog.Tag.MODEL,
                if (useTierB) "Object model: TIER-B ${tierBFile.absolutePath}"
                else "Object model: TIER-A asset/$TIER_A_ASSET"
            )

            var holder: MediaPipeObjectDetector? = null

            fun baseOptions(delegate: Delegate): BaseOptions {
                val b = BaseOptions.builder().setDelegate(delegate)
                if (useTierB) {
                    b.setModelAssetBuffer(readFileToDirectBuffer(tierBFile))
                } else {
                    b.setModelAssetPath(TIER_A_ASSET)
                }
                return b.build()
            }

            fun build(delegate: Delegate): ObjectDetector {
                val options = ObjectDetector.ObjectDetectorOptions.builder()
                    .setBaseOptions(baseOptions(delegate))
                    .setRunningMode(RunningMode.LIVE_STREAM)
                    .setScoreThreshold(0.4f)
                    .setMaxResults(10)
                    .setResultListener { result, inputImage ->
                        holder?.handleResult(result, inputImage)
                    }
                    .setErrorListener { e ->
                        RpLog.e(RpLog.Tag.PERCEPTION, "ObjectDetector error: ${e.message}")
                    }
                    .build()
                return ObjectDetector.createFromOptions(context, options)
            }

            return try {
                // The bundled Tier-A model (efficientdet_lite0 int8) is quantized and designed for
                // CPU/XNNPACK — MediaPipe's GPU delegate requires a float model and fails at
                // inference time ("ToTensorConverter: input data size does not match expected size").
                // So object detection runs on CPU; GPU is only a last resort if CPU init fails.
                val detector = build(Delegate.CPU)
                RpLog.i(RpLog.Tag.PERCEPTION, "ObjectDetector created on CPU delegate")
                MediaPipeObjectDetector(detector, onResults).also { holder = it }
            } catch (cpuError: Throwable) {
                RpLog.w(RpLog.Tag.PERCEPTION, "CPU delegate failed (${cpuError.message}); trying GPU")
                try {
                    val detector = build(Delegate.GPU)
                    RpLog.i(RpLog.Tag.PERCEPTION, "ObjectDetector created on GPU delegate")
                    MediaPipeObjectDetector(detector, onResults).also { holder = it }
                } catch (gpuError: Throwable) {
                    RpLog.e(RpLog.Tag.PERCEPTION, "GPU delegate also failed; no real detector", gpuError)
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
