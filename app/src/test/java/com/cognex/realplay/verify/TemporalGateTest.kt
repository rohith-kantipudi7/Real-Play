package com.cognex.realplay.verify

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The single most important S4 test (Architecture §4.2, §20 invariant 3): a lone satisfying frame
 * in a stream of unsatisfying frames must NEVER pass a temporal rule, and an 80%-satisfied window
 * held long enough must pass.
 */
class TemporalGateTest {

    private fun sat(t: Long) = TemporalGate.Sample(t, satisfied = true, eligible = true)
    private fun unsat(t: Long) = TemporalGate.Sample(t, satisfied = false, eligible = true)
    private fun uncertain(t: Long) = TemporalGate.Sample(t, satisfied = false, eligible = false)

    @Test
    fun singleTrueFrame_amongFalse_neverFires() {
        val gate = TemporalGate(holdMs = 300)
        // Long stream of false frames with one true frame in the middle.
        gate.record(unsat(0))
        gate.record(unsat(100))
        gate.record(sat(200))     // the one lucky frame
        gate.record(unsat(300))
        gate.record(unsat(400))
        assertFalse(gate.fired())
    }

    @Test
    fun singleTrueFrameOnly_neverFires_needsMinEligible() {
        val gate = TemporalGate(holdMs = 0)
        gate.record(sat(0))
        assertFalse("one eligible frame can never satisfy MIN_ELIGIBLE", gate.fired())
    }

    @Test
    fun sustainedHold_fires() {
        val gate = TemporalGate(holdMs = 300)
        gate.record(sat(0))
        gate.record(sat(100))
        gate.record(sat(200))
        gate.record(sat(300))
        assertTrue(gate.fired())
    }

    @Test
    fun eightyPercentOverWindow_fires() {
        val gate = TemporalGate(holdMs = 400)
        // 5 eligible frames, 4 satisfied (80%), latest satisfied, spanning 400ms.
        gate.record(sat(0))
        gate.record(sat(100))
        gate.record(unsat(200))   // the one miss (20%)
        gate.record(sat(300))
        gate.record(sat(400))
        assertTrue(gate.fired())
    }

    @Test
    fun below80Percent_doesNotFire() {
        val gate = TemporalGate(holdMs = 400)
        gate.record(sat(0))
        gate.record(unsat(100))
        gate.record(unsat(200))   // 3/5 = 60%
        gate.record(unsat(300))
        gate.record(sat(400))
        assertFalse(gate.fired())
    }

    @Test
    fun latestFrameMustSatisfy() {
        val gate = TemporalGate(holdMs = 200)
        gate.record(sat(0))
        gate.record(sat(100))
        gate.record(unsat(200))   // latest is a miss
        assertFalse(gate.fired())
    }

    @Test
    fun heldTooBriefly_doesNotFire() {
        val gate = TemporalGate(holdMs = 500)
        gate.record(sat(0))
        gate.record(sat(50))      // only 50ms of the required 500ms
        assertFalse(gate.fired())
    }

    @Test
    fun uncertainFramesAreIneligible_notFailures() {
        val gate = TemporalGate(holdMs = 200)
        gate.record(sat(0))
        gate.record(uncertain(100))  // Unsure — ignored, not counted against
        gate.record(sat(200))
        assertTrue(gate.fired())
    }

    @Test
    fun progress_isZeroWhenLatestNotSatisfying() {
        val gate = TemporalGate(holdMs = 300)
        gate.record(sat(0))
        gate.record(unsat(100))
        assertEquals(0f, gate.progress(), 1e-6f)
    }

    @Test
    fun reset_clearsWindow() {
        val gate = TemporalGate(holdMs = 100)
        gate.record(sat(0))
        gate.record(sat(100))
        assertTrue(gate.fired())
        gate.reset()
        assertFalse(gate.fired())
    }
}
