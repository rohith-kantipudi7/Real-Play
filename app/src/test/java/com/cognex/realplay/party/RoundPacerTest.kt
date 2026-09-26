package com.cognex.realplay.party

import com.cognex.realplay.challenge.AgeBand
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** RoundPacer — party pacing bounds, toddler-safe (Architecture §25, §10). */
class RoundPacerTest {

    @Test
    fun toddler_hasNoClock() {
        assertNull(RoundPacer.roundDurationMs(AgeBand.TODDLER))
    }

    @Test
    fun everyOtherBand_getsABoundedDuration() {
        for (band in listOf(AgeBand.EARLY, AgeBand.MIDDLE, AgeBand.OLDER)) {
            val ms = RoundPacer.roundDurationMs(band)
            assertTrue("expected a duration for $band", ms != null)
            assertTrue(ms!! in RoundPacer.MIN_ROUND_MS..RoundPacer.MAX_ROUND_MS)
        }
    }
}
