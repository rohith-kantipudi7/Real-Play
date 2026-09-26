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
    val color: Color = Color(0xFF22D3EE)
)

private val CalibrationBoxColor = Color(0xFF22D3EE) // electric cyan
private val CrosshairColor = Color(0xFFFBBF24)      // warm amber

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
    detections: List<OverlayDetection> = emptyList()
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

        detections.forEach { d ->
            drawDetectionBox(mapper, d.left, d.top, d.right, d.bottom, d.color, d.label)
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

/** Draws a zone polygon from NORMALIZED vertices. Unused until S7. */
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
