package com.cognex.realplay.party

import com.cognex.realplay.challenge.AgeBand
import com.cognex.realplay.challenge.ChallengeType
import com.cognex.realplay.challenge.SelectionMode
import com.cognex.realplay.world.ColorTag
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** PartySession — the full rotation lifecycle over the shipped skills (Architecture §25). */
class PartySessionTest {

    private fun roster(n: Int, kind: Roster.EntrantKind = Roster.EntrantKind.PLAYER) =
        Roster((0 until n).map { Entrant("e$it", "P$it", ColorTag.RED) }, kind)

    @Test
    fun sessionEndsExactlyAfterEveryEntrantHasPlayedOneLap() {
        val session = PartySession(roster(3), PartyFormat.RELAY, AgeBand.OLDER)
        assertFalse(session.sessionOver)
        session.completeRound(ChallengeType.MOVE_CLOSE, passed = true, gained = 10)
        assertFalse(session.sessionOver)
        session.completeRound(ChallengeType.DROP_ZONE, passed = true, gained = 10)
        assertFalse(session.sessionOver)
        session.completeRound(ChallengeType.FIND_COLOR, passed = true, gained = 10)
        assertTrue(session.sessionOver)
        assertEquals(3, session.turnsPlayed)
    }

    @Test
    fun rotatesToTheNextEntrantAfterEachRound() {
        val session = PartySession(roster(2), PartyFormat.RELAY, AgeBand.OLDER)
        assertEquals("e0", session.turns.current.id)
        session.completeRound(ChallengeType.MOVE_CLOSE, passed = true, gained = 5)
        assertEquals("e1", session.turns.current.id)
    }

    @Test
    fun relay_scoresPerEntrant() {
        val session = PartySession(roster(2), PartyFormat.RELAY, AgeBand.OLDER)
        session.completeRound(ChallengeType.MOVE_CLOSE, passed = true, gained = 40)
        session.completeRound(ChallengeType.MOVE_CLOSE, passed = true, gained = 10)
        assertEquals(40, session.leaderboard.ranked().first { it.entrantId == "e0" }.score)
        assertEquals(10, session.leaderboard.ranked().first { it.entrantId == "e1" }.score)
    }

    @Test
    fun coOpStreak_poolsEveryRoundIntoOneSharedScore() {
        val session = PartySession(roster(2), PartyFormat.CO_OP_STREAK, AgeBand.OLDER)
        session.completeRound(ChallengeType.MOVE_CLOSE, passed = true, gained = 10)
        session.completeRound(ChallengeType.DROP_ZONE, passed = true, gained = 10)
        assertEquals(20, session.leaderboard.ranked().single().score)
    }

    @Test
    fun selectionModeIsOpen_andHistoryFeedsBackIntoRecentTypes() {
        val session = PartySession(roster(2), PartyFormat.RELAY, AgeBand.OLDER)
        assertEquals(SelectionMode.OPEN, session.selectionMode)
        session.completeRound(ChallengeType.MOVE_CLOSE, passed = true, gained = 10)
        assertEquals(listOf(ChallengeType.MOVE_CLOSE), session.recentTypes)
    }

    @Test
    fun toddlerSession_hasNoRoundClock() {
        val session = PartySession(roster(2), PartyFormat.RELAY, AgeBand.TODDLER)
        assertEquals(null, session.roundDurationMs)
    }
}
