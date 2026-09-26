package com.cognex.realplay.perception

import android.graphics.Bitmap
import com.cognex.realplay.world.FrameQuality

/**
 * Judges whether a frame is good enough to verify on (Architecture §4, §12, S3).
 *
 * The scoring core ([analyze]) is pure — it works on a downscaled grid of 0..255 luminance values,
 * so it is unit-tested on the JVM. [sampleLuma] is the thin Android wrapper that downscales a
 * [Bitmap] into that grid. Two proxies are used:
 *  - mean luminance → too dark (lens covered / bad light)
 *  - Laplacian variance → blur / motion (edges wash out when out of focus or shaking)
 *
 * A POOR frame yields a user-facing [FrameQuality.reason]; verifiers must return Unsure, never
 * Pass/Fail, on a POOR frame (§12 "Never verify on a bad frame").
 */
object FrameQualityAnalyzer {

    /** Below this mean luminance (0..255) the frame is treated as too dark. */
    const val DARK_MEAN = 40f

    /** Below this Laplacian variance the frame is treated as blurred / shaky. */
    const val BLUR_LAPVAR = 8f

    /** Default analysis grid size for [sampleLuma]. */
    const val GRID_W = 32
    const val GRID_H = 24

    /**
     * Scores a [grid] of [width]×[height] luminance values (0..255, row-major). Grids smaller than
     * 3×3 are treated as good (not enough data to fault the frame).
     */
    fun analyze(grid: IntArray, width: Int, height: Int): FrameQuality {
        if (width < 3 || height < 3 || grid.size < width * height) {
            return FrameQuality(good = true, reason = null)
        }
        var sum = 0.0
        for (v in grid) sum += v
        val mean = (sum / grid.size).toFloat()
        if (mean < DARK_MEAN) return FrameQuality(good = false, reason = "Move to better light")

        val lapVar = laplacianVariance(grid, width, height)
        if (lapVar < BLUR_LAPVAR) return FrameQuality(good = false, reason = "Hold steady")

        return FrameQuality(good = true, reason = null)
    }

    /** Variance of the 4-neighbour Laplacian over the interior cells of the grid. */
    private fun laplacianVariance(grid: IntArray, width: Int, height: Int): Float {
        var sum = 0.0
        var sumSq = 0.0
        var count = 0
        for (y in 1 until height - 1) {
            for (x in 1 until width - 1) {
                val c = grid[y * width + x]
                val up = grid[(y - 1) * width + x]
                val down = grid[(y + 1) * width + x]
                val left = grid[y * width + (x - 1)]
                val right = grid[y * width + (x + 1)]
                val lap = (4 * c - up - down - left - right).toDouble()
                sum += lap
                sumSq += lap * lap
                count++
            }
        }
        if (count == 0) return 0f
        val mean = sum / count
        return (sumSq / count - mean * mean).toFloat()
    }

    /**
     * Downscales [bitmap] into a [gridW]×[gridH] luminance grid (0..255, row-major) by point
     * sampling. Best-effort — quality is advisory, never on the truth path.
     */
    fun sampleLuma(bitmap: Bitmap, gridW: Int = GRID_W, gridH: Int = GRID_H): IntArray {
        val w = bitmap.width
        val h = bitmap.height
        val grid = IntArray(gridW * gridH)
        if (w <= 0 || h <= 0) return grid
        for (gy in 0 until gridH) {
            val py = ((gy + 0.5f) / gridH * h).toInt().coerceIn(0, h - 1)
            for (gx in 0 until gridW) {
                val px = ((gx + 0.5f) / gridW * w).toInt().coerceIn(0, w - 1)
                val p = bitmap.getPixel(px, py)
                val r = (p shr 16) and 0xFF
                val g = (p shr 8) and 0xFF
                val b = p and 0xFF
                grid[gy * gridW + gx] = ((299 * r + 587 * g + 114 * b) / 1000)
            }
        }
        return grid
    }
}
