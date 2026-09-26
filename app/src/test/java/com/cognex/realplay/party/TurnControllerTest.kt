package com.cognex.realplay.party

import com.cognex.realplay.world.ColorTag
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** TurnController — rotation order, opponent naming, round numbering (Architecture §25). */
class TurnControllerTest {

    private fun roster(n: Int) = Roster((0 until n).map { Entrant("e$it", "P$it", ColorTag.RED) }, Roster.EntrantKind.PLAYER)

    @Test
    fun rotatesThroughEveryEntrant_thenWraps() {
        val r = roster(3)
        val tc = TurnController(r, PartyFormat.RELAY)
        assertEquals("e0", tc.current.id)
        assertEquals("e1", tc.advance().id)
        assertEquals("e2", tc.advance().id)
        assertEquals("e0", tc.advance().id) // wraps
    }

    @Test
    fun opponent_onlyForHeadToHead() {
        val r = roster(2)
        val relay = TurnController(r, PartyFormat.RELAY)
        assertNull(relay.opponent)

        val h2h = TurnController(r, PartyFormat.HEAD_TO_HEAD)
        assertEquals("e1", h2h.opponent?.id)
    }

    @Test
    fun roundNumber_incrementsAfterAFullLap() {
        val tc = TurnController(roster(3), PartyFormat.RELAY)
        assertEquals(1, tc.roundNumber(0))
        assertEquals(1, tc.roundNumber(2))
        assertEquals(2, tc.roundNumber(3))
        assertEquals(3, tc.roundNumber(6))
    }
}
