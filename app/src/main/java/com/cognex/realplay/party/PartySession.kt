package com.cognex.realplay.party

import com.cognex.realplay.challenge.AgeBand
import com.cognex.realplay.challenge.ChallengeType
import com.cognex.realplay.challenge.SelectionMode

/**
 * One live PARTY session (Architecture §25) — the glue the UI drives. Composes [TurnController],
 * [Leaderboard] and [PartyOrchestrator] over the SAME registered skills a SOLO session plays; it
 * adds no verifier and no new way to win (§20 invariant 24). Pure JVM.
 *
 * One "round" = one participant's turn (however many quick challenges fit inside
 * [roundDurationMs], mirroring the existing SOLO loop). A "lap" = every entrant has played once;
 * the session ends after [lapsToPlay] laps and the caller shows the podium.
 */
class PartySession(
    val roster: Roster,
    val format: PartyFormat,
    val ageBand: AgeBand,
    private val lapsToPlay: Int = 1
) {
    val turns = TurnController(roster, format)
    // CO_OP_STREAK tracks one shared bucket only (Leaderboard.SHARED_ID) — individual entrants are
    // never registered so the leaderboard doesn't show empty per-player rows alongside it.
    val leaderboard = Leaderboard().apply {
        if (format != PartyFormat.CO_OP_STREAK) roster.entrants.forEach { register(it) }
    }

    private val typeHistory = mutableListOf<ChallengeType>()

    var turnsPlayed: Int = 0
        private set
    var sessionOver: Boolean = false
        private set

    val selectionMode: SelectionMode get() = PartyOrchestrator.selectionModeFor(format)
    val recentTypes: List<ChallengeType> get() = PartyOrchestrator.trimHistory(typeHistory)
    val roundDurationMs: Long? get() = RoundPacer.roundDurationMs(ageBand)

    /** Records the just-finished round's outcome for the entrant whose turn it was, then rotates. */
    fun completeRound(type: ChallengeType, passed: Boolean, gained: Int) {
        typeHistory.add(type)
        when (format) {
            PartyFormat.CO_OP_STREAK -> leaderboard.recordSharedRound(passed, gained)
            else -> leaderboard.recordRound(turns.current.id, passed, gained)
        }
        turnsPlayed++
        if (turnsPlayed >= roster.entrants.size * lapsToPlay) {
            sessionOver = true
        } else {
            turns.advance()
        }
    }
}
