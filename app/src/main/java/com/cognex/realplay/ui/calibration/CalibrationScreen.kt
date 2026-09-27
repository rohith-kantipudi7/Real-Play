package com.cognex.realplay.ui.calibration

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.cognex.realplay.camera.CameraController
import com.cognex.realplay.perception.ObjectDetectionPipeline
import com.cognex.realplay.ui.camera.CameraPermissionGate
import com.cognex.realplay.ui.camera.CameraPreview
import com.cognex.realplay.ui.common.RpButton
import com.cognex.realplay.ui.common.RpChip
import com.cognex.realplay.ui.common.RpOutlinedButton
import com.cognex.realplay.ui.overlay.MinimalTrackerOverlay
import com.cognex.realplay.ui.overlay.OverlayCanvas
import com.cognex.realplay.ui.overlay.OverlayDetection
import com.cognex.realplay.ui.overlay.TrackTarget
import com.cognex.realplay.ui.theme.RpNavyDeep
import com.cognex.realplay.ui.theme.RpRadius
import com.cognex.realplay.ui.theme.RpSpace
import com.cognex.realplay.world.ColorTag
import com.cognex.realplay.world.RichnessBranch
import com.cognex.realplay.world.SceneCapabilityReport
import com.cognex.realplay.world.WorldState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * S1/S2 calibration: live camera preview with the coordinate-mapper calibration overlay, live
 * detection boxes (label + confidence + colour), and an FPS/resolution readout.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CalibrationScreen(onReady: () -> Unit, onBack: () -> Unit) {
    CameraPermissionGate {
        val context = LocalContext.current
        val scope = rememberCoroutineScope()
        val controller = remember { CameraController(context.applicationContext) }

        val fps by controller.analyzer.fps.collectAsState()
        val analysisInfo by controller.analyzer.analysisInfo.collectAsState()

        var showCapability by remember { mutableStateOf(com.cognex.realplay.engine.AppSettings.devOverlayEnabled.value) }
        var world by remember { mutableStateOf(WorldState.EMPTY) }
        var capReport by remember { mutableStateOf<SceneCapabilityReport?>(null) }

        // Build the detection pipeline once for the lifetime of this screen. Created OFF the main
        // thread so tapping Play opens the camera instantly instead of freezing on model load.
        DisposableEffect(controller) {
            var pipeline: ObjectDetectionPipeline? = null
            val initJob = scope.launch {
                val p = withContext(Dispatchers.Default) { ObjectDetectionPipeline(context.applicationContext) }
                pipeline = p
                controller.analyzer.frameSink = { p.onFrame(it) }
                launch { p.worldState.collect { world = it } }
                launch { p.capabilityReport.collect { capReport = it } }
            }
            onDispose {
                controller.analyzer.frameSink = null
                initJob.cancel()
                pipeline?.close()
                world = WorldState.EMPTY
                capReport = null
            }
        }
        DisposableEffect(controller) {
            onDispose { controller.shutdown() }
        }

        val overlayDetections = world.objects.map { obj ->
            OverlayDetection(
                left = obj.box.left, top = obj.box.top, right = obj.box.right, bottom = obj.box.bottom,
                label = buildString {
                    append("#${obj.trackId} ")
                    append(obj.label)
                    obj.color?.let { append("  ${it.name.lowercase()}") }
                    if (obj.stable) append("  \u25CF")     // ● settled
                    if (obj.stale) append("  ~")           // coasting
                },
                // Ambiguous reads as an amber box instead of a "?" glued onto the label text —
                // no extra glyph to collide with the label on small/dense boxes.
                color = if (obj.ambiguous) AmbiguousBoxColor else colorForTag(obj.color)
            )
        }

        val devOverlay by com.cognex.realplay.engine.AppSettings.devOverlayEnabled.collectAsState()
        // Detected object types (deduped by label) that have settled into the top list.
        val listed = remember { mutableStateListOf<String>() }
        // Chips currently flying up from the camera into the list.
        val flying = remember { mutableStateListOf<FlyingSpec>() }
        // Labels already listed or in flight, so each type flies up exactly once.
        val seen = remember { mutableSetOf<String>() }
        // Wall-clock of the last frame each label was seen, for debounced removal from the list.
        val lastSeen = remember { mutableMapOf<String, Long>() }
        var nextId by remember { mutableStateOf(0L) }

        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val widthPx = constraints.maxWidth.toFloat()
            val heightPx = constraints.maxHeight.toFloat()
            val targetX = widthPx / 2f
            val targetY = heightPx * 0.14f

            CameraPreview(controller = controller, modifier = Modifier.fillMaxSize())

            // Fly a chip up from each newly-settled object's position into the list, and remove a
            // chip when its object has been gone for a moment (debounced against flicker).
            LaunchedEffect(world) {
                val info = analysisInfo ?: return@LaunchedEffect
                if (widthPx < 1f || heightPx < 1f) return@LaunchedEffect
                val mapper = com.cognex.realplay.camera.CoordinateMapper(
                    imageW = info.imageW, imageH = info.imageH,
                    rotationDegrees = info.rotationDegrees,
                    isFrontCamera = controller.isFrontCamera,
                    viewW = widthPx.toInt(), viewH = heightPx.toInt(),
                    scaleType = com.cognex.realplay.camera.ScaleType.FILL_CENTER
                )
                val now = System.currentTimeMillis()
                world.objects.forEach { obj ->
                    val label = obj.label
                    if (label.isNotBlank()) {
                        // Any present object (even briefly unstable) keeps its chip alive so the
                        // list doesn't flicker; only settle-fly a NEW label once it's stable.
                        lastSeen[label] = now
                        if (obj.stable && label !in seen) {
                            seen.add(label)
                            val p = mapper.mapPoint((obj.box.left + obj.box.right) / 2f, (obj.box.top + obj.box.bottom) / 2f)
                            flying.add(FlyingSpec(nextId++, label, p.x, p.y))
                        }
                    }
                }
                val gone = listed.filter { now - (lastSeen[it] ?: 0L) > 2000L }
                if (gone.isNotEmpty()) {
                    listed.removeAll(gone)
                    gone.forEach { seen.remove(it); lastSeen.remove(it) }
                }
            }

            // Minimal live tracker dots on EVERY tracked object (no boxes) — showing all of them
            // (not only "stable") keeps the markers steady, the way the old boxes felt.
            if (!devOverlay) {
                val trackTargets = world.objects.map { obj ->
                    TrackTarget(
                        key = obj.trackId.toString(),
                        nx = (obj.box.left + obj.box.right) / 2f,
                        ny = (obj.box.top + obj.box.bottom) / 2f,
                        label = "",
                        color = if (obj.ambiguous) AmbiguousBoxColor else colorForTag(obj.color)
                    )
                }
                MinimalTrackerOverlay(
                    analysisInfo = analysisInfo,
                    isFrontCamera = controller.isFrontCamera,
                    targets = trackTargets,
                    showLabels = false,
                    modifier = Modifier.fillMaxSize()
                )
            }

            // Developer overlay (boxes + raw counts) — off by default (Settings → Developer).
            if (devOverlay) {
                OverlayCanvas(
                    analysisInfo = analysisInfo,
                    isFrontCamera = controller.isFrontCamera,
                    modifier = Modifier.fillMaxSize(),
                    showCalibration = false,
                    detections = overlayDetections
                )
                Text(
                    text = "FPS ${String.format("%.1f", fps)} \u00b7 ${world.objects.size} obj",
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(start = 12.dp, top = 130.dp)
                        .background(RpNavyDeep.copy(alpha = 0.7f), RoundedCornerShape(RpRadius.sm))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }

            // Top scrim so the list reads over any background.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(240.dp)
                    .background(Brush.verticalGradient(listOf(Color(0xE6000000), Color.Transparent)))
            )

            // The "Detected" list, filling one chip at a time.
            Column(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .padding(top = 44.dp, start = RpSpace.md, end = RpSpace.md),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = if (listed.isEmpty()) "Scanning your table\u2026" else "Here's what I can see",
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(12.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    listed.forEach { label -> key(label) { ScanChip(label) } }
                }
            }

            // Chips in flight from the camera to the list.
            flying.toList().forEach { spec ->
                key(spec.id) {
                    FlyingChip(
                        spec = spec,
                        targetX = targetX,
                        targetY = targetY,
                        onArrived = {
                            if (spec.label !in listed) listed.add(spec.label)
                            flying.remove(spec)
                        }
                    )
                }
            }

            // Bottom scrim + controls.
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(200.dp)
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xE6000000))))
            )
            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(RpSpace.lg),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                RpOutlinedButton(text = "Back", onClick = onBack, modifier = Modifier.weight(1f))
                RpButton(
                    text = if (listed.isEmpty()) "Point at objects\u2026" else "Play with these",
                    onClick = onReady,
                    enabled = listed.isNotEmpty(),
                    modifier = Modifier.weight(1.4f)
                )
            }
        }
    }
}

