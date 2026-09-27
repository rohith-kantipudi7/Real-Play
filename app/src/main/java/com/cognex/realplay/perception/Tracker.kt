package com.cognex.realplay.perception

import com.cognex.realplay.world.ColorTag
import com.cognex.realplay.world.NormPoint
import com.cognex.realplay.world.NormRect
import com.cognex.realplay.world.SpatialRelations
import com.cognex.realplay.world.TrackedObject

/**
 * Multi-object tracker (Architecture §12 "Tracker", S3). Pure JVM — no Android, fully testable on
 * synthetic detection sequences.
 *
 * Algorithm per frame:
 *  1. Greedy assignment of [RawDetection]s to existing tracks by a cost that blends IoU, centroid
 *     distance and an appearance (dominant-colour histogram) term.
 *     - A same-label pair is a candidate when IoU ≥ [IOU_MATCH] OR centroid distance ≤
 *       [CENTROID_FALLBACK] (the fallback catches fast movement where boxes stop overlapping).
 *     - A cross-label pair is a candidate only above IoU [CROSS_LABEL_IOU].
 *  2. Ambiguity: if a detection's two best candidate tracks are within [AMBIGUOUS_RATIO] of each
 *     other's cost (or a track's two best detections are), BOTH involved tracks are flagged
 *     `ambiguous` for this frame and the incumbent assignment is kept (hysteresis). Verifiers read
 *     `ambiguous` and return Unsure (§20 invariant 2).
 *  3. Matched tracks update position by EMA ([EMA_ALPHA]), recompute velocity in normalized
 *     units/second from timestamp deltas, and grow their colour histogram by a slow EMA.
 *  4. A track is promoted (confirmed) after [PROMOTE_HITS] consecutive detections.
 *  5. Unmatched tracks coast: their position is extrapolated from velocity, they are flagged
 *     `stale`, and they are deleted after [COAST_FRAMES] missed frames.
 *
 * Only confirmed tracks are returned as [TrackedObject]s. `stable` is always false here — the
 * [com.cognex.realplay.world.StabilityDetector] sets it downstream.
 */
class Tracker {

    private val tracks = ArrayList<Track>()
    private var nextId = 1

    /** Advances the tracker by one frame and returns the confirmed tracks. */
    fun update(detections: List<RawDetection>, timestampMs: Long): List<TrackedObject> {
        // 1. Reset per-frame ambiguity.
        tracks.forEach { it.ambiguous = false }

        val nTracks = tracks.size
        val nDets = detections.size

        // 2. Cost + candidacy matrices.
        val cost = Array(nTracks) { FloatArray(nDets) }
        val candidate = Array(nTracks) { BooleanArray(nDets) }
        for (t in 0 until nTracks) {
            for (d in 0 until nDets) {
                val pair = evaluate(tracks[t], detections[d], timestampMs)
                cost[t][d] = pair.first
                candidate[t][d] = pair.second
            }
        }

        // 3. Ambiguity flags (detection-side and track-side).
        flagAmbiguity(cost, candidate, nTracks, nDets)

        // 4. Greedy assignment over candidate pairs, lowest cost first.
        data class Pair3(val t: Int, val d: Int, val c: Float)
        val pairs = ArrayList<Pair3>()
        for (t in 0 until nTracks) for (d in 0 until nDets) {
            if (candidate[t][d]) pairs.add(Pair3(t, d, cost[t][d]))
        }
        pairs.sortBy { it.c }

        val trackTaken = BooleanArray(nTracks)
        val detTaken = BooleanArray(nDets)
        val matchedTrackOf = IntArray(nDets) { -1 }
        for (p in pairs) {
            if (!trackTaken[p.t] && !detTaken[p.d]) {
                trackTaken[p.t] = true
                detTaken[p.d] = true
                matchedTrackOf[p.d] = p.t
            }
        }

        // 5. Update matched, spawn new, coast unmatched.
        for (d in 0 until nDets) {
            val t = matchedTrackOf[d]
            if (t >= 0) {
                tracks[t].updateMatched(detections[d], timestampMs)
            } else {
                tracks.add(Track(nextId++).apply { initFrom(detections[d], timestampMs) })
            }
        }
        for (t in 0 until nTracks) {
            if (!trackTaken[t]) tracks[t].coast(timestampMs)
        }

        // 6. Delete tracks that have coasted too long.
        tracks.removeAll { it.missed > COAST_FRAMES }

        // 7. Emit confirmed tracks.
        return tracks.filter { it.confirmed }.map { it.toTrackedObject() }
    }

