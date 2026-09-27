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
    // Live dots keyed by object. Each keeps a "last seen" time so a brief detection miss COASTS
    // (stays put, then fades) instead of blinking out — the key to steady, box-like tracking.
    val tracked = remember { mutableMapOf<String, Dot>() }
    var latest by remember { mutableStateOf(targets) }
    latest = targets
    var tick by remember { mutableIntStateOf(0) }

    LaunchedFrameLoop {
        val now = System.nanoTime()
        latest.forEach { t ->
            val d = tracked[t.key]
            if (d == null) {
                tracked[t.key] = Dot(Offset(t.nx, t.ny), now, t.label, t.color)
            } else {
                d.pos = Offset(d.pos.x + (t.nx - d.pos.x) * SMOOTH, d.pos.y + (t.ny - d.pos.y) * SMOOTH)
                d.lastSeen = now
                d.label = t.label
                d.color = t.color
            }
        }
        // Drop only dots that have been gone past the whole coast window.
        val gone = tracked.entries.filter { now - it.value.lastSeen > COAST_NANOS }.map { it.key }
        gone.forEach { tracked.remove(it) }
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
        val now = System.nanoTime()
        val pulse = (sin(now / 1_000_000_000.0 * 2.2).toFloat() + 1f) / 2f
        val dotR = 7.dp.toPx() + pulse * 2f
        val haloR = 16.dp.toPx() + pulse * 4f
        val labelGap = 6.dp.toPx()
        val labelSize = 13.dp.toPx()
        tracked.values.forEach { d ->
            val age = now - d.lastSeen
            val fade = if (age <= FRESH_NANOS) 1f
            else (1f - (age - FRESH_NANOS).toFloat() / (COAST_NANOS - FRESH_NANOS)).coerceIn(0f, 1f)
            val p = mapper.mapPoint(d.pos.x, d.pos.y)
            val c = Offset(p.x, p.y)
            drawCircle(color = d.color.copy(alpha = 0.22f * fade), radius = haloR, center = c)
            drawCircle(color = d.color.copy(alpha = fade), radius = dotR, center = c)
            drawCircle(color = Color.White.copy(alpha = fade), radius = dotR * 0.38f, center = c)
            if (showLabels && d.label.isNotBlank()) {
                drawContext.canvas.nativeCanvas.apply {
                    val paint = android.graphics.Paint().apply {
                        color = android.graphics.Color.argb((255 * fade).toInt().coerceIn(0, 255), 255, 255, 255)
                        textSize = labelSize
                        isAntiAlias = true
                        textAlign = android.graphics.Paint.Align.CENTER
                        setShadowLayer(6f, 0f, 0f, android.graphics.Color.BLACK)
                        isFakeBoldText = true
                    }
                    drawText(d.label, c.x, c.y - haloR - labelGap, paint)
                }
            }
        }
    }
}

/** A tracked object's on-screen dot; [lastSeen] drives the coast/fade so it never blinks. */
private class Dot(var pos: Offset, var lastSeen: Long, var label: String, var color: Color)

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
private const val FRESH_NANOS = 320_000_000L   // full opacity for 320ms after last detection
private const val COAST_NANOS = 800_000_000L   // then fade out by 800ms — a brief miss won't blink
