package com.cognex.realplay.engine

import com.cognex.realplay.verify.Evidence

/**
 * The per-challenge outcome the [com.cognex.realplay.ui.result.ResultScreen] lists (Architecture
 * §13 S6). [evidence] keeps each measurement in its own [com.cognex.realplay.verify.MeasurementDomain]
 * so the result screen can word it honestly (§12), exactly like the live EvidencePanel.
 */
data class ChallengeResult(
    val index: Int,
    val type: String,
    val winnerId: String,
    val passed: Boolean,
    val timedOut: Boolean,
    val score: Int,
    val stepCount: Int,
    val completedSteps: Int,
    val evidence: List<Evidence>
)

/**
 * Process-lifetime holder for the just-played session, read by the result screen after the game
 * screen ends (Architecture §13 S6). The [GameViewModel] resets it when a session starts and
 * appends one [ChallengeResult] as each challenge resolves. No game logic lives here — it is a
 * presentation hand-off between two navigation destinations.
 */
object SessionResults {
    private val _results = mutableListOf<ChallengeResult>()
    val results: List<ChallengeResult> get() = _results.toList()

    var totalScore: Int = 0
        private set
    var bestStreak: Int = 0
        private set

    fun reset() {
        _results.clear()
        totalScore = 0
        bestStreak = 0
    }

    fun record(result: ChallengeResult, totalScore: Int, bestStreak: Int) {
        _results.add(result)
        this.totalScore = totalScore
        this.bestStreak = bestStreak
    }
}
