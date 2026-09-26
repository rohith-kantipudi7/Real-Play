package com.cognex.realplay.party

import com.cognex.realplay.challenge.ChallengeType
import com.cognex.realplay.challenge.RankedCandidate
import com.cognex.realplay.challenge.SelectionMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** PartyOrchestrator — variety policy + fairness signal across a rotation (Architecture §25). */
class PartyOrchestratorTest {

    private fun candidate(score: Float) =
        RankedCandidate("G", ChallengeType.MOVE_CLOSE, score, 1f, 1f, 1f, score, if (score <= 0f) "n/a" else null)

    @Test
    fun everyFormat_usesOpenForVariety() {
        for (format in PartyFormat.entries) {
            assertEquals(SelectionMode.OPEN, PartyOrchestrator.selectionModeFor(format))
        }
    }

    @Test
    fun trimHistory_keepsOnlyTheRecentWindow() {
        val history = listOf(ChallengeType.MOVE_CLOSE, ChallengeType.DROP_ZONE, ChallengeType.FIND_COLOR, ChallengeType.FETCH_RACE)
        val trimmed = PartyOrchestrator.trimHistory(history, windowSize = 3)
        assertEquals(listOf(ChallengeType.DROP_ZONE, ChallengeType.FIND_COLOR, ChallengeType.FETCH_RACE), trimmed)
    }

    @Test
    fun isRepeat_comparesAgainstTheLastType() {
        assertTrue(PartyOrchestrator.isRepeat(ChallengeType.MOVE_CLOSE, ChallengeType.MOVE_CLOSE))
        assertFalse(PartyOrchestrator.isRepeat(ChallengeType.MOVE_CLOSE, ChallengeType.DROP_ZONE))
        assertFalse(PartyOrchestrator.isRepeat(null, ChallengeType.MOVE_CLOSE))
    }

    @Test
    fun isFeasibilityComparable_trueWhenCloseOrTooFewCandidates() {
        assertTrue(PartyOrchestrator.isFeasibilityComparable(emptyList()))
        assertTrue(PartyOrchestrator.isFeasibilityComparable(listOf(candidate(0.8f))))
        assertTrue(PartyOrchestrator.isFeasibilityComparable(listOf(candidate(0.8f), candidate(0.7f))))
    }

    @Test
    fun isFeasibilityComparable_falseWhenOneSkillDominates() {
        assertFalse(PartyOrchestrator.isFeasibilityComparable(listOf(candidate(0.9f), candidate(0.1f))))
    }
}
