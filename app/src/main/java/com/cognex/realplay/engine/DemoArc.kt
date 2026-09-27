package com.cognex.realplay.engine

import com.cognex.realplay.challenge.AgeBand

/**
 * The scripted demo game order per audience tier (DEMO_GAME_PLAN §5, §5.5). When
 * [AppSettings.demoArcEnabled] is on, a SOLO session plays its tier's list in order (looping)
 * instead of the adaptive scored pick — so each tier shows a DIFFERENT, predictable progression and
 * Toddler stays on basic games only.
 *
 * Each round the engine plays the next id whose scene requirement is met this frame, skipping only a
 * game the scene can't support (e.g. Triangle with fewer than three objects, a pose/combo with no
 * person). Age gating is bypassed for the pinned game, but the per-tier lists themselves keep each
 * tier age-appropriate — Toddler never lists geometry/pose/sort/combo.
 */
object DemoArc {

    /** The ordered generator ids for [band]. */
    fun orderFor(band: AgeBand): List<String> = when (band) {
        // Basic only (§2): show the named object, then find a colour. No geometry/pose/sort.
        AgeBand.TODDLER -> listOf("G8", "G3")                       // Grab, Find-colour
        // Kids (§5.5 Tier 1): gentle and playful.
        AgeBand.EARLY -> listOf("G8", "G4", "G12")                  // Grab, Pose, Group-by-kind
        // Player (§5.5 Tier 2): balanced, adds arranging.
        AgeBand.MIDDLE -> listOf("G12", "G7", "G9", "G4")           // Group-by-kind, Triangle, Line-up, Pose
        // Pro (§5, §5.5 Tier 3): the full demo arc, fast and precise.
        AgeBand.OLDER -> listOf("G8", "G7", "G9", "G10", "G13")     // Grab, Triangle, Line-up, Sort, Hold & Pose
    }
}
