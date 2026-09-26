package com.cognex.realplay.engine

import com.cognex.realplay.challenge.RankedCandidate

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
    val stepProgress: Float,
    val score: Int,
    val streak: Int,
    val coachingHint: String?,
    val evidenceLine: String?,
    val timeRemainingMs: Long?,
    val retriesLeft: Int,
    val winnerId: String,
    val winnerType: String,
    val ranked: List<RankedCandidate>,
    val sessionOver: Boolean
) {
    companion object {
        val INITIAL = GameUiState(
            status = PlayStatus.SELECTING,
            instruction = "Point me at your play space…",
            stepIndex = 0,
            stepCount = 1,
            stepProgress = 0f,
            score = 0,
            streak = 0,
            coachingHint = null,
            evidenceLine = null,
            timeRemainingMs = null,
            retriesLeft = 2,
            winnerId = "",
            winnerType = "",
            ranked = emptyList(),
            sessionOver = false
        )
    }
}
