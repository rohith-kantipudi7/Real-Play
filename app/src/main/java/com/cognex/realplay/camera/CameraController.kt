package com.cognex.realplay.camera

import android.content.Context
import android.util.Size
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.cognex.realplay.util.RpLog
import java.util.concurrent.Executors

/**
 * Owns the CameraX use-case graph: a [Preview] rendered into the PreviewView plus an
 * [ImageAnalysis] feeding [FrameAnalyzer].
 *
 * Design points from the spec (§12, §3.3):
 *  - Analysis is fixed at 640x480 and uses KEEP_ONLY_LATEST so we always process the freshest
 *    frame and never build a backlog.
 *  - The analyzer runs on a dedicated single-thread executor and never touches the main thread.
 *  - Every bind is wrapped in try/catch with loud logging — a silent bind failure is the classic
 *    "black preview" bug.
 */
class CameraController(
    private val context: Context,
    val analyzer: FrameAnalyzer = FrameAnalyzer()
) {
    private val analysisExecutor = Executors.newSingleThreadExecutor()
    private var cameraProvider: ProcessCameraProvider? = null

    var lensFacing: Int = CameraSelector.LENS_FACING_BACK
        private set

    val isFrontCamera: Boolean get() = lensFacing == CameraSelector.LENS_FACING_FRONT

    /**
     * Binds Preview + ImageAnalysis to [lifecycleOwner]. Safe to call again on resume; it
     * always unbinds first so use-cases are never double-bound.
     */
    fun bind(lifecycleOwner: LifecycleOwner, surfaceProvider: Preview.SurfaceProvider) {
        val providerFuture = ProcessCameraProvider.getInstance(context)
        providerFuture.addListener({
            try {
                val provider = providerFuture.get()
                cameraProvider = provider

                val preview = Preview.Builder().build().also {
                    it.setSurfaceProvider(surfaceProvider)
                }

                val resolutionSelector = ResolutionSelector.Builder()
                    .setResolutionStrategy(
                        ResolutionStrategy(
                            Size(640, 480),
                            ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER
                        )
                    )
                    .build()

                val analysis = ImageAnalysis.Builder()
                    .setResolutionSelector(resolutionSelector)
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                    .build()
                    .also { it.setAnalyzer(analysisExecutor, analyzer) }

                val selector = CameraSelector.Builder()
                    .requireLensFacing(lensFacing)
                    .build()

                provider.unbindAll()
                provider.bindToLifecycle(lifecycleOwner, selector, preview, analysis)
                RpLog.i(
                    RpLog.Tag.CAMERA,
                    "Camera bound (lens=${if (isFrontCamera) "FRONT" else "BACK"}, analysis=640x480, KEEP_ONLY_LATEST)"
                )
            } catch (t: Throwable) {
                RpLog.e(RpLog.Tag.CAMERA, "Failed to bind camera use-cases", t)
            }
        }, ContextCompat.getMainExecutor(context))
    }

    /** Unbinds all use-cases (e.g. when the screen leaves the foreground). */
    fun unbind() {
        try {
            cameraProvider?.unbindAll()
            RpLog.i(RpLog.Tag.CAMERA, "Camera unbound")
        } catch (t: Throwable) {
            RpLog.e(RpLog.Tag.CAMERA, "Failed to unbind camera", t)
        }
    }

    /** Releases the analyzer thread. Call when the controller is permanently discarded. */
    fun shutdown() {
        unbind()
        analysisExecutor.shutdown()
    }
}
