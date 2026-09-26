package com.cognex.realplay.ui.game

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cognex.realplay.camera.CameraController
import com.cognex.realplay.engine.AppSettings
import com.cognex.realplay.engine.GameUiState
import com.cognex.realplay.engine.GameViewModel
import com.cognex.realplay.engine.PlayStatus
import com.cognex.realplay.perception.ObjectDetectionPipeline
import com.cognex.realplay.ui.camera.CameraPermissionGate
import com.cognex.realplay.ui.camera.CameraPreview
import com.cognex.realplay.ui.overlay.OverlayCanvas
import com.cognex.realplay.ui.overlay.OverlayDetection
import com.cognex.realplay.world.ColorTag
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * The live game loop (Architecture §13 S5). Preview + detection overlay + instruction + progress
 * ring + score/streak, driven entirely by the single [GameUiState] the [GameViewModel] exposes.
 *
 * The camera/detector run exactly as in calibration; each world update (with its capability) is
 * handed to the view model, which verifies OFF the analyzer thread and publishes UI state. Retry,
 * timeout and the Unsure/coaching path are all handled by the view model — this screen only draws.
 */
@Composable
fun GameScreen(onFinish: () -> Unit, onBack: () -> Unit) {
    CameraPermissionGate {
        val context = LocalContext.current
        val scope = rememberCoroutineScope()
        val controller = remember { CameraController(context.applicationContext) }
        val vm: GameViewModel = viewModel()

        val ui by vm.ui.collectAsState()
        val analysisInfo by controller.analyzer.analysisInfo.collectAsState()
        val useFake by AppSettings.useFakeDetector.collectAsState()

        var overlay by remember { mutableStateOf<List<OverlayDetection>>(emptyList()) }

        DisposableEffect(controller, useFake) {
            val pipeline = ObjectDetectionPipeline(context.applicationContext, useFake)
            controller.analyzer.frameSink = { pipeline.onFrame(it) }
            val job = scope.launch {
                combine(pipeline.worldState, pipeline.capabilityReport) { world, report ->
                    world to report
                }.collect { (world, report) ->
                    vm.submitFrame(world, report.capability)
                    overlay = world.objects.map { obj ->
                        OverlayDetection(
                            left = obj.box.left, top = obj.box.top, right = obj.box.right, bottom = obj.box.bottom,
                            label = "#${obj.trackId} ${obj.label}",
                            color = colorForTag(obj.color)
                        )
                    }
                }
            }
            onDispose {
                controller.analyzer.frameSink = null
                job.cancel()
                pipeline.close()
                overlay = emptyList()
            }
        }
        DisposableEffect(controller) { onDispose { controller.shutdown() } }

        // When the session ends, leave for the result screen.
        LaunchedEffect(ui.sessionOver) {
            if (ui.sessionOver) onFinish()
        }

        Box(modifier = Modifier.fillMaxSize()) {
            CameraPreview(controller = controller, modifier = Modifier.fillMaxSize())
            OverlayCanvas(
                analysisInfo = analysisInfo,
                isFrontCamera = controller.isFrontCamera,
                modifier = Modifier.fillMaxSize(),
                showCalibration = false,
                detections = overlay
            )

            GameHud(ui = ui, modifier = Modifier.align(Alignment.TopCenter))

            // Bottom controls.
            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(onClick = onBack, modifier = Modifier.weight(1f)) { Text("Back") }
                OutlinedButton(
                    onClick = { vm.retry() },
                    enabled = ui.retriesLeft > 0,
                    modifier = Modifier.weight(1f)
                ) { Text("Retry (${ui.retriesLeft})") }
                Button(onClick = { vm.finish() }, modifier = Modifier.weight(1f)) { Text("Finish") }
            }
        }
    }
}

@Composable
private fun GameHud(ui: GameUiState, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .padding(top = 24.dp, start = 16.dp, end = 16.dp)
            .fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Score + streak + step indicator.
        Row(
            modifier = Modifier
                .background(Color(0xAA000000), RoundedCornerShape(10.dp))
                .padding(horizontal = 14.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("Score ${ui.score}", color = Color.White, style = MaterialTheme.typography.labelLarge)
            Text("Streak ${ui.streak}", color = Color(0xFFFDE047), style = MaterialTheme.typography.labelLarge)
            if (ui.stepCount > 1) {
                Text(
                    "Step ${ui.stepIndex + 1} of ${ui.stepCount}",
                    color = Color(0xFF60A5FA),
                    style = MaterialTheme.typography.labelLarge
                )
            }
            ui.timeRemainingMs?.let {
                Text("${it / 1000}s", color = Color(0xFFF87171), style = MaterialTheme.typography.labelLarge)
            }
        }

        // Instruction.
        Text(
            text = ui.instruction,
            color = Color.White,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier
                .background(Color(0x99000000), RoundedCornerShape(12.dp))
                .padding(horizontal = 16.dp, vertical = 10.dp)
        )

        // Progress ring + status word.
        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(96.dp)) {
            CircularProgressIndicator(
                progress = { ui.stepProgress.coerceIn(0f, 1f) },
                modifier = Modifier.size(96.dp),
                color = statusColor(ui.status),
                trackColor = Color(0x33FFFFFF)
            )
            Text(
                text = statusWord(ui.status),
                color = statusColor(ui.status),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold
            )
        }

        // Coaching (Unsure) or evidence line.
        val subtitle = ui.coachingHint ?: ui.evidenceLine
        if (subtitle != null) {
            Text(
                text = subtitle,
                color = Color(0xFFE2E8F0),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier
                    .background(Color(0x88000000), RoundedCornerShape(8.dp))
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            )
        }

        if (ui.winnerId.isNotEmpty()) {
            Text(
                text = "${ui.winnerId} · ${ui.winnerType}",
                color = Color(0xFF94A3B8),
                style = MaterialTheme.typography.labelSmall
            )
        }
    }
}

private fun statusWord(status: PlayStatus): String = when (status) {
    PlayStatus.SELECTING -> "…"
    PlayStatus.PLAYING -> "GO"
    PlayStatus.PASSED -> "\u2713"
    PlayStatus.FAILED -> "\u2717"
    PlayStatus.TIMED_OUT -> "\u23F1"
    PlayStatus.COACHING -> "?"
}

private fun statusColor(status: PlayStatus): Color = when (status) {
    PlayStatus.PASSED -> Color(0xFF4ADE80)
    PlayStatus.FAILED, PlayStatus.TIMED_OUT -> Color(0xFFF87171)
    PlayStatus.COACHING -> Color(0xFFFBBF24)
    else -> Color(0xFF22D3EE)
}

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
