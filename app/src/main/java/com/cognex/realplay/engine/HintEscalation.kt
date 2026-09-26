package com.cognex.realplay.engine

/**
 * Timed hint escalation for the youngest bands (Architecture §10 rule 6): a verbal nudge at 8 s, a
 * more specific one at 16 s, then the §26 on-screen visual cue (already drawn continuously for
 * TODDLER/EARLY) is left to carry it from 24 s. Pure JVM.
 */
object HintEscalation {
    const val VERBAL_MS = 8_000L
    const val SPECIFIC_MS = 16_000L
    const val VISUAL_MS = 24_000L

    /** 0 = nothing yet, 1 = verbal, 2 = specific, 3 = visual-only (no new spoken text). */
    fun levelFor(elapsedMs: Long): Int = when {
        elapsedMs >= VISUAL_MS -> 3
        elapsedMs >= SPECIFIC_MS -> 2
        elapsedMs >= VERBAL_MS -> 1
        else -> 0
    }

    /** The hint text for [level] drawn from the challenge's own [hints] (§4); null at level 0 or 3. */
    fun hintFor(hints: List<String>, level: Int): String? = when (level) {
        1 -> hints.getOrNull(0)
        2 -> hints.getOrNull(1) ?: hints.getOrNull(0)
        else -> null
    }
}
