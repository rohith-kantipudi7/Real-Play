package com.cognex.realplay.ui.calibration

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.cognex.realplay.camera.CameraController
import com.cognex.realplay.engine.AppSettings
import com.cognex.realplay.perception.ObjectDetectionPipeline
import com.cognex.realplay.perception.RawDetection
import com.cognex.realplay.ui.camera.CameraPermissionGate
import com.cognex.realplay.ui.camera.CameraPreview
import com.cognex.realplay.ui.overlay.OverlayCanvas
import com.cognex.realplay.ui.overlay.OverlayDetection
import com.cognex.realplay.world.ColorTag
import kotlinx.coroutines.launch

/**
 * S1/S2 calibration: live camera preview with the coordinate-mapper calibration overlay, live
 * detection boxes (label + confidence + colour), and an FPS/resolution readout.
 */
@Composable
fun CalibrationScreen(onReady: () -> Unit, onBack: () -> Unit) {
    CameraPermissionGate {
        val context = LocalContext.current
        val scope = rememberCoroutineScope()
        val controller = remember { CameraController(context.applicationContext) }

        val fps by controller.analyzer.fps.collectAsState()
        val analysisInfo by controller.analyzer.analysisInfo.collectAsState()
        val useFake by AppSettings.useFakeDetector.collectAsState()

        var showCalibration by remember { mutableStateOf(true) }
        var detections by remember { mutableStateOf<List<RawDetection>>(emptyList()) }

        // (Re)build the detection pipeline whenever the fake/real toggle changes.
        DisposableEffect(controller, useFake) {
            val pipeline = ObjectDetectionPipeline(context.applicationContext, useFake)
            controller.analyzer.frameSink = { pipeline.onFrame(it) }
            val job = scope.launch { pipeline.detections.collect { detections = it } }
            onDispose {
                controller.analyzer.frameSink = null
                job.cancel()
                pipeline.close()
                detections = emptyList()
            }
        }
        DisposableEffect(controller) {
            onDispose { controller.shutdown() }
        }

        val overlayDetections = detections.map { d ->
            OverlayDetection(
                left = d.box.left, top = d.box.top, right = d.box.right, bottom = d.box.bottom,
                label = buildString {
                    append(d.label)
                    append(' ')
                    append(String.format("%.2f", d.confidence))
                    d.color?.let { append("  ${it.name.lowercase()}") }
                },
                color = colorForTag(d.color)
            )
        }

        Box(modifier = Modifier.fillMaxSize()) {
            CameraPreview(controller = controller, modifier = Modifier.fillMaxSize())

            OverlayCanvas(
                analysisInfo = analysisInfo,
                isFrontCamera = controller.isFrontCamera,
                modifier = Modifier.fillMaxSize(),
                showCalibration = showCalibration,
                detections = overlayDetections
            )

            // Top readout — FPS + analysis geometry + detection count.
            val info = analysisInfo
            val readout = buildString {
                append("FPS ")
                append(String.format("%.1f", fps))
                if (info != null) {
                    append("   \u2022   ${info.imageW}\u00d7${info.imageH}")
                    append("   \u2022   rot ${info.rotationDegrees}\u00b0")
                }
                append("   \u2022   ${detections.size} obj")
            }
            Text(
                text = readout,
                style = MaterialTheme.typography.labelLarge,
                color = Color.White,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 24.dp)
                    .background(Color(0xAA000000), RoundedCornerShape(8.dp))
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            )

            // Bottom controls.
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = showCalibration,
                        onClick = { showCalibration = !showCalibration },
                        label = { Text("Calibration") }
                    )
                    FilterChip(
                        selected = useFake,
                        onClick = { AppSettings.setUseFakeDetector(!useFake) },
                        label = { Text("Fake detector") }
                    )
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = onBack,
                        modifier = Modifier.weight(1f)
                    ) { Text("Back") }
                    Button(
                        onClick = onReady,
                        modifier = Modifier.weight(1f)
                    ) { Text("Ready") }
                }
            }
        }
    }
}

/** Maps a [ColorTag] to a display colour for the detection box. */
private fun colorForTag(tag: ColorTag?): Color = when (tag) {
    ColorTag.RED -> Color(0xFFF87171)
    ColorTag.ORANGE -> Color(0xFFFB923C)
    ColorTag.YELLOW -> Color(0xFFFDE047)
    ColorTag.GREEN -> Color(0xFF4ADE80)
    ColorTag.CYAN -> Color(0xFF22D3EE)
    ColorTag.BLUE -> Color(0xFF60A5FA)
    ColorTag.PURPLE -> Color(0xFFA78BFA)
    ColorTag.PINK -> Color(0xFFF472B6)
    ColorTag.WHITE -> Color(0xFFF1F5F9)
    ColorTag.GRAY -> Color(0xFF94A3B8)
    ColorTag.BLACK -> Color(0xFF334155)
    else -> Color(0xFF22D3EE)
}
