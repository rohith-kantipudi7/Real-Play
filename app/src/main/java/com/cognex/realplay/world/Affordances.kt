package com.cognex.realplay.world

/**
 * Affordance derivation — "what can each thing be used for" (Architecture §6, S3.5). Pure JVM.
 *
 * Affordances are **derived, never detected**: no extra model, ~1 ms. Every flag has a
 * GEOMETRIC justification (area / stability) so the system still works when labels are unreliable
 * — the same degradation path as track-only mode (§6). A flag never depends on a label alone
 * where area or stability can decide it.
 *
 * The label sets below are editable top-level constants (§6). `box*` / `tray*` in the spec are
 * matched by prefix so both "box" and "storage box" qualify.
 */
object AffordanceSets {
    /** Large, load-bearing things a person sits/lies on or that anchor a room. */
    val FURNITURE = setOf(
        "chair", "couch", "sofa", "bed", "dining table", "table", "tv", "refrigerator", "bench"
    )

    /** Things that can hold other things. Prefix-matched entries end with '*'. */
    val CONTAINER = setOf(
        "bowl", "cup", "vase", "suitcase", "handbag", "backpack", "sink", "box*", "tray*"
    )

    /** Things that must never be treated as movable props. */
    val NEVER_MOVE = FURNITURE + setOf("person", "tv", "oven", "microwave")

    fun isContainer(label: String): Boolean {
        val l = label.trim().lowercase()
        return CONTAINER.any { entry ->
            if (entry.endsWith("*")) l.startsWith(entry.dropLast(1)) else l == entry
        }
    }

    fun isNeverMove(label: String): Boolean = NEVER_MOVE.contains(label.trim().lowercase())
}

/** §6 geometric thresholds — top-level so they are easy to tune. */
object AffordanceThresholds {
    const val MOVABLE_MAX_AREA = 0.25f
    const val HANDHELD_MAX_AREA = 0.08f
    const val LANDMARK_MIN_AREA = 0.20f
    const val LANDMARK_STABLE_MS = 3_000L
    const val NAMEABLE_MIN_CONF = 0.55f
    const val SEMANTIC_MIN_MEAN_CONF = 0.50f
}

/** Labels the object detector uses for an unrecognised region. */
private val UNKNOWN_LABELS = setOf("", "unknown", "?")

private fun isUnknownLabel(label: String): Boolean =
    label.trim().lowercase() in UNKNOWN_LABELS

/**
 * Derives per-object [Affordance]s from a [WorldState] (Architecture §6, S3.5).
 *
 * Stateful only for the `landmark` flag, which needs "stable for ≥ 3 s": the engine remembers,
 * per track, the timestamp at which it most recently became stable, so it can measure continuous
 * stability duration across frames. Everything else is a pure function of the current frame.
 */
class AffordanceEngine {

    /** trackId → timestamp (ms) at which this track most recently became continuously stable. */
    private val stableSince = HashMap<Int, Long>()

    fun derive(world: WorldState): List<Affordance> {
        val objects = world.objects
        val now = world.timestampMs

        // Maintain continuous-stability timers (drop tracks that vanished or destabilised).
        val liveIds = objects.mapTo(HashSet()) { it.trackId }
        stableSince.keys.retainAll(liveIds)

        // Count (label, color) occurrences for the `distinct` flag.
        val identityCounts = HashMap<Pair<String, ColorTag?>, Int>()
        for (o in objects) {
            val key = o.label.trim().lowercase() to o.color
            identityCounts[key] = (identityCounts[key] ?: 0) + 1
        }

        return objects.map { o ->
            val area = o.box.area
            val neverMove = AffordanceSets.isNeverMove(o.label)

            // Stability duration bookkeeping.
            val stableForMs: Long = if (o.stable) {
                val since = stableSince.getOrPut(o.trackId) { now }
                now - since
            } else {
                stableSince.remove(o.trackId)
                0L
            }

            val movable = area < AffordanceThresholds.MOVABLE_MAX_AREA && !neverMove
            val handheld = movable && area < AffordanceThresholds.HANDHELD_MAX_AREA
            val container = AffordanceSets.isContainer(o.label) || enclosesAnyZone(o, world.zones)
            val landmark = area > AffordanceThresholds.LANDMARK_MIN_AREA &&
                stableForMs >= AffordanceThresholds.LANDMARK_STABLE_MS
            val colorful = o.color?.isChromatic == true
            val key = o.label.trim().lowercase() to o.color
            val distinct = (identityCounts[key] ?: 0) == 1
            val nameable = !isUnknownLabel(o.label) &&
                o.confidence > AffordanceThresholds.NAMEABLE_MIN_CONF

            Affordance(
                trackId = o.trackId,
                movable = movable,
                handheld = handheld,
                container = container,
                landmark = landmark,
                colorful = colorful,
                distinct = distinct,
                nameable = nameable
            )
        }
    }

    /** Forgets stability history (e.g. camera session restart). */
    fun reset() = stableSince.clear()

    private fun enclosesAnyZone(o: TrackedObject, zones: List<Zone>): Boolean {
        if (zones.isEmpty()) return false
        return zones.any { zone -> zone.polygon.all { p -> pointInBox(p, o.box) } }
    }

    private fun pointInBox(p: NormPoint, b: NormRect): Boolean =
        p.x in b.left..b.right && p.y in b.top..b.bottom
}
