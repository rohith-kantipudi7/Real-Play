package com.cognex.realplay.ui.game

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
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
 * The live game loop (Architecture §13 S5/S6). Preview + detection overlay, the pre-challenge
 * briefing with a 3-2-1 countdown, the hold-ring, the HUD, the multi-step tracker, the domain-aware
 * evidence panel, the calm coaching toast and the success burst — all a pure function of the single
 * [GameUiState] the [GameViewModel] exposes.
 *
 * The camera/detector run exactly as in calibration; each world update (with its capability) is
 * handed to the view model, which verifies OFF the analyzer thread and publishes UI state. This
 * screen only draws and plays sound.
 */
@Composable
fun GameScreen(onFinish: () -> Unit, onBack: () -> Unit) {
    CameraPermissionGate {
        val context = LocalContext.current
        val scope = rememberCoroutineScope()
        val controller = remember { CameraController(context.applicationContext) }
        val vm: GameViewModel = viewModel()
        val sound = remember { SoundManager() }

        val ui by vm.ui.collectAsState()
        val analysisInfo by controller.analyzer.analysisInfo.collectAsState()

        var overlay by remember { mutableStateOf<List<OverlayDetection>>(emptyList()) }

        DisposableEffect(controller) {
            val pipeline = ObjectDetectionPipeline(context.applicationContext)
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
        DisposableEffect(sound) { onDispose { sound.release() } }

        // Success / fail cues, driven purely by status transitions.
        LaunchedEffect(ui.challengeIndex, ui.status) {
            when (ui.status) {
                PlayStatus.PASSED -> sound.success()
                PlayStatus.TIMED_OUT, PlayStatus.FAILED -> sound.fail()
                else -> Unit
            }
        }
        // Distinct step cue when an intermediate step fires (multi-step missions).
        LaunchedEffect(ui.completedSteps) {
            if (ui.completedSteps in 1 until ui.stepCount) sound.step()
        }

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

            HudBar(
                score = ui.score,
                streak = ui.streak,
                challengeIndex = ui.challengeIndex,
                timeFraction = ui.timeFraction,
                timeRemainingMs = ui.timeRemainingMs,
                modifier = Modifier.align(Alignment.TopCenter)
            )

            GamePlayColumn(ui = ui, modifier = Modifier.align(Alignment.Center))

            // Coaching toast sits just above the controls.
            CoachingToast(
                hint = ui.coachingHint,
                visible = ui.status == PlayStatus.COACHING,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 108.dp)
            )

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

            // Pre-challenge briefing + countdown (on top of everything).
            BriefingOverlay(
                challengeKey = ui.challengeIndex,
                instruction = ui.instruction,
                onTick = { sound.countdown() },
                onGo = { sound.start() }
            )

            // Success celebration.
            SuccessBurst(
                visible = ui.status == PlayStatus.PASSED,
                perfect = ui.perfect,
                gainedPoints = ui.lastGain,
                triggerKey = if (ui.status == PlayStatus.PASSED) ui.challengeIndex else 0
            )
        }
    }
}

@Composable
private fun GamePlayColumn(ui: GameUiState, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = ui.instruction,
            color = Color.White,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold
        )

        // The persuasive hold-ring with the status glyph in the centre.
        ProgressRing(
            progress = ui.stepProgress,
            color = statusColor(ui.status)
        ) {
            Text(
                text = statusWord(ui.status),
                color = statusColor(ui.status),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Black
            )
        }

        // Multi-step tracker (renders only when stepCount > 1).
        StepTracker(
            stepIndex = ui.stepIndex,
            stepCount = ui.stepCount,
            completedSteps = ui.completedSteps,
            stepProgress = ui.stepProgress
        )

        // Domain-aware measurement panel.
        EvidencePanel(evidence = ui.evidence)

        Spacer(Modifier.height(4.dp))
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
    PlayStatus.PASSED -> Color(0xFF57E39B)
    PlayStatus.FAILED, PlayStatus.TIMED_OUT -> Color(0xFFFF5C7A)
    PlayStatus.COACHING -> Color(0xFF7CD4FF)
    else -> Color(0xFF25E0C8)
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
