package com.cognex.realplay.challenge

import com.cognex.realplay.world.TrackedObject
import com.cognex.realplay.world.WorldState

/**
 * The safety gate (Architecture §11, §20 invariant 9). Pure JVM.
 *
 * Runs BEFORE generation and cannot be overridden by any downstream LLM: a generator only ever
 * sees the objects [SafetyFilter] allows. Fails closed — anything on the deny list, or (in toddler
 * mode) too small / unlabelled, is removed from the scene the generators build against.
 *
 * DENY_ACTIONS (climb, jump, throw, aim-at-head, run out of frame) are not enumerable from object
 * labels; the closed rule set simply contains no primitive that can express them, so they are
 * unreachable by construction.
 */
object SafetyFilter {

    /** Objects that must never be a game target at any age (§11). Matched as a substring of label. */
    val DENY_ALWAYS = listOf(
        "knife", "scissor", "glass", "chemical", "lighter", "socket",
        "cable", "cord", "hot drink", "coffee", "tea", "medicine", "pill"
    )

    /** Toddler choking heuristic — reject objects below this normalized box area (§10, §11). */
    const val TODDLER_MIN_AREA = 0.015f

    private val UNKNOWN_LABELS = setOf("", "unknown", "?")

    /** The reason [obj] is unsafe for [ageBand], or null when it is allowed. */
    fun rejection(obj: TrackedObject, ageBand: AgeBand): String? {
        val label = obj.label.trim().lowercase()
        DENY_ALWAYS.firstOrNull { label.contains(it) }?.let { return "unsafe: $it" }
        if (ageBand == AgeBand.TODDLER) {
            if (label in UNKNOWN_LABELS) return "toddler: unlabelled object"
            if (obj.box.area < TODDLER_MIN_AREA) return "toddler: too small (choking risk)"
        }
        return null
    }

    fun isAllowed(obj: TrackedObject, ageBand: AgeBand): Boolean = rejection(obj, ageBand) == null

    /**
     * Returns a copy of [world] with every denied object removed, so no generator can build a spec
     * around an unsafe target (§20 invariant 9). Players, zones and quality are untouched.
     */
    fun apply(world: WorldState, ageBand: AgeBand): WorldState {
        val allowedIds = world.objects.filter { isAllowed(it, ageBand) }.mapTo(HashSet()) { it.trackId }
        if (allowedIds.size == world.objects.size) return world
        return world.copy(
            objects = world.objects.filter { it.trackId in allowedIds },
            affordances = world.affordances.filter { it.trackId in allowedIds }
        )
    }
}
