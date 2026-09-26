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
import com.cognex.realplay.perception.ObjectDetectionPipeline
import com.cognex.realplay.ui.camera.CameraPermissionGate
import com.cognex.realplay.ui.camera.CameraPreview
import com.cognex.realplay.ui.overlay.OverlayCanvas
import com.cognex.realplay.ui.overlay.OverlayDetection
import com.cognex.realplay.world.ColorTag
import com.cognex.realplay.world.RichnessBranch
import com.cognex.realplay.world.SceneCapabilityReport
import com.cognex.realplay.world.WorldState
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

        var showCalibration by remember { mutableStateOf(true) }
        var showCapability by remember { mutableStateOf(true) }
        var world by remember { mutableStateOf(WorldState.EMPTY) }
        var capReport by remember { mutableStateOf<SceneCapabilityReport?>(null) }

        // Build the detection pipeline once for the lifetime of this screen.
        DisposableEffect(controller) {
            val pipeline = ObjectDetectionPipeline(context.applicationContext)
            controller.analyzer.frameSink = { pipeline.onFrame(it) }
            val worldJob = scope.launch { pipeline.worldState.collect { world = it } }
            val capJob = scope.launch { pipeline.capabilityReport.collect { capReport = it } }
            onDispose {
                controller.analyzer.frameSink = null
                worldJob.cancel()
                capJob.cancel()
                pipeline.close()
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
                    if (obj.ambiguous) append("  ?")       // ambiguous
                },
                color = colorForTag(obj.color)
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

            // Top readout — FPS + analysis geometry + object count + frame quality.
            val info = analysisInfo
            val readout = buildString {
                append("FPS ")
                append(String.format("%.1f", fps))
                if (info != null) {
                    append("   \u2022   ${info.imageW}\u00d7${info.imageH}")
                }
                append("   \u2022   ${world.objects.size} obj")
                if (!world.quality.good) {
                    append("   \u2022   ${world.quality.reason ?: "POOR"}")
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

            // Dev capability panel (§S3.5.4) — every richness term, active branch, player terms,
            // and the three capability flags, so they can be watched changing live.
            val report = capReport
            if (showCapability && report != null) {
                Text(
                    text = capabilityReadout(report),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(start = 12.dp, top = 96.dp)
                        .background(Color(0xAA000000), RoundedCornerShape(8.dp))
                        .padding(horizontal = 10.dp, vertical = 8.dp)
                )
            }

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
                        selected = showCapability,
                        onClick = { showCapability = !showCapability },
                        label = { Text("Capability") }
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
                    val usable = world.quality.good && world.objects.isNotEmpty()
                    Button(
                        onClick = onReady,
                        enabled = usable,
                        modifier = Modifier.weight(1f)
                    ) { Text(if (usable) "Ready" else "Get set…") }
                }
            }
        }
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
