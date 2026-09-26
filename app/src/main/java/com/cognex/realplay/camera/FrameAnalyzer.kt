package com.cognex.realplay.camera

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

/**
 * The CameraX analyzer. In S1 it only measures throughput (a rolling 30-frame FPS) and publishes
 * the frame geometry for [CoordinateMapper]. Later stages plug detection into [onFrame].
 *
 * (Android note: [ImageProxy] is a single camera frame; it MUST be closed or the pipeline stalls
 * after a few frames — hence the `finally` block.)
 *
 * Threading contract (§3.3): this runs on a single-thread executor and must NEVER suspend.
 */
class FrameAnalyzer : ImageAnalysis.Analyzer {

    private val _fps = MutableStateFlow(0f)
    val fps: StateFlow<Float> = _fps.asStateFlow()

    private val _analysisInfo = MutableStateFlow<AnalysisInfo?>(null)
    val analysisInfo: StateFlow<AnalysisInfo?> = _analysisInfo.asStateFlow()

    /** Ring buffer of the last [WINDOW] frame timestamps (ns), used to compute rolling FPS. */
    private val frameTimestampsNs = LongArray(WINDOW)
    private var writeIndex = 0
    private var filled = 0

    override fun analyze(imageProxy: ImageProxy) {
        try {
            _analysisInfo.value = AnalysisInfo(
                imageW = imageProxy.width,
                imageH = imageProxy.height,
                rotationDegrees = imageProxy.imageInfo.rotationDegrees
            )
            recordFrameForFps()
            // S2+ will run detection here; S1 does nothing else with the pixels.
        } catch (t: Throwable) {
            RpLog.e(RpLog.Tag.CAMERA, "Analyzer frame failed", t)
        } finally {
            imageProxy.close()
        }
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

    private var framesSinceLog = 0

    companion object {
        private const val WINDOW = 30
    }
}
