package com.cognex.realplay.ui.game

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cognex.realplay.ai.Narrator
import com.cognex.realplay.camera.CameraController
import com.cognex.realplay.challenge.AgeBand
import com.cognex.realplay.challenge.ChallengeType
import com.cognex.realplay.engine.GameViewModel
import com.cognex.realplay.engine.PartyRuntime
import com.cognex.realplay.engine.PlayMode
import com.cognex.realplay.engine.PlayStatus
import com.cognex.realplay.engine.SessionConfig
import com.cognex.realplay.engine.SessionResults
import com.cognex.realplay.perception.ObjectDetectionPipeline
import com.cognex.realplay.present.Hud
import com.cognex.realplay.ui.camera.CameraPermissionGate
import com.cognex.realplay.ui.camera.CameraPreview
import com.cognex.realplay.ui.common.RpButton
import com.cognex.realplay.ui.common.RpOutlinedButton
import com.cognex.realplay.ui.overlay.CueCanvas
import com.cognex.realplay.ui.overlay.MinimalTrackerOverlay
import com.cognex.realplay.ui.overlay.OverlayCanvas
import com.cognex.realplay.ui.present.MobileTarget
import com.cognex.realplay.ui.theme.RpNavyDeep
import com.cognex.realplay.ui.theme.RpNavyElevated
import com.cognex.realplay.ui.theme.RpRadius
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
        val narrator = remember { Narrator(context.applicationContext) }
        DisposableEffect(narrator) { onDispose { narrator.shutdown() } }

        val ui by vm.ui.collectAsState()
        val model by vm.renderModel.collectAsState()
        val analysisInfo by controller.analyzer.analysisInfo.collectAsState()

        DisposableEffect(controller) {
            // Created OFF the main thread so entering the game doesn't freeze on model load.
            var pipeline: ObjectDetectionPipeline? = null
            val initJob = scope.launch {
                val p = withContext(Dispatchers.Default) { ObjectDetectionPipeline(context.applicationContext) }
                pipeline = p
                // Enable pose for Body/Mixed sessions when a pose backend exists; OBJECTS mode keeps
                // pose fully disabled so the object-only path is untouched (§20 invariant 12).
                p.setPoseActive(SessionConfig.mode != PlayMode.OBJECTS && p.poseAvailable)
                controller.analyzer.frameSink = { p.onFrame(it) }
                combine(p.worldState, p.capabilityReport) { world, report ->
                    world to report
                }.collect { (world, report) ->
                    // The engine composes the device-independent RenderModel from this frame; the
                    // overlay + HUD below render only that (§3.6, §20 invariant 21).
                    vm.submitFrame(world, report.capability)
                }
            }
            onDispose {
                controller.analyzer.frameSink = null
                initJob.cancel()
                pipeline?.close()
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

        // When the session ends, leave for the result screen. In PARTY mode, first fold this
        // round's outcome into the live PartySession (§25) — presentation-layer bookkeeping only,
        // never a new PASS path (§20 invariant 24).
        LaunchedEffect(ui.sessionOver) {
            if (ui.sessionOver) {
                PartyRuntime.active?.let { party ->
                    val results = SessionResults.results
                    val gained = results.sumOf { it.score }
                    val passed = results.any { it.passed }
                    val type = results.lastOrNull()?.type
                        ?.let { runCatching { ChallengeType.valueOf(it) }.getOrNull() }
                        ?: ChallengeType.LAST_RESORT
                    party.completeRound(type, passed, gained)
                }
                onFinish()
            }
        }

        // Read the game out loud at the start of every challenge, for all tiers (§3a). Coaching
        // hints are still spoken for TODDLER only (§10).
        val toddler = SessionConfig.ageBand == AgeBand.TODDLER
        LaunchedEffect(ui.challengeIndex) {
            if (ui.challengeIndex > 0 && ui.status != PlayStatus.COMPOSING) {
                narrator.speak(ui.instruction)
            }
        }
        LaunchedEffect(ui.coachingHint) {
            if (toddler) ui.coachingHint?.let { narrator.speak(it, interrupt = false) }
        }

        // PARTY round pacing (§25) — a visible countdown that auto-advances the turn so the room
        // never stalls. TODDLER has no clock (RoundPacer returns null): the participant plays until
        // they choose Finish, matching §10's "no timer, no clock pressure".
        val partyRoundMs = PartyRuntime.active?.roundDurationMs
        var partyRemainingMs by remember(partyRoundMs) { mutableStateOf(partyRoundMs) }
        LaunchedEffect(partyRoundMs) {
            val total = partyRoundMs ?: return@LaunchedEffect
            var remaining = total
            while (remaining > 0) {
                delay(TICK_MS)
                remaining = (remaining - TICK_MS).coerceAtLeast(0L)
                partyRemainingMs = remaining
            }
            vm.finish()
        }

        // Looping animation that drives the visual-first coaching cues (§26): highlights breathe,
        // the arrow's chevron travels toward the goal.
        val cueAnim = rememberInfiniteTransition(label = "cues")
        val pulse by cueAnim.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(900, easing = LinearEasing), RepeatMode.Reverse),
            label = "pulse"
        )
        val arrowPhase by cueAnim.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(1100, easing = LinearEasing), RepeatMode.Restart),
            label = "arrow"
        )

        Box(modifier = Modifier.fillMaxSize()) {
            CameraPreview(controller = controller, modifier = Modifier.fillMaxSize())
            // Zones + player skeletons still use the canvas; objects use the minimal tracker (no boxes).
            OverlayCanvas(
                analysisInfo = analysisInfo,
                isFrontCamera = controller.isFrontCamera,
                modifier = Modifier.fillMaxSize(),
                showCalibration = false,
                detections = emptyList(),
                zones = MobileTarget.zones(model),
                players = MobileTarget.players(model)
            )
            MinimalTrackerOverlay(
                analysisInfo = analysisInfo,
                isFrontCamera = controller.isFrontCamera,
                targets = MobileTarget.trackTargets(model),
                modifier = Modifier.fillMaxSize()
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

            // Dev overlay (§13 S9): the difficulty "why" line — a one-glance answer to "what does
            // hard actually look like?". Off by default (AppSettings.devOverlayEnabled); presentation only.
            val devOverlay by com.cognex.realplay.engine.AppSettings.devOverlayEnabled.collectAsState()
            if (devOverlay && ui.difficultyExplanation.isNotEmpty()) {
                Text(
                    text = "${ui.winnerId} · ${ui.difficultyExplanation}",
                    color = Color(0xCCFFFFFF),
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(start = 12.dp, top = 96.dp)
                )
            }

            // Coaching toast sits just above the controls.
            CoachingToast(
                hint = model.hud.coachingHint,
                visible = model.hud.status == PlayStatus.COACHING,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 108.dp)
            )

            // Live mission progress bar — fills from the verifier's step progress and completes the
            // level automatically when full (§3a). Smoothly animated; hidden between challenges.
            GameProgressBar(
                progress = missionProgress(model.hud),
                visible = model.hud.status == PlayStatus.PLAYING || model.hud.status == PlayStatus.COACHING,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 96.dp)
            )

            // Bottom controls.
            val freeform by com.cognex.realplay.engine.AppSettings.verifierEnabled.collectAsState()
            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                RpOutlinedButton(text = "Back", onClick = onBack, modifier = Modifier.weight(1f))
                if (freeform) {
                    // Freeform (no verifier): advance manually — no timer, no scoring.
                    RpButton(text = "Next level", onClick = { vm.nextFreeform() }, modifier = Modifier.weight(1.4f))
                    RpOutlinedButton(text = "Finish", onClick = { vm.finish() }, modifier = Modifier.weight(1f))
                } else {
                    RpOutlinedButton(
                        text = "Retry (${ui.retriesLeft})",
                        onClick = { vm.retry() },
                        enabled = ui.retriesLeft > 0,
                        modifier = Modifier.weight(1f)
                    )
                    RpButton(text = "Finish", onClick = { vm.finish() }, modifier = Modifier.weight(1f))
                }
            }

            // Skip escape hatch (top-right, below the HUD) — some scenes can't complete a given
            // game (missing object, impossible arrangement). Solo only, so it never collides with
            // the PARTY round badge; visible only while a game is actually in play.
            if ((model.hud.status == PlayStatus.PLAYING || model.hud.status == PlayStatus.COACHING) &&
                PartyRuntime.active == null
            ) {
                SkipPill(
                    onClick = { vm.skip() },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 108.dp, end = 16.dp)
                )
            }

            // Pre-challenge briefing + countdown (on top of everything).
            BriefingOverlay(
                challengeKey = model.hud.challengeIndex,
                instruction = model.hud.instruction,
                onTick = { sound.countdown() },
                onGo = { sound.start() }
            )

            // "Creating your game…" — shown while the cloud/on-device model composes the next game.
            ComposingOverlay(
                visible = model.hud.status == PlayStatus.COMPOSING,
                message = model.hud.instruction
            )

            // Success celebration — particle burst plus a calm "Level Complete" panel that holds
            // for the full ~2s advance beat (§3a smooth transitions).
            SuccessBurst(
                visible = model.hud.status == PlayStatus.PASSED,
                perfect = model.hud.perfect,
                gainedPoints = model.hud.lastGain,
                triggerKey = if (model.hud.status == PlayStatus.PASSED) model.hud.challengeIndex else 0
            )
            LevelCompleteOverlay(
                visible = model.hud.status == PlayStatus.PASSED,
                perfect = model.hud.perfect,
                gainedPoints = model.hud.lastGain
            )

            // TODDLER-only break suggestion after 5 continuous minutes (§10 rule 8) — a gentle
            // banner, never a timer or a failure state.
            if (ui.breakSuggested) {
                BreakSuggestionBanner(onDismiss = { vm.dismissBreak() })
            }

            // PARTY round countdown badge (§25) — visible pacing, never a challenge time limit.
            partyRemainingMs?.let { remaining ->
                PartyRoundBadge(remainingMs = remaining, modifier = Modifier.align(Alignment.TopEnd))
            }
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
    PlayStatus.COMPOSING -> "…"
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

