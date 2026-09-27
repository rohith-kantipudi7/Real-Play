package com.cognex.realplay.engine

/**
 * The locked demo game order (Architecture / DEMO_GAME_PLAN §5). When [AppSettings.demoArcEnabled]
 * is on, a SOLO session plays these generators in this exact sequence (looping) instead of the
 * adaptive scored pick — so the presenter reliably sees the intended progression rather than the
 * registry cycling between the low-requirement games (Grab / Find-colour).
 *
 * Each round the engine plays the next id in [order] whose scene requirement is met this frame,
 * skipping only a game the scene genuinely can't support (e.g. Triangle with fewer than three
 * objects, or the combo finale with no person). Age/difficulty gating is bypassed for the arc, so
 * the same order plays on every tier.
 */
object DemoArc {
    val order: List<String> = listOf(
        "G8",  // Grab — show the named object
        "G7",  // Triangle — arrange three into a triangle
        "G9",  // Line-up — all objects in one straight row
        "G10", // Sort by size — order left→right
        "G13"  // Hold & Pose combo — the finale (needs a person)
    )
}
