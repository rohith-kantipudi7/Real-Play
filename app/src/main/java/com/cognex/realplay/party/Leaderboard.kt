package com.cognex.realplay.party

/** One row of the live leaderboard (Architecture §25). */
data class LeaderboardEntry(val entrantId: String, val name: String, val score: Int, val streak: Int)

/**
 * Cumulative score + streak per entrant, with team aggregation and a shared co-op counter
 * (Architecture §25). Pure JVM. A team IS one entrant (its members alternate turns via
 * [TurnController]), so per-entrant accumulation already gives team aggregation for free — no
 * separate code path. Ties keep registration order (stable sort), so an untouched roster reads
 * top-to-bottom exactly as entered.
 */
class Leaderboard {
    private val scores = LinkedHashMap<String, Int>()
    private val streaks = LinkedHashMap<String, Int>()
    private val names = LinkedHashMap<String, String>()

    fun register(entrant: Entrant) {
        scores.putIfAbsent(entrant.id, 0)
        streaks.putIfAbsent(entrant.id, 0)
        names[entrant.id] = entrant.name
    }

    /** Records one entrant's round: score always accumulates, streak resets on a non-pass. */
    fun recordRound(entrantId: String, passed: Boolean, gained: Int) {
        scores[entrantId] = (scores[entrantId] ?: 0) + gained
        streaks[entrantId] = if (passed) (streaks[entrantId] ?: 0) + 1 else 0
    }

    /** CO_OP_STREAK: the whole room shares one score and one streak (§25). */
    fun recordSharedRound(passed: Boolean, gained: Int) {
        names.putIfAbsent(SHARED_ID, "The Room")
        recordRound(SHARED_ID, passed, gained)
    }

    fun ranked(): List<LeaderboardEntry> =
        scores.entries
            .map { (id, score) -> LeaderboardEntry(id, names[id] ?: id, score, streaks[id] ?: 0) }
            .sortedByDescending { it.score }

    fun winner(): LeaderboardEntry? = ranked().firstOrNull()

    companion object {
        const val SHARED_ID = "__room__"
    }
}
