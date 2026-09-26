package com.cognex.realplay.engine

import com.cognex.realplay.challenge.RankedCandidate
import com.cognex.realplay.verify.Evidence

/** The play-phase the UI renders (Architecture §13 S5, §S6 refines the visuals). */
enum class PlayStatus { SELECTING, PLAYING, PASSED, FAILED, TIMED_OUT, COACHING }

/**
 * The ONE immutable UI state the [GameViewModel] exposes (Architecture §3.3 threading contract,
 * §13 S5). Everything the game screen needs to draw a frame lives here; the screen is a pure
 * function of this state.
 */
data class GameUiState(
    val status: PlayStatus,
    val instruction: String,
    val stepIndex: Int,
    val stepCount: Int,
    val completedSteps: Int,
    val stepProgress: Float,
    val score: Int,
    val streak: Int,
    val challengeIndex: Int,
    val lastGain: Int,
    val perfect: Boolean,
    val coachingHint: String?,
    /** Current step's measured facts, each in its own MeasurementDomain (§12, §20 invariant 6). */
    val evidence: List<Evidence>,
    val timeRemainingMs: Long?,
    /** Remaining fraction of the time limit (1→0), or null when the challenge is untimed. */
    val timeFraction: Float?,
    val retriesLeft: Int,
    val winnerId: String,
    val winnerType: String,
    val ranked: List<RankedCandidate>,
    /** Presentable difficulty "why" for the dev overlay (§8, §13 S9), e.g. "skill=HARD but richness 0.41 → playing MEDIUM". */
    val difficultyExplanation: String,
    val sessionOver: Boolean
) {
    companion object {
        val INITIAL = GameUiState(
            status = PlayStatus.SELECTING,
            instruction = "Point me at your play space…",
            stepIndex = 0,
            stepCount = 1,
            completedSteps = 0,
            stepProgress = 0f,
            score = 0,
            streak = 0,
            challengeIndex = 0,
            lastGain = 0,
            perfect = false,
            coachingHint = null,
            evidence = emptyList(),
            timeRemainingMs = null,
            timeFraction = null,
            retriesLeft = 2,
            winnerId = "",
            winnerType = "",
            ranked = emptyList(),
            difficultyExplanation = "",
            sessionOver = false
        )
    }
}
