package com.cognex.realplay.perception.zone

import android.graphics.Bitmap
import com.cognex.realplay.world.Zone

/**
 * Android wrapper around [ZoneRegions] + [ZoneTracker] (Architecture §13 S7). Downscales the camera
 * frame to [GRID_W]×[GRID_H], runs pure-JVM detection, and keeps a thread-safe snapshot of the
 * confirmed zones.
 *
 * Threading: [onFrame] runs on the analyzer thread; [latestZones] is read on the detector-result
 * thread. The only shared state is the `@Volatile` immutable [latest] list, so no lock is needed.
 * Detection is invoked at most every [RUN_EVERY_N]th frame by the caller.
 */
class ZoneDetector {

    private val tracker = ZoneTracker()

    @Volatile
    private var latest: List<Zone> = emptyList()

    /** The most recent confirmed zones. Cheap, lock-free, safe to call from any thread. */
    fun latestZones(): List<Zone> = latest

    /** Runs detection on one frame. Call at most every [RUN_EVERY_N]th frame. */
    fun onFrame(bitmap: Bitmap) {
        if (bitmap.width <= 0 || bitmap.height <= 0) return
        val scaled = Bitmap.createScaledBitmap(bitmap, GRID_W, GRID_H, false)
        try {
            val px = IntArray(GRID_W * GRID_H)
            scaled.getPixels(px, 0, GRID_W, 0, 0, GRID_W, GRID_H)
            val raw = ZoneRegions.detect(px, GRID_W, GRID_H)
            latest = tracker.update(raw)
        } finally {
            if (scaled != bitmap) scaled.recycle()
        }
    }

    companion object {
        const val GRID_W = 160
        const val GRID_H = 120
        const val RUN_EVERY_N = 3
    }
}
