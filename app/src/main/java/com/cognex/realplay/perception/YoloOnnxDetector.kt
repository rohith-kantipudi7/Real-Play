package com.cognex.realplay.perception

import android.content.Context
import android.graphics.Bitmap
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import com.cognex.realplay.util.RpLog
import com.cognex.realplay.world.NormRect
import com.google.mediapipe.framework.image.MPImage
import java.io.File
import java.nio.FloatBuffer
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.max
import kotlin.math.min

/**
 * OPTIONAL stronger object detector: a YOLO model (v8/v11, 80-class COCO) run on ONNX Runtime
 * (Architecture §S2, model-upgrade path). Opt-in and side-loaded — activates only when a
 * `.onnx` model is present at [MODEL_PATH]; otherwise the pipeline uses the bundled EfficientDet.
 *
 * Runs off the analyzer thread on a single-thread executor, dropping frames while busy so it never
 * blocks perception (§3.3). Output boxes are in NORMALIZED analysis-image space, matching the rest
 * of the app; a plain stretch-resize to the model input means normalized coords map back directly.
 *
 * NOTE: YOLO's raw output is export-specific. This decodes the standard Ultralytics export
 * (`[1, 84, 8400]` or transposed), applies confidence gating + NMS, and maps class indices to COCO
 * names. If a specific export differs, tune [CONF], [IOU] and the tensor orientation here.
 */
