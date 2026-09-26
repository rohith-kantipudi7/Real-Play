package com.cognex.realplay.ui.overlay

import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import com.cognex.realplay.camera.AnalysisInfo
import com.cognex.realplay.camera.CoordinateMapper
import com.cognex.realplay.camera.ScaleType
import com.cognex.realplay.camera.ViewPoint
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/**
 * A drawable visual-first coaching cue in NORMALIZED analysis-space (Architecture §26). Produced by
 * [com.cognex.realplay.ui.present.MobileTarget] from the pure `RenderModel.cues` track; drawn by
 * [CueCanvas] with a live pulse animation. Presentation only — cues never affect a verdict.
 */
sealed interface OverlayCue {
    /** "This one" — a pulsing box around the target actor. [strong] is the primary target. */
    data class PulseBox(
        val left: Float, val top: Float, val right: Float, val bottom: Float,
        val color: Color, val strong: Boolean
    ) : OverlayCue

    /** An animated route from → to (a travelling chevron shows the direction). */
    data class Arrow(
        val fromX: Float, val fromY: Float, val toX: Float, val toY: Float, val color: Color
    ) : OverlayCue

    /** The target zone, breathing. */
    data class ZonePulse(val polygon: List<Pair<Float, Float>>, val color: Color) : OverlayCue

    /** A language-free goal glyph (emoji) centred in its placard box. */
    data class Glyph(val cx: Float, val cy: Float, val text: String) : OverlayCue

    /** A looping ghost-demo stage (pose games, S8) — a faint dashed silhouette box. */
    data class GhostBox(
        val left: Float, val top: Float, val right: Float, val bottom: Float
    ) : OverlayCue
}

/**
 * Draws the visual-first coaching cue track over the camera (Architecture §26). [pulse] (0..1) and
 * [arrowPhase] (0..1) come from an infinite transition in the screen, so highlights breathe and the
 * arrow's chevron travels toward the goal. Builds a fresh [CoordinateMapper] each frame exactly like
 * [OverlayCanvas], so cues track the image through rotation and the FILL_CENTER crop.
 */
@Composable
fun CueCanvas(
    analysisInfo: AnalysisInfo?,
    isFrontCamera: Boolean,
    cues: List<OverlayCue>,
    pulse: Float,
    arrowPhase: Float,
    modifier: Modifier = Modifier
) {
    if (cues.isEmpty()) return
    Canvas(modifier = modifier) {
        val info = analysisInfo ?: return@Canvas
        if (size.width <= 0f || size.height <= 0f) return@Canvas

        val mapper = CoordinateMapper(
            imageW = info.imageW,
            imageH = info.imageH,
            rotationDegrees = info.rotationDegrees,
            isFrontCamera = isFrontCamera,
            viewW = size.width.toInt(),
            viewH = size.height.toInt(),
            scaleType = ScaleType.FILL_CENTER
        )

        cues.forEach { cue ->
            when (cue) {
                is OverlayCue.ZonePulse -> drawZonePulse(mapper, cue, pulse)
                is OverlayCue.PulseBox -> drawPulseBox(mapper, cue, pulse)
                is OverlayCue.GhostBox -> drawGhostBox(mapper, cue, pulse)
                is OverlayCue.Arrow -> drawArrow(mapper, cue, arrowPhase, pulse)
                is OverlayCue.Glyph -> drawGlyph(mapper, cue)
            }
        }
    }
}

private fun DrawScope.drawPulseBox(mapper: CoordinateMapper, cue: OverlayCue.PulseBox, pulse: Float) {
    val r = mapper.mapRect(cue.left, cue.top, cue.right, cue.bottom)
    val base = if (cue.strong) 6f else 3f
    val width = base + (if (cue.strong) 6f else 3f) * pulse
    val alpha = (if (cue.strong) 0.55f else 0.35f) + 0.45f * pulse
    // Outer breathing halo grows with the pulse.
    val grow = (if (cue.strong) 26f else 14f) * pulse
    drawRoundRect(
        color = cue.color.copy(alpha = alpha),
        topLeft = Offset(r.left - grow, r.top - grow),
        size = Size(r.width + grow * 2, r.height + grow * 2),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(24f, 24f),
        style = Stroke(width = width)
    )
}

private fun DrawScope.drawGhostBox(mapper: CoordinateMapper, cue: OverlayCue.GhostBox, pulse: Float) {
    val r = mapper.mapRect(cue.left, cue.top, cue.right, cue.bottom)
    val dashed = PathEffect.dashPathEffect(floatArrayOf(24f, 18f), 0f)
    drawRoundRect(
        color = Color.White.copy(alpha = 0.25f + 0.25f * pulse),
        topLeft = Offset(r.left, r.top),
        size = Size(r.width, r.height),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(40f, 40f),
        style = Stroke(width = 5f, pathEffect = dashed)
    )
}

private fun DrawScope.drawZonePulse(mapper: CoordinateMapper, cue: OverlayCue.ZonePulse, pulse: Float) {
    if (cue.polygon.size < 2) return
    val pts = cue.polygon.map { mapper.mapPoint(it.first, it.second) }
    val width = 5f + 7f * pulse
    val alpha = 0.4f + 0.5f * pulse
    for (i in pts.indices) {
        val a = pts[i]
        val b = pts[(i + 1) % pts.size]
        drawLine(cue.color.copy(alpha = alpha), Offset(a.x, a.y), Offset(b.x, b.y), strokeWidth = width)
    }
}

private fun DrawScope.drawArrow(
    mapper: CoordinateMapper,
    cue: OverlayCue.Arrow,
    phase: Float,
    pulse: Float
) {
    val from: ViewPoint = mapper.mapPoint(cue.fromX, cue.fromY)
    val to: ViewPoint = mapper.mapPoint(cue.toX, cue.toY)
    val color = cue.color.copy(alpha = 0.5f + 0.4f * pulse)
    val dashed = PathEffect.dashPathEffect(floatArrayOf(26f, 20f), -phase * 46f)
    drawLine(color, Offset(from.x, from.y), Offset(to.x, to.y), strokeWidth = 8f, pathEffect = dashed)

    // A chevron head that travels along the route (phase 0→1) to show the direction of travel.
    val hx = from.x + (to.x - from.x) * phase
    val hy = from.y + (to.y - from.y) * phase
    val angle = atan2((to.y - from.y).toDouble(), (to.x - from.x).toDouble())
    val len = 34f
    val spread = Math.toRadians(28.0)
    val leftPt = Offset(
        (hx - len * cos(angle - spread)).toFloat(),
        (hy - len * sin(angle - spread)).toFloat()
    )
    val rightPt = Offset(
        (hx - len * cos(angle + spread)).toFloat(),
        (hy - len * sin(angle + spread)).toFloat()
    )
    val head = Offset(hx, hy)
    drawLine(color, head, leftPt, strokeWidth = 8f)
    drawLine(color, head, rightPt, strokeWidth = 8f)
}

private fun DrawScope.drawGlyph(mapper: CoordinateMapper, cue: OverlayCue.Glyph) {
    val c: ViewPoint = mapper.mapPoint(cue.cx, cue.cy)
    drawContext.canvas.nativeCanvas.apply {
        val paint = Paint().apply {
            color = android.graphics.Color.WHITE
            textSize = 120f
            isAntiAlias = true
            textAlign = Paint.Align.CENTER
        }
        drawText(cue.text, c.x, c.y + 42f, paint)
    }
}