/** A calm, dismissible "time for a break?" banner (Architecture §10 rule 8) — never a countdown. */
@Composable
private fun BreakSuggestionBanner(onDismiss: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = 140.dp),
        contentAlignment = Alignment.TopCenter
    ) {
        val shape = RoundedCornerShape(RpRadius.md)
        Row(
            modifier = Modifier
                .shadow(6.dp, shape, clip = false)
                .background(RpNavyElevated.copy(alpha = 0.95f), shape)
                .padding(horizontal = 18.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Time for a little break? \uD83C\uDF1F",
                color = Color(0xFFBFF7EC),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium
            )
            RpOutlinedButton(text = "Keep playing", onClick = onDismiss)
        }
    }
}

/** A compact "Skip" pill (top-right) that abandons the current game and composes a fresh one. */
@Composable
private fun SkipPill(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(RpRadius.xl)
    Row(
        modifier = modifier
            .shadow(4.dp, shape, clip = false)
            .clip(shape)
            .background(RpNavyDeep.copy(alpha = 0.82f))
            .border(1.dp, Color(0x33FFFFFF), shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "Skip game",
            color = Color.White,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.size(6.dp))
        Text(text = "\u23ED", color = Color(0xFF7CD4FF), style = MaterialTheme.typography.labelLarge)
    }
}

