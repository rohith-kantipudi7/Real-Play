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
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cognex.realplay.camera.CameraController
import com.cognex.realplay.engine.GameViewModel
import com.cognex.realplay.engine.PlayStatus
import com.cognex.realplay.perception.ObjectDetectionPipeline
import com.cognex.realplay.present.Hud
import com.cognex.realplay.ui.camera.CameraPermissionGate
import com.cognex.realplay.ui.camera.CameraPreview
import com.cognex.realplay.ui.overlay.OverlayCanvas
import com.cognex.realplay.ui.present.MobileTarget
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
        val model by vm.renderModel.collectAsState()
        val analysisInfo by controller.analyzer.analysisInfo.collectAsState()

        DisposableEffect(controller) {
            val pipeline = ObjectDetectionPipeline(context.applicationContext)
            controller.analyzer.frameSink = { pipeline.onFrame(it) }
            val job = scope.launch {
                combine(pipeline.worldState, pipeline.capabilityReport) { world, report ->
                    world to report
                }.collect { (world, report) ->
                    // The engine composes the device-independent RenderModel from this frame; the
                    // overlay + HUD below render only that (§3.6, §20 invariant 21).
                    vm.submitFrame(world, report.capability)
                }
            }
            onDispose {
                controller.analyzer.frameSink = null
                job.cancel()
                pipeline.close()
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
                detections = MobileTarget.detections(model),
                zones = MobileTarget.zones(model)
            )

            HudBar(
                score = model.hud.score,
                streak = model.hud.streak,
                challengeIndex = model.hud.challengeIndex,
                timeFraction = model.hud.timeFraction,
                timeRemainingMs = model.hud.timeRemainingMs,
                modifier = Modifier.align(Alignment.TopCenter)
            )

            GamePlayColumn(hud = model.hud, modifier = Modifier.align(Alignment.Center))

            // Coaching toast sits just above the controls.
            CoachingToast(
                hint = model.hud.coachingHint,
                visible = model.hud.status == PlayStatus.COACHING,
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
                challengeKey = model.hud.challengeIndex,
                instruction = model.hud.instruction,
                onTick = { sound.countdown() },
                onGo = { sound.start() }
            )

            // Success celebration.
            SuccessBurst(
                visible = model.hud.status == PlayStatus.PASSED,
                perfect = model.hud.perfect,
                gainedPoints = model.hud.lastGain,
                triggerKey = if (model.hud.status == PlayStatus.PASSED) model.hud.challengeIndex else 0
            )
        }
    }
}

@Composable
private fun GamePlayColumn(hud: Hud, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = hud.instruction,
            color = Color.White,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold
        )

        // The persuasive hold-ring with the status glyph in the centre.
        ProgressRing(
            progress = hud.stepProgress,
            color = statusColor(hud.status)
        ) {
            Text(
                text = statusWord(hud.status),
                color = statusColor(hud.status),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Black
            )
        }

        // Multi-step tracker (renders only when stepCount > 1).
        StepTracker(
            stepIndex = hud.stepIndex,
            stepCount = hud.stepCount,
            completedSteps = hud.completedSteps,
            stepProgress = hud.stepProgress
        )

        // Domain-aware measurement panel.
        EvidencePanel(evidence = hud.evidence)

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