/** The difficulty context used to score the capability card's ranked list (§8, §15). */
private fun capabilityCardContext(): com.cognex.realplay.challenge.GenerationContext {
    val band = com.cognex.realplay.engine.SessionConfig.ageBand
    val tier = com.cognex.realplay.challenge.Tier.EASY
    return com.cognex.realplay.challenge.GenerationContext(
        ageBand = band,
        effectiveTier = tier,
        knobs = com.cognex.realplay.challenge.DifficultyKnobs.forTier(tier, spread = 0.5f, stability = 1f),
        trackOnlyMode = false
    )
}

/** A plain-language readiness line shown instead of raw FPS/geometry numbers (default view). */
private fun friendlyScanReadout(world: WorldState): String = when {
    world.objects.isEmpty() -> "Point the camera at your play area\u2026"
    !world.quality.good -> "Having trouble seeing clearly \u2014 try more light or less clutter"
    else -> {
        val n = world.objects.size
        "$n thing${if (n == 1) "" else "s"} spotted \u2014 looking good!"
    }
}

/** Formats the live [SceneCapabilityReport] for the dev overlay (§S3.5.4). */
private fun capabilityReadout(report: SceneCapabilityReport): String {
    val cap = report.capability
    val r = report.richness
    val terms = r.terms.entries.joinToString("  ") { "${it.key} ${String.format("%.2f", it.value)}" }
    val branch = when (r.branch) {
        RichnessBranch.BASE -> "BASE"
        RichnessBranch.A_NO_PLAYERS -> "A (no players)"
        RichnessBranch.B_NO_MOVABLE -> "B (no movable)"
    }
    return buildString {
        append("richness ${String.format("%.2f", cap.richness)}   branch $branch\n")
        append(terms).append('\n')
        append("mov ${cap.movableCount}  hand ${cap.handheldCount}  cont ${cap.containerCount}")
        append("  land ${cap.landmarkCount}  name ${cap.nameableCount}\n")
        append("colors ${cap.distinctColors.size}  spread ${String.format("%.2f", cap.spread)}")
        append("  stable ${String.format("%.2f", cap.stability)}\n")
        append("pose ${String.format("%.2f", cap.poseVariety)}")
        append("  motion ${String.format("%.2f", cap.motionRange)}")
        append("  cover ${String.format("%.2f", cap.frameCoverage)}\n")
        append("semantic ${if (cap.semanticLabelsAvailable) "Y" else "N"}")
        append("  trackOnly ${if (cap.trackOnlyMode) "Y" else "N"}")
        append("  planar ${if (cap.planarSurfaceAvailable) "Y" else "N"}")
    }
}

/** Maps a [ColorTag] to a display colour for the detection box. */
private val AmbiguousBoxColor = Color(0xFFFFB13A) // tangerine — "not sure yet", no glyph needed

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
