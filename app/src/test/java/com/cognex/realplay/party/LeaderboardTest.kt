package com.cognex.realplay.party

import com.cognex.realplay.world.ColorTag
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Leaderboard — accumulation, streak reset, ranking, team/co-op aggregation (Architecture §25). */
class LeaderboardTest {

    @Test
    fun accumulatesScoreAndTracksStreak() {
        val lb = Leaderboard()
        lb.register(Entrant("e0", "A", ColorTag.RED))
        lb.recordRound("e0", passed = true, gained = 10)
        lb.recordRound("e0", passed = true, gained = 20)
        val entry = lb.ranked().single()
        assertEquals(30, entry.score)
        assertEquals(2, entry.streak)
    }

    @Test
    fun streakResetsOnAFailedRound() {
        val lb = Leaderboard()
        lb.register(Entrant("e0", "A", ColorTag.RED))
        lb.recordRound("e0", passed = true, gained = 10)
        lb.recordRound("e0", passed = false, gained = 0)
        assertEquals(0, lb.ranked().single().streak)
        // Deliberate failure still fails — a lost streak is never silently kept (§20 invariant 24).
        assertEquals(10, lb.ranked().single().score)
    }

    @Test
    fun ranksDescendingByScore_tiesKeepRegistrationOrder() {
        val lb = Leaderboard()
        lb.register(Entrant("e0", "A", ColorTag.RED))
        lb.register(Entrant("e1", "B", ColorTag.BLUE))
        lb.recordRound("e1", passed = true, gained = 50)
        lb.recordRound("e0", passed = true, gained = 50)
        val ranked = lb.ranked()
        assertEquals(listOf("e0", "e1"), ranked.map { it.entrantId }) // stable: registration order
    }

    @Test
    fun teamEntrant_aggregatesLikeAnyOtherEntrant() {
        val lb = Leaderboard()
        val team = Entrant("t0", "Team Red", ColorTag.RED, memberNames = listOf("A", "B"))
        lb.register(team)
        lb.recordRound("t0", passed = true, gained = 15) // member A's turn
        lb.recordRound("t0", passed = true, gained = 25) // member B's turn
        assertEquals(40, lb.ranked().single().score)
    }

    @Test
    fun coOpStreak_sharesOneBucket() {
        val lb = Leaderboard()
        lb.recordSharedRound(passed = true, gained = 10)
        lb.recordSharedRound(passed = true, gained = 10)
        val shared = lb.ranked().single { it.entrantId == Leaderboard.SHARED_ID }
        assertEquals(20, shared.score)
        assertEquals(2, shared.streak)
    }

    @Test
    fun winner_isTheTopScorer_nullWhenEmpty() {
        assertNull(Leaderboard().winner())
        val lb = Leaderboard()
        lb.register(Entrant("e0", "A", ColorTag.RED))
        lb.recordRound("e0", passed = true, gained = 5)
        assertEquals("e0", lb.winner()?.entrantId)
    }
}
