package com.cognex.realplay.ui.present

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.cognex.realplay.camera.AnalysisInfo
import com.cognex.realplay.camera.CameraController
import com.cognex.realplay.perception.ObjectDetectionPipeline
import com.cognex.realplay.present.VrTarget
import com.cognex.realplay.ui.camera.CameraPermissionGate
import com.cognex.realplay.ui.camera.CameraPreview
import com.cognex.realplay.ui.common.RpOutlinedButton
import com.cognex.realplay.ui.overlay.OverlayCanvas
import com.cognex.realplay.ui.overlay.OverlayDetection
import com.cognex.realplay.ui.theme.RpCyan
import com.cognex.realplay.ui.theme.RpNavyDeep
import com.cognex.realplay.ui.theme.RpOnDarkMuted
import com.cognex.realplay.ui.theme.RpRadius
import com.cognex.realplay.world.WorldState
import kotlinx.coroutines.launch

/**
 * VR concept preview (Architecture v3.6-D — roadmap). Shows the SAME live detections the engine
 * already tracks, split into two lens panes with a [VrTarget] parallax offset — an honest,
 * on-phone demo of "this is what a VR target would render", not a real stereoscopic renderer.
 * Presentation only: no new perception, no verifier, no verdict (invariant 21).
 */
@Composable
fun VrPreviewScreen(onBack: () -> Unit) {
    CameraPermissionGate {
        val context = LocalContext.current
        val scope = rememberCoroutineScope()
        val controller = remember { CameraController(context.applicationContext) }
        var world by remember { mutableStateOf(WorldState.EMPTY) }
        val analysisInfo by controller.analyzer.analysisInfo.collectAsState()

        DisposableEffect(controller) {
            val pipeline = ObjectDetectionPipeline(context.applicationContext)
            controller.analyzer.frameSink = { pipeline.onFrame(it) }
            val job = scope.launch { pipeline.worldState.collect { world = it } }
            onDispose {
                controller.analyzer.frameSink = null
                job.cancel()
                pipeline.close()
            }
        }
        DisposableEffect(controller) { onDispose { controller.shutdown() } }

        val baseDetections = world.objects.map { obj ->
            OverlayDetection(
                left = obj.box.left, top = obj.box.top, right = obj.box.right, bottom = obj.box.bottom,
                label = obj.label,
                color = MobileTarget.colorForTag(obj.color)
            ) to obj.box.area
        }

        Column(modifier = Modifier.fillMaxSize().background(Color.Black)) {
            // The real live camera, full width — proves the split view below is the same scene.
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                CameraPreview(controller = controller, modifier = Modifier.fillMaxSize())
                OverlayCanvas(
                    analysisInfo = analysisInfo,
                    isFrontCamera = controller.isFrontCamera,
                    modifier = Modifier.fillMaxSize(),
                    detections = baseDetections.map { it.first }
                )
                Caption(text = "LIVE CAMERA", modifier = Modifier.align(Alignment.TopStart).padding(12.dp))
            }

            // The concept split — the SAME detections, arranged as a stereo pair via VrTarget.
            Box(modifier = Modifier.weight(1f).fillMaxWidth().background(RpNavyDeep)) {
                Row(modifier = Modifier.fillMaxSize()) {
                    LensPane(analysisInfo, controller.isFrontCamera, baseDetections, sign = -1f, modifier = Modifier.weight(1f))
                    Box(modifier = Modifier.width(3.dp).fillMaxHeight().background(Color.Black))
                    LensPane(analysisInfo, controller.isFrontCamera, baseDetections, sign = 1f, modifier = Modifier.weight(1f))
                }
                Text(
                    text = "VR CONCEPT PREVIEW \u2014 same live detections, presentation only",
                    color = RpCyan,
                    style = MaterialTheme.typography.labelMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(12.dp)
                )
            }

            Row(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                RpOutlinedButton(text = "Back", onClick = onBack, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun LensPane(
    analysisInfo: AnalysisInfo?,
    isFrontCamera: Boolean,
    baseDetections: List<Pair<OverlayDetection, Float>>,
    sign: Float,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxHeight()
            .padding(10.dp)
            .clip(CircleShape)
            .background(RpNavyDeep)
            .border(2.dp, RpCyan.copy(alpha = 0.35f), CircleShape)
    ) {
        val offsetDetections = remember(baseDetections, sign) {
            baseDetections.map { (d, area) ->
                val shift = VrTarget.parallaxFor(area) * sign
                d.copy(
                    left = (d.left + shift).coerceIn(0f, 1f),
                    right = (d.right + shift).coerceIn(0f, 1f)
                )
            }
        }
        OverlayCanvas(
            analysisInfo = analysisInfo,
            isFrontCamera = isFrontCamera,
            modifier = Modifier.fillMaxSize(),
            detections = offsetDetections
        )
    }
}

@Composable
private fun Caption(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        color = RpOnDarkMuted,
        style = MaterialTheme.typography.labelMedium,
        modifier = modifier
            .background(RpNavyDeep.copy(alpha = 0.7f), RoundedCornerShape(RpRadius.sm))
            .padding(horizontal = 8.dp, vertical = 4.dp)
    )
}
