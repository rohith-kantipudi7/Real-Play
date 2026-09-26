package com.cognex.realplay.party

import com.cognex.realplay.challenge.ChallengeType
import com.cognex.realplay.challenge.RankedCandidate
import com.cognex.realplay.challenge.SelectionMode

/**
 * PARTY selection policy (Architecture §25): controlled variety and rotation fairness over the
 * SAME registered skills — no new generator, no new RuleId, no new PASS path (§20 invariant 24).
 * Pure JVM.
 */
object PartyOrchestrator {

    /** How many recent challenge types to remember across a rotation (mirrors GameSession, §8). */
    const val HISTORY_WINDOW = 3

    /**
     * OPEN gives weighted-random variety among every feasible skill (§16) — every PARTY format uses
     * it so consecutive players see different games from the same table, exactly as §25 asks.
     */
    fun selectionModeFor(format: PartyFormat): SelectionMode = SelectionMode.OPEN

    /** Keeps only the most recent [HISTORY_WINDOW] types, oldest-first. */
    fun trimHistory(history: List<ChallengeType>, windowSize: Int = HISTORY_WINDOW): List<ChallengeType> =
        if (history.size <= windowSize) history else history.takeLast(windowSize)

    fun isRepeat(lastType: ChallengeType?, nextType: ChallengeType): Boolean = lastType == nextType

    /**
     * True when the top two feasible scores are close enough that OPEN's weighted-random rotation
     * stays genuinely comparable across players, rather than one skill dominating every round.
     * A monitoring signal (log-worthy), not a hard gate — with 0 or 1 feasible skill there is
     * nothing to compare, so it is trivially fair.
     */
    fun isFeasibilityComparable(ranked: List<RankedCandidate>, tolerance: Float = 0.2f): Boolean {
        val feasible = ranked.filter { it.score > 0f }.map { it.score }.sortedDescending()
        if (feasible.size < 2) return true
        return feasible[0] - feasible[1] <= tolerance
    }
}
