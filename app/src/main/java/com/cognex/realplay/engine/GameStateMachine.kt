package com.cognex.realplay.engine

import com.cognex.realplay.util.RpLog

/** The lifecycle states of one game session (Architecture §13 S5). Pure JVM. */
enum class GameState {
    IDLE,          // nothing selected yet
    SELECTING,     // registry is choosing a challenge
    INSTRUCTION,   // spec chosen, instruction being shown / spoken
    PLAYING,       // mission running, gates being fed
    PASSED,        // the mission's final step fired
    FAILED,        // evidence confidently false, or retries exhausted
    TIMED_OUT,     // time limit elapsed before passing
    RESULT         // terminal — score shown, session over
}

/**
 * The legal-transition table as DATA (Architecture §13 S5). Pure JVM.
 *
 * Illegal transitions are logged loudly and REJECTED (the state does not change), never silently
 * followed — an illegal transition is a bug we want to see, not absorb (§20). A unit test walks the
 * whole table.
 */
class GameStateMachine(initial: GameState = GameState.IDLE) {

    var state: GameState = initial
        private set

    /** Attempts [target]; returns true and moves if legal, else logs loudly and stays put. */
    fun transition(target: GameState): Boolean {
        if (target in legalNext(state)) {
            state = target
            return true
        }
        RpLog.w(RpLog.Tag.ENGINE, "ILLEGAL transition $state → $target ignored")
        return false
    }

    fun reset(to: GameState = GameState.IDLE) { state = to }

    companion object {
        /** The single source of truth for what may follow each state. */
        fun legalNext(state: GameState): Set<GameState> = when (state) {
            GameState.IDLE -> setOf(GameState.SELECTING)
            GameState.SELECTING -> setOf(GameState.INSTRUCTION, GameState.IDLE)
            GameState.INSTRUCTION -> setOf(GameState.PLAYING, GameState.SELECTING)
            GameState.PLAYING -> setOf(GameState.PASSED, GameState.FAILED, GameState.TIMED_OUT)
            GameState.PASSED -> setOf(GameState.RESULT)
            // A failure or timeout may retry (back to PLAYING) or end the session (RESULT).
            GameState.FAILED -> setOf(GameState.PLAYING, GameState.RESULT)
            GameState.TIMED_OUT -> setOf(GameState.PLAYING, GameState.RESULT)
            GameState.RESULT -> setOf(GameState.SELECTING, GameState.IDLE)
        }

        fun isLegal(from: GameState, to: GameState): Boolean = to in legalNext(from)
    }
}
