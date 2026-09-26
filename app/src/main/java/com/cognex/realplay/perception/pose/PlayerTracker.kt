package com.cognex.realplay.perception.pose

import com.cognex.realplay.world.ColorTag
import com.cognex.realplay.world.Landmark
import com.cognex.realplay.world.NormPoint
import com.cognex.realplay.world.NormRect
import com.cognex.realplay.world.TrackedPlayer
import kotlin.math.hypot

/**
 * Assigns stable identities to detected poses across frames (Architecture §5). Pure JVM — no
 * Android, fully testable on synthetic pose sequences.
 *
 * Identity policy (§5):
 *  - ANCHOR at round start: a new pose must be present for [CONFIRM_FRAMES] consecutive frames
 *    before it earns a stable [TrackedPlayer.playerId] ("anchoring once N stable poses are present").
 *  - HOLD with hysteresis: a matched track updates its torso by EMA and coasts (survives
 *    [SURVIVE_FRAMES] missed frames) when unmatched, so a brief occlusion never re-numbers a player.
 *    A pose only switches from its incumbent track to a competitor when the competitor is at least
 *    [SWITCH_RATIO]× closer — this stops two crossing players swapping numbers.
 *  - AMBIGUITY at [AMBIGUOUS_SEP] separation: when a pose's two nearest tracks are within
 *    [AMBIGUOUS_SEP] of each other, it is resolved by [ColorTag] band when the bands differ, else
 *    BOTH tracks are frozen with [TrackedPlayer.ambiguous] = true so player rules return Unsure
 *    (§20 invariant 2).
 *  - RE-ANCHOR every round via [reset].
 *
 * [motionEnergy] is a visibility-weighted EMA of landmark speed, carried on each track.
 */
class PlayerTracker(private val maxPlayers: Int = 2) {

    private val tracks = ArrayList<Track>()
    private var nextId = 1

    /** Advances identity tracking by one frame and returns the confirmed players. */
    fun update(poses: List<RawPose>, timestampMs: Long): List<TrackedPlayer> {
        tracks.forEach { it.ambiguous = false; it.matchedThisFrame = false }

        // Only poses that look like a real, framed person can claim/keep an identity — this rejects
        // a faint background walker from stealing a foreground player's number.
        val salient = poses.filter { it.presenceConfidence >= PRESENCE_FLOOR && torsoArea(it.landmarks) >= MIN_TORSO_AREA }
        val centroids = salient.map { torsoCentroid(it.landmarks) }

        val assignedTrackOf = IntArray(salient.size) { -1 }

        // 1. Greedy nearest assignment, cheapest pair first. Cost is centroid distance plus a
        //    band penalty when a pose and a track carry *different* colour bands — this keeps two
        //    crossing players from swapping numbers when colour separates them, even though the
        //    other player's track is momentarily spatially closer.
        data class Cand(val poseIdx: Int, val trackIdx: Int, val dist: Float, val cost: Float)
        val cands = ArrayList<Cand>()
        for (p in salient.indices) {
            for (t in tracks.indices) {
                val d = distance(centroids[p], tracks[t].centroid)
                if (d > MATCH_GATE) continue
                val band = salient[p].colorBand
                val trackBand = tracks[t].colorBand
                val mismatch = band != null && trackBand != null && band != trackBand
                cands.add(Cand(p, t, d, if (mismatch) d + BAND_PENALTY else d))
            }
        }
        cands.sortBy { it.cost }
        val trackTaken = BooleanArray(tracks.size)
        val poseTaken = BooleanArray(salient.size)
        for (c in cands) {
            if (poseTaken[c.poseIdx] || trackTaken[c.trackIdx]) continue
            poseTaken[c.poseIdx] = true
            trackTaken[c.trackIdx] = true
            assignedTrackOf[c.poseIdx] = c.trackIdx
        }

        // 2. Ambiguity: a pose whose two nearest tracks are within AMBIGUOUS_SEP and not separable
        //    by colour band freezes both involved tracks.
        for (p in salient.indices) {
            val dists = tracks.indices
                .map { it to distance(centroids[p], tracks[it].centroid) }
                .filter { it.second <= MATCH_GATE }
                .sortedBy { it.second }
            if (dists.size >= 2) {
                val (t0, d0) = dists[0]
                val (t1, d1) = dists[1]
                if (d1 - d0 < AMBIGUOUS_SEP) {
                    val band = salient[p].colorBand
                    val separable = band != null &&
                        tracks[t0].colorBand != null && tracks[t1].colorBand != null &&
                        tracks[t0].colorBand != tracks[t1].colorBand
                    if (!separable) {
                        tracks[t0].ambiguous = true
                        tracks[t1].ambiguous = true
                    }
                }
            }
        }

        // 3. Update matched tracks; coast the rest.
        for (p in salient.indices) {
            val t = assignedTrackOf[p]
            if (t >= 0) tracks[t].update(salient[p], centroids[p], timestampMs)
        }
        for (t in tracks.indices) if (!trackTaken[t]) tracks[t].coast()

        // 4. Spawn provisional tracks for unmatched salient poses (anchor after CONFIRM_FRAMES).
        for (p in salient.indices) {
            if (poseTaken[p]) continue
            if (tracks.size >= maxPlayers + PROVISIONAL_SLACK) continue
            tracks.add(Track(pendingId = true).apply { update(salient[p], centroids[p], timestampMs) })
        }

        // 5. Retire dead tracks; confirm provisional ones that have been stable long enough.
        tracks.removeAll { it.framesMissed > SURVIVE_FRAMES }
        for (tr in tracks) {
            if (tr.pendingId && tr.framesSeen >= CONFIRM_FRAMES) {
                tr.pendingId = false
                tr.id = nextId++
            }
        }

        return tracks
            .filter { !it.pendingId && it.framesMissed == 0 }
            .sortedBy { it.id }
            .take(maxPlayers)
            .map { it.toTrackedPlayer() }
    }

