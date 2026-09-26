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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.cognex.realplay.camera.CameraController
import com.cognex.realplay.ui.camera.CameraPermissionGate
import com.cognex.realplay.ui.camera.CameraPreview
import com.cognex.realplay.ui.overlay.OverlayCanvas

/**
 * S1 calibration: live camera preview with the coordinate-mapper calibration overlay and a live
 * FPS/resolution readout. This is where framing and (later) lighting checks happen before play.
 */
@Composable
fun CalibrationScreen(onReady: () -> Unit, onBack: () -> Unit) {
    CameraPermissionGate {
        val context = LocalContext.current
        val controller = remember { CameraController(context.applicationContext) }
        DisposableEffect(controller) {
            onDispose { controller.shutdown() }
        }

        val fps by controller.analyzer.fps.collectAsState()
        val analysisInfo by controller.analyzer.analysisInfo.collectAsState()
        var showCalibration by remember { mutableStateOf(true) }

        Box(modifier = Modifier.fillMaxSize()) {
            CameraPreview(controller = controller, modifier = Modifier.fillMaxSize())

            OverlayCanvas(
                analysisInfo = analysisInfo,
                isFrontCamera = controller.isFrontCamera,
                modifier = Modifier.fillMaxSize(),
                showCalibration = showCalibration
            )

            // Top readout — FPS + analysis geometry.
            val info = analysisInfo
            val readout = buildString {
                append("FPS ")
                append(String.format("%.1f", fps))
                if (info != null) {
                    append("   \u2022   ${info.imageW}\u00d7${info.imageH}")
                    append("   \u2022   rot ${info.rotationDegrees}\u00b0")
                }
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
                FilterChip(
                    selected = showCalibration,
                    onClick = { showCalibration = !showCalibration },
                    label = { Text("Calibration overlay") }
                )
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
