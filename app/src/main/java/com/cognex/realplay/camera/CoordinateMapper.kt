package com.cognex.realplay.camera

import kotlin.math.max
import kotlin.math.min

/** How the camera image is fitted into the view (mirrors CameraX PreviewView scale types). */
enum class ScaleType {
    /** Uniformly scale to FILL the view, cropping the overflow (CameraX default, CENTER_CROP). */
    FILL_CENTER,

    /** Uniformly scale to FIT inside the view, letterboxing the remainder. */
    FIT_CENTER
}

/** A point in view-pixel space (top-left origin, x right, y down). */
data class ViewPoint(val x: Float, val y: Float)

/** An axis-aligned rectangle in view-pixel space. */
data class ViewRect(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    val width: Float get() = right - left
    val height: Float get() = bottom - top
}

/**
 * Maps NORMALIZED (0..1) coordinates in analysis-image space to view-pixel coordinates.
 *
 * This is the single place in the app where pixels exist (Architecture §12). Everything in
 * world/, challenge/, verify/ stays normalized; only this class knows about rotation,
 * mirroring and the view crop. It is intentionally **pure Kotlin (no Android imports)** so it
 * can be exhaustively unit-tested on the JVM.
 *
 * Construction inputs:
 *  - [imageW]/[imageH]     : the raw analysis buffer size as delivered by ImageAnalysis, in the
 *                            sensor's native (un-rotated) orientation.
 *  - [rotationDegrees]     : ImageProxy.imageInfo.rotationDegrees — the clockwise rotation that
 *                            must be applied to the buffer to make it upright for display
 *                            (0, 90, 180 or 270).
 *  - [isFrontCamera]       : front cameras are mirrored horizontally in the preview.
 *  - [viewW]/[viewH]       : the on-screen size of the PreviewView / overlay, in pixels.
 *  - [scaleType]           : how the upright image is fitted into the view.
 *
 * Derivation (applied to a normalized point, step by step):
 *   1. image space  → rotate by rotationDegrees about the unit-square centre → "upright" space
 *   2. upright       → mirror across the vertical axis when isFrontCamera
 *   3. mirrored      → uniform FILL_CENTER (or FIT_CENTER) scale + centre offset → view pixels
 */
class CoordinateMapper(
    val imageW: Int,
    val imageH: Int,
    rotationDegrees: Int,
    val isFrontCamera: Boolean,
    val viewW: Int,
    val viewH: Int,
    val scaleType: ScaleType = ScaleType.FILL_CENTER
) {
    /** Normalised to 0/90/180/270. */
    val rotationDegrees: Int = ((rotationDegrees % 360) + 360) % 360

    private val quarterTurned = this.rotationDegrees == 90 || this.rotationDegrees == 270

    /** Dimensions of the image AFTER rotation to upright — a 90/270 turn swaps W and H. */
    val uprightW: Int = if (quarterTurned) imageH else imageW
    val uprightH: Int = if (quarterTurned) imageW else imageH

    // ── Step 3 pre-computed: uniform scale + centre offset (upright normalized → view px) ──
    private val scale: Float = run {
        val sx = viewW.toFloat() / uprightW
        val sy = viewH.toFloat() / uprightH
        when (scaleType) {
            ScaleType.FILL_CENTER -> max(sx, sy)
            ScaleType.FIT_CENTER -> min(sx, sy)
        }
    }
    private val scaledW: Float = uprightW * scale
    private val scaledH: Float = uprightH * scale
    private val offsetX: Float = (viewW - scaledW) / 2f
    private val offsetY: Float = (viewH - scaledH) / 2f

    /** Step 1: rotate a normalized point clockwise by [rotationDegrees] within the unit square. */
    private fun rotate(nx: Float, ny: Float): Pair<Float, Float> = when (rotationDegrees) {
        90 -> (1f - ny) to nx
        180 -> (1f - nx) to (1f - ny)
        270 -> ny to (1f - nx)
        else -> nx to ny // 0
    }

    /** Maps one normalized analysis-space point to a view-pixel point. */
    fun mapPoint(nx: Float, ny: Float): ViewPoint {
        val (rx, ry) = rotate(nx, ny)                       // 1. rotate to upright
        val mx = if (isFrontCamera) 1f - rx else rx         // 2. mirror for front camera
        return ViewPoint(mx * scaledW + offsetX, ry * scaledH + offsetY) // 3. scale + centre
    }

    /**
     * Maps a normalized analysis-space rectangle to a view-pixel rectangle.
     *
     * Rotation and mirroring can swap which corner is min/max, so all four corners are mapped
     * and the axis-aligned bounds are returned.
     */
    fun mapRect(left: Float, top: Float, right: Float, bottom: Float): ViewRect {
        val corners = listOf(
            mapPoint(left, top), mapPoint(right, top),
            mapPoint(right, bottom), mapPoint(left, bottom)
        )
        val xs = corners.map { it.x }
        val ys = corners.map { it.y }
        return ViewRect(xs.min(), ys.min(), xs.max(), ys.max())
    }
}
