package com.cognex.realplay.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** HintEscalation — timed verbal/specific/visual thresholds (Architecture §10 rule 6, §13 S10). */
class HintEscalationTest {

    @Test
    fun levelFor_thresholds() {
        assertEquals(0, HintEscalation.levelFor(0L))
        assertEquals(0, HintEscalation.levelFor(7_999L))
        assertEquals(1, HintEscalation.levelFor(8_000L))
        assertEquals(1, HintEscalation.levelFor(15_999L))
        assertEquals(2, HintEscalation.levelFor(16_000L))
        assertEquals(2, HintEscalation.levelFor(23_999L))
        assertEquals(3, HintEscalation.levelFor(24_000L))
        assertEquals(3, HintEscalation.levelFor(999_999L))
    }

    @Test
    fun hintFor_picksTheRightHint_andFallsBack() {
        val hints = listOf("verbal", "specific")
        assertNull(HintEscalation.hintFor(hints, 0))
        assertEquals("verbal", HintEscalation.hintFor(hints, 1))
        assertEquals("specific", HintEscalation.hintFor(hints, 2))
        assertNull(HintEscalation.hintFor(hints, 3))
        // Falls back to the verbal hint when no specific one exists.
        assertEquals("verbal", HintEscalation.hintFor(listOf("verbal"), 2))
        assertNull(HintEscalation.hintFor(emptyList(), 1))
    }
}