    /** Returns (cost, isCandidate) for a track/detection pair. */
    private fun evaluate(track: Track, det: RawDetection, now: Long): kotlin.Pair<Float, Boolean> {
        // Match against the track's velocity-PREDICTED pose, not its last-seen one, so a moving
        // object still overlaps/near-matches its own track between frames (§12 tracking-while-moving).
        val (predBox, predCenter) = track.predictedPose(now)
        val iou = SpatialRelations.iou(predBox, det.box)
        val cdist = SpatialRelations.distance(predCenter, det.box.center)
        val sameLabel = track.label == det.label
        val appearance = track.appearanceDistance(det.color)
        // Re-identification: a coasting (recently-lost) track may re-claim its object even FAR from
        // its last spot — same label + matching colour — so an object picked up and carried to a new
        // place keeps its id and the verifier keeps judging the SAME object at its new location
        // (§ user request). A still-present same-label object wins its own track spatially first, so
        // this only fires for the one that actually moved away.
        val reId = sameLabel && track.missed > 0 && appearance <= REID_APPEARANCE_MAX
        val candidate = if (sameLabel) {
            iou >= IOU_MATCH || cdist <= CENTROID_FALLBACK || reId
        } else {
            iou >= CROSS_LABEL_IOU
        }
        val labelPenalty = if (sameLabel) 0f else 0.3f
        val cost = (1f - iou) + 0.5f * cdist + 0.2f * appearance + labelPenalty
        return cost to candidate
    }

    private fun flagAmbiguity(
        cost: Array<FloatArray>,
        candidate: Array<BooleanArray>,
        nTracks: Int,
        nDets: Int
    ) {
        // Detection-side: a detection that fits two tracks nearly equally makes both ambiguous.
        for (d in 0 until nDets) {
            val hits = ArrayList<kotlin.Pair<Int, Float>>()
            for (t in 0 until nTracks) if (candidate[t][d]) hits.add(t to cost[t][d])
            if (hits.size >= 2) {
                hits.sortBy { it.second }
                val best = hits[0]
                val second = hits[1]
                if (second.second <= best.second * AMBIGUOUS_RATIO) {
                    tracks[best.first].ambiguous = true
                    tracks[second.first].ambiguous = true
                }
            }
        }
        // Track-side: a track that fits two detections nearly equally is ambiguous.
        for (t in 0 until nTracks) {
            val hits = ArrayList<Float>()
            for (d in 0 until nDets) if (candidate[t][d]) hits.add(cost[t][d])
            if (hits.size >= 2) {
                hits.sort()
                if (hits[1] <= hits[0] * AMBIGUOUS_RATIO) tracks[t].ambiguous = true
            }
        }
    }

    /** Mutable per-track state. */
    private class Track(val id: Int) {
        var label: String = ""
        var confidence: Float = 0f
        var box: NormRect = NormRect(0f, 0f, 0f, 0f)
        var center: NormPoint = NormPoint(0f, 0f)
        val colorHist = FloatArray(ColorTag.entries.size)
        /** Decaying vote per label, so a flickering class name settles on its majority (§12). */
        val labelVotes = HashMap<String, Float>()
        var velocity: NormPoint = NormPoint(0f, 0f)
        var consecutiveHits = 0
        var missed = 0
        var ageFrames = 0
        var lastSeenMs = 0L
        var lastUpdateMs = 0L
        var confirmed = false
        var stale = false
        var ambiguous = false

        fun initFrom(det: RawDetection, now: Long) {
            label = det.label
            confidence = det.confidence
            box = det.box
            center = det.box.center
            det.color?.let { colorHist[it.ordinal] += 1f }
            voteLabel(det.label, det.confidence)
            label = smoothedLabel()
            velocity = NormPoint(0f, 0f)
            consecutiveHits = 1
            missed = 0
            ageFrames = 1
            lastSeenMs = now
            lastUpdateMs = now
            confirmed = consecutiveHits >= PROMOTE_HITS
            stale = false
        }

        fun updateMatched(det: RawDetection, now: Long) {
            val dt = ((now - lastUpdateMs).coerceAtLeast(1L)) / 1000f
            val newBox = ema(box, det.box, EMA_ALPHA)
            val newCenter = newBox.center
            velocity = NormPoint((newCenter.x - center.x) / dt, (newCenter.y - center.y) / dt)
            box = newBox
            center = newCenter
            label = det.label
            confidence = det.confidence
            // Slow appearance EMA.
            for (i in colorHist.indices) colorHist[i] *= (1f - COLOR_EMA)
            det.color?.let { colorHist[it.ordinal] += COLOR_EMA }
            voteLabel(det.label, det.confidence)
            label = smoothedLabel()
            consecutiveHits++
            missed = 0
            ageFrames++
            lastSeenMs = now
            lastUpdateMs = now
            if (consecutiveHits >= PROMOTE_HITS) confirmed = true
            stale = false
        }

