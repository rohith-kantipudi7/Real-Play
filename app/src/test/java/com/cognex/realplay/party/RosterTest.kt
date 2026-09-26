package com.cognex.realplay.party

import com.cognex.realplay.world.ColorTag
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

/** Roster — size bounds + unique ids (Architecture §25). */
class RosterTest {

    private fun players(n: Int) = (0 until n).map { Entrant("e$it", "P$it", ColorTag.RED) }

    @Test
    fun acceptsWithinPlayerBounds() {
        Roster(players(Roster.MIN_PLAYERS), Roster.EntrantKind.PLAYER)
        Roster(players(Roster.MAX_PLAYERS), Roster.EntrantKind.PLAYER)
    }

    @Test
    fun rejectsTooFewPlayers() {
        assertThrows(IllegalArgumentException::class.java) {
            Roster(players(Roster.MIN_PLAYERS - 1), Roster.EntrantKind.PLAYER)
        }
    }

    @Test
    fun rejectsTooManyPlayers() {
        assertThrows(IllegalArgumentException::class.java) {
            Roster(players(Roster.MAX_PLAYERS + 1), Roster.EntrantKind.PLAYER)
        }
    }

    @Test
    fun rejectsTooFewOrTooManyTeams() {
        assertThrows(IllegalArgumentException::class.java) {
            Roster(players(Roster.MIN_TEAMS - 1), Roster.EntrantKind.TEAM)
        }
        assertThrows(IllegalArgumentException::class.java) {
            Roster(players(Roster.MAX_TEAMS + 1), Roster.EntrantKind.TEAM)
        }
    }

    @Test
    fun rejectsDuplicateIds() {
        assertThrows(IllegalArgumentException::class.java) {
            Roster(
                listOf(Entrant("e0", "A", ColorTag.RED), Entrant("e0", "B", ColorTag.BLUE)),
                Roster.EntrantKind.PLAYER
            )
        }
    }

    @Test
    fun preservesEntrantOrder() {
        val roster = Roster(players(3), Roster.EntrantKind.PLAYER)
        assertEquals(listOf("e0", "e1", "e2"), roster.entrants.map { it.id })
    }
}
