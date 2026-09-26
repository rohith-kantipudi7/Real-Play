package com.cognex.realplay.world

/**
 * The immutable snapshot of everything the game knows about the scene at one frame
 * (Architecture §4). This is the single input to the truth line
 * (Perception → WorldState → Verifier). Pure JVM — no Android.
 */
data class WorldState(
    val frameId: Long,
    val timestampMs: Long,
    val objects: List<TrackedObject>,
    val players: List<TrackedPlayer>,
    val zones: List<Zone>,
    val quality: FrameQuality,
    val affordances: List<Affordance>,
    val capability: SceneCapability
) {
    companion object {
        /** The empty world used before the first frame is processed. */
        val EMPTY = WorldState(
            frameId = -1L,
            timestampMs = 0L,
            objects = emptyList(),
            players = emptyList(),
            zones = emptyList(),
            quality = FrameQuality(good = true, reason = null),
            affordances = emptyList(),
            capability = SceneCapability.EMPTY
        )
    }
}

/**
 * A single tracked object with a stable identity across frames (Architecture §4).
 *
 *  - [trackId]   persists through slow movement and brief occlusion.
 *  - [velocity]  is in NORMALIZED units per second (from timestamp deltas).
 *  - [stale]     true while the track is being coasted (extrapolated, not seen this frame).
 *  - [ambiguous] true when the tracker could not confidently distinguish this track from another
 *                (e.g. two similar objects crossing) — verifiers read this and return Unsure
 *                (§20 invariant 2).
 */
data class TrackedObject(
    val trackId: Int,
    val label: String,
    val confidence: Float,
    val box: NormRect,
    val center: NormPoint,
    val color: ColorTag?,
    val ageFrames: Int,
    val lastSeenMs: Long,
    val velocity: NormPoint,
    val stable: Boolean,
    val stale: Boolean,
    val ambiguous: Boolean
)

/**
 * A single tracked player (Architecture §4). Populated by the pose detector in S4; declared here
 * so [WorldState] compiles. Pure JVM.
 */
data class TrackedPlayer(
    val playerId: Int,
    val landmarks: List<Landmark>,
    val torsoBox: NormRect,
    val colorBand: ColorTag?,
    val motionEnergy: Float,
    val confidence: Float,
    val ambiguous: Boolean
)

/**
 * Per-frame quality gate (Architecture §4, §12). [reason] is a user-facing coaching string such
 * as "Move to better light" or "Hold steady". A verifier must NEVER produce Pass/Fail on a frame
 * where [good] is false — it returns Unsure instead.
 */
data class FrameQuality(val good: Boolean, val reason: String?)