class YoloOnnxDetector private constructor(
    private val env: OrtEnvironment,
    private val session: OrtSession,
    private val inputName: String,
    private val inputSize: Int,
    private val onResults: (List<RawDetection>) -> Unit
) : ObjectDetectorSource {

    @Volatile private var currentBitmap: Bitmap? = null
    private val busy = AtomicBoolean(false)
    private val worker = Executors.newSingleThreadExecutor()

    override fun setFrameBitmap(bitmap: Bitmap) { currentBitmap = bitmap }

    override fun detect(image: MPImage, timestampMs: Long) {
        val bmp = currentBitmap ?: return
        if (!busy.compareAndSet(false, true)) return // drop frame while an inference is running
        worker.execute {
            try {
                onResults(runInference(bmp))
            } catch (t: Throwable) {
                RpLog.e(RpLog.Tag.PERCEPTION, "YOLO inference failed", t)
            } finally {
                busy.set(false)
            }
        }
    }

    private fun runInference(src: Bitmap): List<RawDetection> {
        val resized = Bitmap.createScaledBitmap(src, inputSize, inputSize, true)
        val buffer = toChwFloat(resized)
        val shape = longArrayOf(1, 3, inputSize.toLong(), inputSize.toLong())
        OnnxTensor.createTensor(env, buffer, shape).use { input ->
            session.run(mapOf(inputName to input)).use { result ->
                @Suppress("UNCHECKED_CAST")
                val out = (result[0].value) as Array<Array<FloatArray>>
                return decode(out[0])
            }
        }
    }

    /** Bitmap → NCHW float32 (RGB, 0..1), the standard YOLO input layout. */
    private fun toChwFloat(bmp: Bitmap): FloatBuffer {
        val n = inputSize * inputSize
        val pixels = IntArray(n)
        bmp.getPixels(pixels, 0, inputSize, 0, 0, inputSize, inputSize)
        val buf = FloatBuffer.allocate(3 * n)
        val r = buf.duplicate(); r.position(0)
        val g = buf.duplicate(); g.position(n)
        val b = buf.duplicate(); b.position(2 * n)
        for (i in 0 until n) {
            val p = pixels[i]
            r.put(((p shr 16) and 0xFF) / 255f)
            g.put(((p shr 8) and 0xFF) / 255f)
            b.put((p and 0xFF) / 255f)
        }
        buf.rewind()
        return buf
    }

    /** Decodes the YOLO output plane and runs NMS. Handles both `[84][8400]` and `[8400][84]`. */
    private fun decode(plane: Array<FloatArray>): List<RawDetection> {
        val transposed = plane.size == 4 + COCO.size // [84][N]
        val anchors = if (transposed) plane[0].size else plane.size
        fun at(row: Int, a: Int) = if (transposed) plane[row][a] else plane[a][row]

        val cands = ArrayList<Cand>()
        for (a in 0 until anchors) {
            var bestCls = -1
            var bestScore = 0f
            for (c in 0 until COCO.size) {
                val s = at(4 + c, a)
                if (s > bestScore) { bestScore = s; bestCls = c }
            }
            if (bestScore < CONF || bestCls < 0) continue
            val cx = at(0, a) / inputSize
            val cy = at(1, a) / inputSize
            val w = at(2, a) / inputSize
            val h = at(3, a) / inputSize
            cands.add(
                Cand(
                    left = (cx - w / 2f).coerceIn(0f, 1f),
                    top = (cy - h / 2f).coerceIn(0f, 1f),
                    right = (cx + w / 2f).coerceIn(0f, 1f),
                    bottom = (cy + h / 2f).coerceIn(0f, 1f),
                    score = bestScore,
                    cls = bestCls
                )
            )
        }
        return nms(cands).map { c ->
            val label = if (LabelVocabulary.isConfidentName(c.score)) LabelVocabulary.friendly(COCO[c.cls]) else ""
            val box = NormRect(c.left, c.top, c.right, c.bottom)
            val color = currentBitmap?.let {
                runCatching { ColorTagger.sampleCentral(it, box.left, box.top, box.right, box.bottom) }.getOrNull()
            }
            RawDetection(label = label, confidence = c.score, box = box, color = color)
        }
    }

    /** Greedy non-max suppression by IoU (Architecture §S2 — YOLO has no built-in NMS). */
    private fun nms(cands: List<Cand>): List<Cand> {
        val sorted = cands.sortedByDescending { it.score }.toMutableList()
        val kept = ArrayList<Cand>(MAX_RESULTS)
        while (sorted.isNotEmpty() && kept.size < MAX_RESULTS) {
            val best = sorted.removeAt(0)
            kept.add(best)
            sorted.removeAll { iou(best, it) > IOU }
        }
        return kept
    }

    private fun iou(a: Cand, b: Cand): Float {
        val ix = max(0f, min(a.right, b.right) - max(a.left, b.left))
        val iy = max(0f, min(a.bottom, b.bottom) - max(a.top, b.top))
        val inter = ix * iy
        val union = (a.right - a.left) * (a.bottom - a.top) + (b.right - b.left) * (b.bottom - b.top) - inter
        return if (union <= 0f) 0f else inter / union
    }

    override fun close() {
        worker.shutdownNow()
        runCatching { session.close() }
    }

    private class Cand(
        val left: Float, val top: Float, val right: Float, val bottom: Float,
        val score: Float, val cls: Int
    )

    companion object {
        const val MODEL_PATH = "/sdcard/realplay/models/yolo.onnx"
        private const val CONF = 0.30f
        private const val IOU = 0.45f
        private const val MAX_RESULTS = 25
        private const val DEFAULT_INPUT = 640

        /** Creates the YOLO detector if a side-loaded model exists, else null (use EfficientDet). */
        fun createIfPresent(
            @Suppress("UNUSED_PARAMETER") context: Context,
            onResults: (List<RawDetection>) -> Unit
        ): YoloOnnxDetector? {
            val file = File(MODEL_PATH)
            if (!file.exists() || file.length() <= 0L) return null
            return try {
                val env = OrtEnvironment.getEnvironment()
                val opts = OrtSession.SessionOptions().apply {
                    runCatching { addNnapi() } // hardware acceleration when available; falls back to CPU
                }
                val session = env.createSession(file.readBytes(), opts)
                val inputName = session.inputNames.first()
                RpLog.i(RpLog.Tag.MODEL, "Object model: TIER-0 YOLO (ONNX) ${file.absolutePath}")
                YoloOnnxDetector(env, session, inputName, DEFAULT_INPUT, onResults)
            } catch (t: Throwable) {
                RpLog.e(RpLog.Tag.PERCEPTION, "YOLO ONNX load failed; falling back to EfficientDet", t)
                null
            }
        }

        /** COCO-80 class names in model order. */
        private val COCO = listOf(
            "person", "bicycle", "car", "motorcycle", "airplane", "bus", "train", "truck", "boat",
            "traffic light", "fire hydrant", "stop sign", "parking meter", "bench", "bird", "cat",
            "dog", "horse", "sheep", "cow", "elephant", "bear", "zebra", "giraffe", "backpack",
            "umbrella", "handbag", "tie", "suitcase", "frisbee", "skis", "snowboard", "sports ball",
            "kite", "baseball bat", "baseball glove", "skateboard", "surfboard", "tennis racket",
            "bottle", "wine glass", "cup", "fork", "knife", "spoon", "bowl", "banana", "apple",
            "sandwich", "orange", "broccoli", "carrot", "hot dog", "pizza", "donut", "cake", "chair",
            "couch", "potted plant", "bed", "dining table", "toilet", "tv", "laptop", "mouse",
            "remote", "keyboard", "cell phone", "microwave", "oven", "toaster", "sink",
            "refrigerator", "book", "clock", "vase", "scissors", "teddy bear", "hair drier",
            "toothbrush"
        )
    }
}
