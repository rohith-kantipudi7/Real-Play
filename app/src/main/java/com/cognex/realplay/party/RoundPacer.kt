package com.cognex.realplay.party

import com.cognex.realplay.challenge.AgeBand

/**
 * PARTY round pacing (Architecture §25): short, capped rounds so the room never stalls. Pure JVM —
 * this is presentation/orchestration timing only, never a verifier or challenge time limit (§20
 * invariant 24). TODDLER is never time-pressured: [roundDurationMs] returns null and the caller
 * must not start a countdown (§10, §25 "toddler-safe pacing — no elimination, no clock pressure").
 */
object RoundPacer {
    const val MIN_ROUND_MS = 20_000L
    const val MAX_ROUND_MS = 40_000L
    private const val DEFAULT_ROUND_MS = 30_000L

    fun roundDurationMs(ageBand: AgeBand): Long? =
        if (ageBand == AgeBand.TODDLER) null else DEFAULT_ROUND_MS
}
