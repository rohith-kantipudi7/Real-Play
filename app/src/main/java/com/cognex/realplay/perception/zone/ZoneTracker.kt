package com.cognex.realplay.perception.zone

import com.cognex.realplay.world.ColorTag
import com.cognex.realplay.world.NormPoint
import com.cognex.realplay.world.Zone
import com.cognex.realplay.world.ZoneSource
import kotlin.math.hypot

/**
 * Temporal confirmation + survival + EMA smoothing for detected zones (Architecture §13 S7). Pure
 * JVM — [ZoneRegions] finds per-frame candidates, this class turns the noisy stream into stable,
 * flicker-free [Zone]s:
 *
 *   - a candidate must be seen [CONFIRM_FRAMES] consecutive frames before it becomes a [Zone];
 *   - a confirmed zone SURVIVES up to [SURVIVE_FRAMES] frames of absence before it is dropped, so a
 *     one-frame miss never makes it blink;
 *   - the quad vertices are EMA-smoothed ([EMA_ALPHA]) so the rendered polygon does not shimmer.
 *
 * Matching is by same colour + centroid proximity ([MATCH_DIST]) — zones do not move, so this is
 * enough. Zone ids are stable for a track's whole lifetime.
 */
class ZoneTracker {

    private class Track(
        val zoneId: String,
        val color: ColorTag,
        var polygon: List<NormPoint>,
        var centroid: NormPoint,
        var seen: Int,
        var missed: Int,
        var confirmed: Boolean
    )

    private val tracks = ArrayList<Track>()
    private var nextId = 0

    /** Feeds one frame of raw candidates and returns the currently-confirmed zones. */
    fun update(raw: List<RawZone>): List<Zone> {
        val usedRaw = BooleanArray(raw.size)

        // 1. Match existing tracks to the nearest same-colour candidate.
        for (t in tracks) {
            var bestIdx = -1
            var bestDist = MATCH_DIST
            for (i in raw.indices) {
                if (usedRaw[i]) continue
                val r = raw[i]
                if (r.color != t.color) continue
                val d = dist(r.centroid, t.centroid)
                if (d < bestDist) { bestDist = d; bestIdx = i }
            }
            if (bestIdx >= 0) {
                val r = raw[bestIdx]
                usedRaw[bestIdx] = true
                t.seen++
                t.missed = 0
                t.centroid = ema(t.centroid, r.centroid)
                t.polygon = emaPolygon(t.polygon, r.polygon)
                if (t.seen >= CONFIRM_FRAMES) t.confirmed = true
            } else {
                t.missed++
            }
        }

        // 2. Retire tracks absent too long.
        tracks.removeAll { it.missed > SURVIVE_FRAMES }

        // 3. Spawn tracks for unmatched candidates.
        for (i in raw.indices) {
            if (usedRaw[i]) continue
            val r = raw[i]
            tracks.add(
                Track(
                    zoneId = "z${nextId++}",
                    color = r.color,
                    polygon = r.polygon,
                    centroid = r.centroid,
                    seen = 1,
                    missed = 0,
                    confirmed = CONFIRM_FRAMES <= 1
                )
            )
        }

        return tracks.filter { it.confirmed }
            .map { Zone(it.zoneId, it.polygon, it.color, ZoneSource.DETECTED) }
    }

    private fun dist(a: NormPoint, b: NormPoint) = hypot(a.x - b.x, a.y - b.y)

    private fun ema(old: NormPoint, new: NormPoint) = NormPoint(
        old.x + EMA_ALPHA * (new.x - old.x),
        old.y + EMA_ALPHA * (new.y - old.y)
    )

    private fun emaPolygon(old: List<NormPoint>, new: List<NormPoint>): List<NormPoint> {
        if (old.size != new.size) return new
        return old.indices.map { ema(old[it], new[it]) }
    }

    companion object {
        const val CONFIRM_FRAMES = 5
        const val SURVIVE_FRAMES = 15
        const val MATCH_DIST = 0.15f
        const val EMA_ALPHA = 0.3f
    }
}
