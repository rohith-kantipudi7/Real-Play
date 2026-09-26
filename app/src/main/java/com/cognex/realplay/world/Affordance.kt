package com.cognex.realplay.world

/**
 * Per-object affordances — "what can this thing be used for" (Architecture §4, §6). Pure JVM.
 *
 * The eight boolean flags are computed by the affordance engine in S3.5. They are declared here
 * (with the deriving predicate in each comment) so [WorldState] and [SceneCapability] compile now
 * and the S3 world model can carry an empty affordance list until the engine lands.
 */
data class Affordance(
    val trackId: Int,
    val movable: Boolean,    // area < 25% && label ∉ NEVER_MOVE
    val handheld: Boolean,   // area < 8%  && movable
    val container: Boolean,  // label ∈ CONTAINER || encloses a zone
    val landmark: Boolean,   // area > 20% && stable ≥ 3 s
    val colorful: Boolean,   // ColorTag chromatic
    val distinct: Boolean,   // unique (label, color) in scene
    val nameable: Boolean    // label != UNKNOWN && conf > 0.55
)

/**
 * The scene's aggregate capability (Architecture §4, §3.5, §6.2). Pure JVM.
 *
 * S3 populates the object-derived, always-available terms ([distinctColors], [spread],
 * [stability]) and the capability-mode flags; the affordance-derived counts and player/richness
 * terms are filled by S3.5 / S4 and default to neutral values until then.
 */
data class SceneCapability(
    val movableCount: Int,
    val handheldCount: Int,
    val containerCount: Int,
    val landmarkCount: Int,
    val nameableCount: Int,
    val distinctColors: Set<ColorTag>,
    val playerCount: Int,
    val zoneCount: Int,
    val spread: Float,          // mean pairwise centroid distance
    val stability: Float,       // fraction of tracks stable
    // ── player-derived terms, used by §6.2 human-only renormalisation ──
    val poseVariety: Float,     // 0..1 — distinct pose clusters observed this session
    val motionRange: Float,     // 0..1 — observed landmark motion span
    val frameCoverage: Float,   // 0..1 — fraction of frame the player body occupies
    val richness: Float,        // §6.2 — renormalises for BOTH empty-player and empty-object
    // ── capability mode flags, §3.5 ──
    val semanticLabelsAvailable: Boolean,
    val trackOnlyMode: Boolean,
    val planarSurfaceAvailable: Boolean
) {
    companion object {
        /** An empty scene — no objects, no players, everything neutral. */
        val EMPTY = SceneCapability(
            movableCount = 0, handheldCount = 0, containerCount = 0,
            landmarkCount = 0, nameableCount = 0, distinctColors = emptySet(),
            playerCount = 0, zoneCount = 0, spread = 0f, stability = 0f,
            poseVariety = 0f, motionRange = 0f, frameCoverage = 0f, richness = 0f,
            semanticLabelsAvailable = true, trackOnlyMode = false,
            planarSurfaceAvailable = false
        )
    }
}
