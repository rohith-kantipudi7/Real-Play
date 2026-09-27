package com.cognex.realplay.ui.overlay

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.unit.dp
import com.cognex.realplay.camera.AnalysisInfo
import com.cognex.realplay.camera.CoordinateMapper
import com.cognex.realplay.camera.ScaleType
import kotlin.math.sin

/** One object to track, in NORMALIZED analysis-space coordinates. [key] must be stable per object. */
data class TrackTarget(
    val key: String,
    val nx: Float,
    val ny: Float,
    val label: String,
    val color: Color = Color(0xFF25E0C8)
)

/**
 * A minimal, smooth object tracker (replaces bounding boxes). Each object is a gliding dot with a
 * soft halo and a small label — it interpolates toward the latest detection every display frame, so
 * the marker flows at 60fps even though detection updates arrive slower. No boxes, no jitter.
 */
@Composable
fun MinimalTrackerOverlay(
    analysisInfo: AnalysisInfo?,
    isFrontCamera: Boolean,
    targets: List<TrackTarget>,
    modifier: Modifier = Modifier,
    showLabels: Boolean = true
) {
    // Smoothed normalized positions, keyed by object. Survives across frames for interpolation.
    val smoothed = remember { mutableMapOf<String, Offset>() }
    var latest by remember { mutableStateOf(targets) }
    latest = targets
    var tick by remember { mutableIntStateOf(0) }

    LaunchedFrameLoop {
        val tgts = latest
        val keys = tgts.mapTo(HashSet()) { it.key }
        smoothed.keys.retainAll(keys)
        tgts.forEach { t ->
            val cur = smoothed[t.key]
            smoothed[t.key] = if (cur == null) Offset(t.nx, t.ny)
            else Offset(cur.x + (t.nx - cur.x) * SMOOTH, cur.y + (t.ny - cur.y) * SMOOTH)
        }
        tick++
    }

    Canvas(modifier) {
        @Suppress("UNUSED_EXPRESSION") tick // read so the draw re-runs each animation frame
        val info = analysisInfo ?: return@Canvas
        if (size.width <= 0f || size.height <= 0f) return@Canvas
        val mapper = CoordinateMapper(
            imageW = info.imageW, imageH = info.imageH,
            rotationDegrees = info.rotationDegrees, isFrontCamera = isFrontCamera,
            viewW = size.width.toInt(), viewH = size.height.toInt(),
            scaleType = ScaleType.FILL_CENTER
        )
        val pulse = (sin(System.nanoTime() / 1_000_000_000.0 * 2.2).toFloat() + 1f) / 2f
        val dotR = 7.dp.toPx() + pulse * 2f
        val haloR = 16.dp.toPx() + pulse * 4f
        latest.forEach { t ->
            val n = smoothed[t.key] ?: Offset(t.nx, t.ny)
            val p = mapper.mapPoint(n.x, n.y)
            val c = Offset(p.x, p.y)
            drawCircle(color = t.color.copy(alpha = 0.22f), radius = haloR, center = c)
            drawCircle(color = t.color, radius = dotR, center = c)
            drawCircle(color = Color.White, radius = dotR * 0.38f, center = c)
            if (showLabels && t.label.isNotBlank()) {
                drawContext.canvas.nativeCanvas.apply {
                    val paint = android.graphics.Paint().apply {
                        color = android.graphics.Color.WHITE
                        textSize = 13.dp.toPx()
                        isAntiAlias = true
                        textAlign = android.graphics.Paint.Align.CENTER
                        setShadowLayer(6f, 0f, 0f, android.graphics.Color.BLACK)
                        isFakeBoldText = true
                    }
                    drawText(t.label, c.x, c.y - haloR - 6f, paint)
                }
            }
        }
    }
}

/** Runs [block] once per display frame (a lightweight animation clock). */
@Composable
private fun LaunchedFrameLoop(block: () -> Unit) {
    androidx.compose.runtime.LaunchedEffect(Unit) {
        while (true) {
            withFrameNanos { }
            block()
        }
    }
}

private const val SMOOTH = 0.28f