        /** Box + centre extrapolated to [now] by the current velocity, capped so an erratic reading
         *  can't fling the prediction across the frame. Used only for MATCHING, never for output. */
        fun predictedPose(now: Long): kotlin.Pair<NormRect, NormPoint> {
            val dt = (((now - lastUpdateMs).coerceAtLeast(0L)) / 1000f).coerceAtMost(PREDICT_MAX_DT)
            val dx = velocity.x * dt
            val dy = velocity.y * dt
            if (dx == 0f && dy == 0f) return box to center
            return NormRect(box.left + dx, box.top + dy, box.right + dx, box.bottom + dy) to
                NormPoint(center.x + dx, center.y + dy)
        }

        fun coast(now: Long) {
            val dt = ((now - lastUpdateMs).coerceAtLeast(1L)) / 1000f
            val newCenter = NormPoint(center.x + velocity.x * dt, center.y + velocity.y * dt)
            val dx = newCenter.x - center.x
            val dy = newCenter.y - center.y
            box = NormRect(box.left + dx, box.top + dy, box.right + dx, box.bottom + dy)
            center = newCenter
            // Bleed off velocity while coasting so a lost track settles near where it vanished,
            // which lets the same object re-match its OWN track (same id) when it reappears.
            velocity = NormPoint(velocity.x * COAST_VELOCITY_DECAY, velocity.y * COAST_VELOCITY_DECAY)
            consecutiveHits = 0
            missed++
            ageFrames++
            lastUpdateMs = now
            stale = true
        }

        /** Appearance cost in 0..1: 1 minus this track's normalized weight on the detection's colour. */
        fun appearanceDistance(color: ColorTag?): Float {
            if (color == null) return 0.1f
            val sum = colorHist.sum()
            if (sum <= 0f) return 0.1f
            return 1f - (colorHist[color.ordinal] / sum)
        }

        /** Decays existing votes and adds the current label, weighted by detection confidence. */
        private fun voteLabel(raw: String, confidence: Float) {
            if (labelVotes.isNotEmpty()) {
                val it = labelVotes.iterator()
                while (it.hasNext()) {
                    val e = it.next()
                    val decayed = e.value * (1f - LABEL_DECAY)
                    if (decayed < 0.01f) it.remove() else e.setValue(decayed)
                }
            }
            if (raw.isNotBlank()) {
                labelVotes[raw] = (labelVotes[raw] ?: 0f) + LABEL_DECAY * confidence.coerceIn(0f, 1f)
            }
        }

        /** The current majority label over recent frames, or "" while no confident name has won. */
        private fun smoothedLabel(): String =
            labelVotes.maxByOrNull { it.value }?.key ?: ""

        private fun dominantColor(): ColorTag? {
            var bestIdx = -1
            var bestVal = 0f
            for (i in colorHist.indices) if (colorHist[i] > bestVal) { bestVal = colorHist[i]; bestIdx = i }
            if (bestIdx < 0) return null
            val tag = ColorTag.entries[bestIdx]
            return if (tag == ColorTag.UNKNOWN) null else tag
        }

        fun toTrackedObject(): TrackedObject = TrackedObject(
            trackId = id,
            label = label,
            confidence = confidence,
            box = box,
            center = center,
            color = dominantColor(),
            ageFrames = ageFrames,
            lastSeenMs = lastSeenMs,
            velocity = velocity,
            stable = false,
            stale = stale,
            ambiguous = ambiguous
        )

        private fun ema(old: NormRect, new: NormRect, alpha: Float): NormRect = NormRect(
            left = alpha * new.left + (1f - alpha) * old.left,
            top = alpha * new.top + (1f - alpha) * old.top,
            right = alpha * new.right + (1f - alpha) * old.right,
            bottom = alpha * new.bottom + (1f - alpha) * old.bottom
        )
    }

    companion object {
        const val IOU_MATCH = 0.3f
        // Wider centroid gate so a fast move (boxes stop overlapping) still re-matches its own track.
        const val CENTROID_FALLBACK = 0.16f
        const val CROSS_LABEL_IOU = 0.6f
        const val PROMOTE_HITS = 3
        /** Cap on how far ahead velocity may predict a track's pose for matching (seconds). */
        const val PREDICT_MAX_DT = 0.3f
        /** Max colour-appearance distance for a far re-identification of a recently-lost track. */
        const val REID_APPEARANCE_MAX = 0.35f
        // Long coast so a track survives a multi-second detection dropout (occlusion, a missed
        // model frame) — it stays resolvable by its id and re-matches the same object on reappear,
        // instead of being deleted and re-detected as a NEW id mid-game.
        const val COAST_FRAMES = 20
        /** Velocity retained per coasted frame — settles a lost track near where it vanished. */
        const val COAST_VELOCITY_DECAY = 0.5f
        const val EMA_ALPHA = 0.6f
        const val COLOR_EMA = 0.1f
        const val AMBIGUOUS_RATIO = 1.15f

        /** Per-frame weight for the decaying label vote — steadies flickering class names (§12). */
        const val LABEL_DECAY = 0.35f
    }
}
