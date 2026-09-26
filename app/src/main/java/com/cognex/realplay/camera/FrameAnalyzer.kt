package com.cognex.realplay.camera

import android.graphics.Bitmap
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.cognex.realplay.util.RpLog
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Size + rotation of the latest analysis frame — everything the mapper needs from the image. */
data class AnalysisInfo(
    val imageW: Int,
    val imageH: Int,
    val rotationDegrees: Int
)

/** A decoded RGBA frame handed to downstream perception. [bitmap] is reused — copy if retained. */
data class CameraFrame(
    val bitmap: Bitmap,
    val rotationDegrees: Int,
    val timestampMs: Long
)

/**
 * The CameraX analyzer. Measures a rolling 30-frame FPS, publishes the frame geometry for
 * [CoordinateMapper], and — when [frameSink] is set — decodes each RGBA frame into a reused
 * [Bitmap] and forwards it for detection (S2+).
 *
 * (Android note: [ImageProxy] is a single camera frame; it MUST be closed or the pipeline stalls
 * after a few frames — hence the `finally` block.)
 *
 * Threading contract (§3.3): this runs on a single-thread executor and must NEVER suspend.
 * [frameSink] is invoked on that same analyzer thread and must return quickly (MediaPipe's
 * detectAsync is non-blocking, so it satisfies this).
 */
class FrameAnalyzer : ImageAnalysis.Analyzer {

    /** Set by the perception pipeline to receive decoded frames. Null = FPS-only (S1 behaviour). */
    @Volatile
    var frameSink: ((CameraFrame) -> Unit)? = null

    private val _fps = MutableStateFlow(0f)
    val fps: StateFlow<Float> = _fps.asStateFlow()

    private val _analysisInfo = MutableStateFlow<AnalysisInfo?>(null)
    val analysisInfo: StateFlow<AnalysisInfo?> = _analysisInfo.asStateFlow()

    /** Ring buffer of the last [WINDOW] frame timestamps (ns), used to compute rolling FPS. */
    private val frameTimestampsNs = LongArray(WINDOW)
    private var writeIndex = 0
    private var filled = 0
    private var framesSinceLog = 0

    private var reusableBitmap: Bitmap? = null
    private var lastMonotonicMs = 0L

    override fun analyze(imageProxy: ImageProxy) {
        try {
            _analysisInfo.value = AnalysisInfo(
                imageW = imageProxy.width,
                imageH = imageProxy.height,
                rotationDegrees = imageProxy.imageInfo.rotationDegrees
            )
            recordFrameForFps()

            val sink = frameSink
            if (sink != null) {
                val bitmap = decodeRgba(imageProxy)
                if (bitmap != null) {
                    sink(
                        CameraFrame(
                            bitmap = bitmap,
                            rotationDegrees = imageProxy.imageInfo.rotationDegrees,
                            timestampMs = nextMonotonicMs()
                        )
                    )
                }
            }
        } catch (t: Throwable) {
            RpLog.e(RpLog.Tag.CAMERA, "Analyzer frame failed", t)
        } finally {
            imageProxy.close()
        }
    }

    /**
     * Decodes an RGBA_8888 [ImageProxy] into a reused [Bitmap]. The single plane may have row
     * padding, so the bitmap is allocated to the padded width; consumers treat the first
     * [ImageProxy.getWidth] columns as valid pixels.
     */
    private fun decodeRgba(imageProxy: ImageProxy): Bitmap? {
        val plane = imageProxy.planes.firstOrNull() ?: return null
        val pixelStride = plane.pixelStride
        val rowStride = plane.rowStride
        val paddedWidth = if (pixelStride > 0) rowStride / pixelStride else imageProxy.width
        val height = imageProxy.height

        var bmp = reusableBitmap
        if (bmp == null || bmp.width != paddedWidth || bmp.height != height) {
            bmp = Bitmap.createBitmap(paddedWidth, height, Bitmap.Config.ARGB_8888)
            reusableBitmap = bmp
        }
        plane.buffer.rewind()
        bmp.copyPixelsFromBuffer(plane.buffer)
        return bmp
    }

    private fun nextMonotonicMs(): Long {
        val now = System.nanoTime() / 1_000_000L
        val ms = if (now <= lastMonotonicMs) lastMonotonicMs + 1 else now
        lastMonotonicMs = ms
        return ms
    }

    private fun recordFrameForFps() {
        val now = System.nanoTime()
        frameTimestampsNs[writeIndex] = now
        writeIndex = (writeIndex + 1) % WINDOW
        if (filled < WINDOW) filled++

        if (filled >= 2) {
            val oldestIndex = if (filled < WINDOW) 0 else writeIndex
            val spanNs = now - frameTimestampsNs[oldestIndex]
            if (spanNs > 0) {
                _fps.value = (filled - 1) * 1_000_000_000f / spanNs
            }
        }

        // Periodic throughput log so the S1 "≥25 fps analyzer" gate is measurable from logcat.
        // Info level: some OEM builds (OriginOS) suppress debug logs.
        if (++framesSinceLog >= WINDOW) {
            framesSinceLog = 0
            RpLog.i(RpLog.Tag.CAMERA, "Analyzer FPS ~${"%.1f".format(_fps.value)}")
        }
    }

    companion object {
        private const val WINDOW = 30
    }
}
