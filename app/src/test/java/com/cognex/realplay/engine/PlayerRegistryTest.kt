package com.cognex.realplay.engine

import com.cognex.realplay.world.Landmark
import com.cognex.realplay.world.NormRect
import com.cognex.realplay.world.TrackedPlayer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** JVM tests for player registration and turn rotation (Architecture §5, §7). */
class PlayerRegistryTest {

    private fun player(id: Int, ambiguous: Boolean = false) = TrackedPlayer(
        playerId = id, landmarks = emptyList<Landmark>(),
        torsoBox = NormRect(0.4f, 0.3f, 0.6f, 0.7f), colorBand = null,
        motionEnergy = 0f, confidence = 0.9f, ambiguous = ambiguous
    )

    @Test
    fun `the first registered player becomes active`() {
        val reg = PlayerRegistry()
        assertNull(reg.activePlayerId)
        reg.sync(listOf(player(1)))
        assertEquals(listOf(1), reg.registered())
        assertEquals(1, reg.activePlayerId)
    }

    @Test
    fun `a second player registers but does not steal the turn`() {
        val reg = PlayerRegistry()
        reg.sync(listOf(player(1)))
        val added = reg.sync(listOf(player(1), player(2)))
        assertEquals(listOf(2), added)
        assertEquals(listOf(1, 2), reg.registered())
        assertEquals("active stays with P1 until the turn advances", 1, reg.activePlayerId)
    }

    @Test
    fun `advanceTurn rotates between two players and wraps`() {
        val reg = PlayerRegistry()
        reg.sync(listOf(player(1), player(2)))
        assertEquals(1, reg.activePlayerId)
        assertEquals(2, reg.advanceTurn())
        assertEquals(1, reg.advanceTurn())
    }

    @Test
    fun `a single player stays active across turn advances`() {
        val reg = PlayerRegistry()
        reg.sync(listOf(player(1)))
        assertEquals(1, reg.advanceTurn())
        assertEquals(1, reg.advanceTurn())
    }

    @Test
    fun `registration is capped at maxPlayers (no 3rd player)`() {
        val reg = PlayerRegistry(maxPlayers = 2)
        reg.sync(listOf(player(1), player(2), player(3)))
        assertEquals(listOf(1, 2), reg.registered())
        assertFalse(reg.isRegistered(3))
    }

    @Test
    fun `ambiguous players never earn a slot`() {
        val reg = PlayerRegistry()
        reg.sync(listOf(player(1, ambiguous = true)))
        assertTrue(reg.registered().isEmpty())
        assertNull(reg.activePlayerId)
    }

    @Test
    fun `reset clears registration and active player`() {
        val reg = PlayerRegistry()
        reg.sync(listOf(player(1), player(2)))
        reg.reset()
        assertTrue(reg.registered().isEmpty())
        assertNull(reg.activePlayerId)
    }
}
