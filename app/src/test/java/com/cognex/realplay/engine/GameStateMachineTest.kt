package com.cognex.realplay.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The state machine only follows its legal-transition table (Architecture §13 S5). */
class GameStateMachineTest {

    @Test
    fun happyPath_walksTheTable() {
        val sm = GameStateMachine()
        assertEquals(GameState.IDLE, sm.state)
        assertTrue(sm.transition(GameState.SELECTING))
        assertTrue(sm.transition(GameState.INSTRUCTION))
        assertTrue(sm.transition(GameState.PLAYING))
        assertTrue(sm.transition(GameState.PASSED))
        assertTrue(sm.transition(GameState.RESULT))
        assertTrue(sm.transition(GameState.SELECTING))
    }

    @Test
    fun illegalTransition_isRejected_stateUnchanged() {
        val sm = GameStateMachine()
        // IDLE → PLAYING is illegal (must select + instruct first).
        assertFalse(sm.transition(GameState.PLAYING))
        assertEquals(GameState.IDLE, sm.state)
        // IDLE → PASSED is illegal.
        assertFalse(sm.transition(GameState.PASSED))
        assertEquals(GameState.IDLE, sm.state)
    }

    @Test
    fun failAndTimeout_mayRetryOrEnd() {
        assertTrue(GameStateMachine.isLegal(GameState.PLAYING, GameState.FAILED))
        assertTrue(GameStateMachine.isLegal(GameState.FAILED, GameState.PLAYING))   // retry
        assertTrue(GameStateMachine.isLegal(GameState.FAILED, GameState.RESULT))    // give up
        assertTrue(GameStateMachine.isLegal(GameState.TIMED_OUT, GameState.PLAYING))
        assertTrue(GameStateMachine.isLegal(GameState.TIMED_OUT, GameState.RESULT))
    }

    @Test
    fun everyStateHasAtLeastOneLegalExit() {
        for (state in GameState.entries) {
            assertTrue("$state is a dead end", GameStateMachine.legalNext(state).isNotEmpty())
        }
    }

    @Test
    fun noSelfLoopsDeclared() {
        for (state in GameState.entries) {
            assertFalse("$state loops to itself", state in GameStateMachine.legalNext(state))
        }
    }
}