    /** Clears every track and re-anchors — call at the start of each round (§5). */
    fun reset() {
        tracks.clear()
        nextId = 1
    }

    // ── internal track ───────────────────────────────────────────────────────

    private inner class Track(var pendingId: Boolean) {
        var id: Int = 0
        var centroid: NormPoint = NormPoint(0.5f, 0.5f)
        var landmarks: List<Landmark> = emptyList()
        var torsoBox: NormRect = NormRect(0.4f, 0.3f, 0.6f, 0.7f)
        var colorBand: ColorTag? = null
        var confidence: Float = 0f
        var motionEnergy: Float = 0f
        var framesSeen: Int = 0
        var framesMissed: Int = 0
        var ambiguous: Boolean = false
        var matchedThisFrame: Boolean = false
        private var prevLandmarks: List<Landmark> = emptyList()
        private var lastTs: Long = 0L

        fun update(pose: RawPose, newCentroid: NormPoint, timestampMs: Long) {
            prevLandmarks = landmarks
            val dt = if (lastTs == 0L) 0L else timestampMs - lastTs
            lastTs = timestampMs
            centroid = ema(centroid, newCentroid)
            landmarks = pose.landmarks
            torsoBox = torsoBox(pose.landmarks)
            pose.colorBand?.let { colorBand = it }
            confidence = pose.presenceConfidence
            if (prevLandmarks.isNotEmpty() && dt > 0L) {
                motionEnergy = motionEnergy(prevLandmarks, pose.landmarks, dt, motionEnergy)
            }
            framesSeen++
            framesMissed = 0
            matchedThisFrame = true
        }

        fun coast() {
            framesMissed++
        }

        fun toTrackedPlayer(): TrackedPlayer = TrackedPlayer(
            playerId = id,
            landmarks = landmarks,
            torsoBox = torsoBox,
            colorBand = colorBand,
            motionEnergy = motionEnergy,
            confidence = confidence,
            ambiguous = ambiguous
        )
    }

    companion object {
        /** Consecutive stable frames before a new pose earns an id (§5 anchoring). */
        const val CONFIRM_FRAMES = 5

        /** Missed frames a track survives (coasting through brief occlusion). */
        const val SURVIVE_FRAMES = 15

        /** Max centroid distance for a pose↔track match. */
        const val MATCH_GATE = 0.30f

        /** Two nearest tracks within this separation ⇒ ambiguous unless colour separates them. */
        const val AMBIGUOUS_SEP = 0.08f

        /** A competitor must be this much closer than the incumbent to steal a pose. */
        const val SWITCH_RATIO = 1.6f

        /** Cost added to a pose↔track match when their colour bands differ (keeps crossers apart). */
        const val BAND_PENALTY = 1.0f

        /** Minimum overall detector confidence for a pose to claim/keep an identity. */
        const val PRESENCE_FLOOR = 0.5f

        /** Minimum torso-box area — rejects a small, far background walker. */
        const val MIN_TORSO_AREA = 0.01f

        private const val EMA_ALPHA = 0.4f
        private const val PROVISIONAL_SLACK = 2

        private fun ema(prev: NormPoint, next: NormPoint) = NormPoint(
            EMA_ALPHA * next.x + (1f - EMA_ALPHA) * prev.x,
            EMA_ALPHA * next.y + (1f - EMA_ALPHA) * prev.y
        )

        private fun distance(a: NormPoint, b: NormPoint) = hypot(a.x - b.x, a.y - b.y)

        // Landmark indices (MediaPipe pose): shoulders 11/12, hips 23/24.
        private fun torsoCentroid(landmarks: List<Landmark>): NormPoint {
            val pts = listOf(11, 12, 23, 24).mapNotNull { landmarks.getOrNull(it)?.point }
            if (pts.isEmpty()) return NormPoint(0.5f, 0.5f)
            return NormPoint(pts.map { it.x }.average().toFloat(), pts.map { it.y }.average().toFloat())
        }

        private fun torsoBox(landmarks: List<Landmark>): NormRect {
            val pts = listOf(11, 12, 23, 24).mapNotNull { landmarks.getOrNull(it)?.point }
            if (pts.isEmpty()) return NormRect(0.4f, 0.3f, 0.6f, 0.7f)
            val xs = pts.map { it.x }; val ys = pts.map { it.y }
            return NormRect(xs.min(), ys.min(), xs.max(), ys.max())
        }

        private fun torsoArea(landmarks: List<Landmark>): Float {
            val b = torsoBox(landmarks)
            return (b.right - b.left).coerceAtLeast(0f) * (b.bottom - b.top).coerceAtLeast(0f)
        }

        /** Visibility-weighted EMA of landmark speed (mirrors PoseMath.motionEnergy, kept local to
         *  avoid a perception→verify dependency). */
        private fun motionEnergy(
            previous: List<Landmark>, current: List<Landmark>, dtMs: Long, prevEnergy: Float,
            alpha: Float = 0.5f
        ): Float {
            val dt = dtMs.coerceAtLeast(1L) / 1000f
            val n = minOf(previous.size, current.size)
            var weightSum = 0f
            var speedAcc = 0f
            for (i in 0 until n) {
                val w = minOf(previous[i].visibility, current[i].visibility)
                if (w <= 0f) continue
                val d = hypot(current[i].point.x - previous[i].point.x, current[i].point.y - previous[i].point.y)
                speedAcc += w * (d / dt)
                weightSum += w
            }
            val instant = if (weightSum <= 0f) 0f else speedAcc / weightSum
            return alpha * instant + (1f - alpha) * prevEnergy
        }
    }
}