/** PARTY round countdown badge (Architecture §25) — visible pacing, presentation only. */
@Composable
private fun PartyRoundBadge(remainingMs: Long, modifier: Modifier = Modifier) {
    val seconds = (remainingMs / 1000L).coerceAtLeast(0L)
    val shape = RoundedCornerShape(RpRadius.md)
    Box(
        modifier = modifier
            .padding(top = 24.dp, end = 16.dp)
            .shadow(4.dp, shape, clip = false)
            .background(RpNavyDeep.copy(alpha = 0.8f), shape)
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        Text(
            text = "0:${seconds.toString().padStart(2, '0')}",
            color = Color.White,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Black
        )
    }
}

/**
 * The between-games beat (Architecture §6.5 v3.7, §3a). While the next game is being prepared this
 * shows a calm, on-brand "Get ready" with pulsing dots over the live camera — never a spinner or
 * any "loading"/"generating" language.
 */
@Composable
private fun ComposingOverlay(visible: Boolean, message: String) {
    androidx.compose.animation.AnimatedVisibility(
        visible = visible,
        enter = androidx.compose.animation.fadeIn(),
        exit = androidx.compose.animation.fadeOut()
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0x800B1220)),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                PulsingDots()
                Text(
                    text = "Get ready",
                    color = Color.White,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

/** Three softly pulsing dots — a calm "next game is coming" cue, not a loading spinner. */
@Composable
private fun PulsingDots() {
    val t = rememberInfiniteTransition(label = "dots")
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        for (i in 0..2) {
            val a by t.animateFloat(
                initialValue = 0.3f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    tween(560, delayMillis = i * 160, easing = LinearEasing),
                    RepeatMode.Reverse
                ),
                label = "dot$i"
            )
            Box(
                modifier = Modifier
                    .size(14.dp)
                    .graphicsLayer {
                        alpha = a
                        val s = 0.7f + 0.3f * a
                        scaleX = s; scaleY = s
                    }
                    .background(Color(0xFF25E0C8), androidx.compose.foundation.shape.CircleShape)
            )
        }
    }
}

