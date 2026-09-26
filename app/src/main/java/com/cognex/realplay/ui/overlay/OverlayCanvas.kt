package com.cognex.realplay.ui.overlay

import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import com.cognex.realplay.camera.AnalysisInfo
import com.cognex.realplay.camera.CoordinateMapper
import com.cognex.realplay.camera.ScaleType
import com.cognex.realplay.camera.ViewPoint

/** A detection to draw, in NORMALIZED analysis-space coordinates. */
data class OverlayDetection(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
    val label: String? = null,
    val color: Color = Color(0xFF25E0C8)
)

/** A confirmed zone to draw, as an ordered NORMALIZED polygon (S7). */
data class OverlayZone(
    val polygon: List<Pair<Float, Float>>,
    val color: Color = Color(0xFFFFB13A)
)

/** A tracked player to draw as a coloured torso halo with a large "P{id}" label (S8). */
data class OverlayPlayer(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
    val label: String,
    val color: Color = Color(0xFF60A5FA),
    val ambiguous: Boolean = false
)

private val CalibrationBoxColor = Color(0xFF25E0C8) // turquoise
private val CrosshairColor = Color(0xFFFFB13A)      // tangerine

/**
 * Draws all camera overlays (detection boxes now; zones and skeletons in later stages) on top of
 * the preview. It builds a fresh [CoordinateMapper] each frame from the live [analysisInfo] and
 * the Canvas size, so boxes track the image through rotation and the FILL_CENTER crop.
 *
 * When [showCalibration] is true it draws the §12 calibration reference: the normalized rect
 * (0.25,0.25)–(0.75,0.75) mapped through the mapper, plus a crosshair at the mapped centre
 * (0.5,0.5). If the mapper is correct the crosshair sits dead-centre and the box stays locked to
 * the same physical area as you rotate the phone.
 */
@Composable
fun OverlayCanvas(
    analysisInfo: AnalysisInfo?,
    isFrontCamera: Boolean,
    modifier: Modifier = Modifier,
    showCalibration: Boolean = false,
    detections: List<OverlayDetection> = emptyList(),
    zones: List<OverlayZone> = emptyList(),
    players: List<OverlayPlayer> = emptyList()
) {
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

        zones.forEach { z ->
            drawZoneShape(mapper, z.polygon, z.color)
        }

        detections.forEach { d ->
            drawDetectionBox(mapper, d.left, d.top, d.right, d.bottom, d.color, d.label)
        }

        players.forEach { p ->
            drawPlayerHalo(mapper, p)
        }

        if (showCalibration) {
            drawCalibration(mapper)
        }
    }
}

/** Draws a labelled detection box from NORMALIZED coordinates. */
fun DrawScope.drawDetectionBox(
    mapper: CoordinateMapper,
    left: Float,
    top: Float,
    right: Float,
    bottom: Float,
    color: Color,
    label: String? = null,
    strokeWidthPx: Float = 4f
) {
    val r = mapper.mapRect(left, top, right, bottom)
    drawRect(
        color = color,
        topLeft = Offset(r.left, r.top),
        size = androidx.compose.ui.geometry.Size(r.width, r.height),
        style = Stroke(width = strokeWidthPx)
    )
    if (label != null) {
        drawContext.canvas.nativeCanvas.apply {
            val paint = Paint().apply {
                this.color = android.graphics.Color.WHITE
                textSize = 34f
                isAntiAlias = true
            }
            drawText(label, r.left, (r.top - 10f).coerceAtLeast(34f), paint)
        }
    }
}

/** Draws a player halo (a coloured torso ring + a large "P{id}" label above it) from a NORMALIZED
 *  box (S8). A dimmer, thinner ring signals an ambiguous identity (§5). */
fun DrawScope.drawPlayerHalo(mapper: CoordinateMapper, p: OverlayPlayer) {
    val r = mapper.mapRect(p.left, p.top, p.right, p.bottom)
    val cx = r.left + r.width / 2f
    val cy = r.top + r.height / 2f
    val radius = maxOf(r.width, r.height) * 0.75f
    val ringColor = if (p.ambiguous) p.color.copy(alpha = 0.45f) else p.color
    drawCircle(
        color = ringColor,
        radius = radius,
        center = Offset(cx, cy),
        style = Stroke(width = if (p.ambiguous) 5f else 8f)
    )
    drawContext.canvas.nativeCanvas.apply {
        val paint = Paint().apply {
            this.color = android.graphics.Color.WHITE
            textSize = 72f
            isAntiAlias = true
            isFakeBoldText = true
            textAlign = Paint.Align.CENTER
            setShadowLayer(6f, 0f, 0f, android.graphics.Color.BLACK)
        }
        drawText(p.label, cx, (cy - radius - 18f).coerceAtLeast(72f), paint)
    }
}

/** Draws a zone polygon from NORMALIZED vertices (S7). */
fun DrawScope.drawZoneShape(
    mapper: CoordinateMapper,
    polygon: List<Pair<Float, Float>>,
    color: Color,
    strokeWidthPx: Float = 4f
) {
    if (polygon.size < 2) return
    val pts = polygon.map { mapper.mapPoint(it.first, it.second) }
    for (i in pts.indices) {
        val a = pts[i]
        val b = pts[(i + 1) % pts.size]
        drawLine(color, Offset(a.x, a.y), Offset(b.x, b.y), strokeWidth = strokeWidthPx)
    }
}

/** Draws a skeleton from NORMALIZED joints + a connection list. Unused until S8. */
fun DrawScope.drawSkeletonShape(
    mapper: CoordinateMapper,
    joints: List<Pair<Float, Float>>,
    connections: List<Pair<Int, Int>>,
    color: Color,
    strokeWidthPx: Float = 4f
) {
    val pts = joints.map { mapper.mapPoint(it.first, it.second) }
    connections.forEach { (a, b) ->
        if (a in pts.indices && b in pts.indices) {
            drawLine(color, Offset(pts[a].x, pts[a].y), Offset(pts[b].x, pts[b].y), strokeWidth = strokeWidthPx)
        }
    }
}

/** Draws the §12 calibration reference: mapped (0.25..0.75) rect + centre crosshair. */
private fun DrawScope.drawCalibration(mapper: CoordinateMapper) {
    val r = mapper.mapRect(0.25f, 0.25f, 0.75f, 0.75f)
    drawRect(
        color = CalibrationBoxColor,
        topLeft = Offset(r.left, r.top),
        size = androidx.compose.ui.geometry.Size(r.width, r.height),
        style = Stroke(width = 4f)
    )

    val c: ViewPoint = mapper.mapPoint(0.5f, 0.5f)
    val arm = 28f
    drawLine(CrosshairColor, Offset(c.x - arm, c.y), Offset(c.x + arm, c.y), strokeWidth = 4f)
    drawLine(CrosshairColor, Offset(c.x, c.y - arm), Offset(c.x, c.y + arm), strokeWidth = 4f)
}