/**
 * The "Level Complete" beat (§3a) — a calm panel that scales/fades in on a pass and holds for the
 * full ~2s advance window, so completing a game feels deliberate and celebratory instead of rushing
 * straight into the next one. Sits above the [SuccessBurst] particles.
 */
@Composable
private fun LevelCompleteOverlay(visible: Boolean, perfect: Boolean, gainedPoints: Int) {
    val appear by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = androidx.compose.animation.core.tween(
            durationMillis = 420,
            easing = androidx.compose.animation.core.FastOutSlowInEasing
        ),
        label = "levelComplete"
    )
    if (appear <= 0.01f) return
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0B1220).copy(alpha = 0.80f * appear)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.graphicsLayer {
                alpha = appear
                scaleX = 0.85f + 0.15f * appear
                scaleY = 0.85f + 0.15f * appear
            }
        ) {
            Text(
                text = if (perfect) "PERFECT!" else "LEVEL COMPLETE",
                color = if (perfect) Color(0xFFFFD24B) else Color(0xFF25E0C8),
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Black
            )
            if (gainedPoints > 0) {
                Text(
                    text = "+$gainedPoints points",
                    color = Color.White,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            }
            Text(
                text = "Get ready for the next one…",
                color = Color(0xFF9FB2C4),
                style = MaterialTheme.typography.bodyLarge
            )
        }
    }
}

/** Overall mission progress 0→1: completed steps plus the current step's live fraction. */
private fun missionProgress(hud: Hud): Float {
    val steps = hud.stepCount.coerceAtLeast(1)
    return ((hud.completedSteps + hud.stepProgress) / steps).coerceIn(0f, 1f)
}

/**
 * The live mission progress bar (§3a) — a smooth bottom bar that fills as the verifier reports
 * progress and reads "Done!" at 100%, right before the level auto-completes into the celebration.
 */
@Composable
private fun GameProgressBar(progress: Float, visible: Boolean, modifier: Modifier = Modifier) {
    val anim by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = tween(300, easing = FastOutSlowInEasing),
        label = "missionProgress"
    )
    androidx.compose.animation.AnimatedVisibility(
        visible = visible,
        enter = androidx.compose.animation.fadeIn(),
        exit = androidx.compose.animation.fadeOut(),
        modifier = modifier
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 28.dp)) {
            Text(
                text = when {
                    anim >= 0.999f -> "Done!"
                    anim > 0.04f -> "Keep going…"
                    else -> "Go!"
                },
                color = Color(0xFFBFF7EC),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(bottom = 6.dp)
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(14.dp)
                    .clip(RoundedCornerShape(50))
                    .background(Color(0x66000000))
                    .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(50))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(anim.coerceIn(0f, 1f))
                        .height(14.dp)
                        .clip(RoundedCornerShape(50))
                        .background(Brush.horizontalGradient(listOf(Color(0xFF25E0C8), Color(0xFF57E39B))))
                )
            }
        }
    }
}

private const val TICK_MS = 250L
