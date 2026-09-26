package com.cognex.realplay.challenge

import com.cognex.realplay.world.ColorTag
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** SafetyFilter runs before generation and fails closed (Architecture §11, §20 invariant 9). */
class SafetyFilterTest {

    @Test
    fun deniesUnsafeObjects_atEveryAge() {
        val knife = CFix.obj(1, label = "knife")
        assertNotNull(SafetyFilter.rejection(knife, AgeBand.OLDER))
        assertFalse(SafetyFilter.isAllowed(knife, AgeBand.OLDER))
    }

    @Test
    fun allowsOrdinaryObjects() {
        val ball = CFix.obj(1, label = "ball")
        assertNull(SafetyFilter.rejection(ball, AgeBand.OLDER))
        assertTrue(SafetyFilter.isAllowed(ball, AgeBand.OLDER))
    }

    @Test
    fun toddler_rejectsTinyAndUnlabelled() {
        val tiny = CFix.obj(1, label = "bead", w = 0.05f, h = 0.05f)   // area 0.0025 < 1.5%
        val unknown = CFix.obj(2, label = "", w = 0.3f, h = 0.3f)
        assertFalse(SafetyFilter.isAllowed(tiny, AgeBand.TODDLER))
        assertFalse(SafetyFilter.isAllowed(unknown, AgeBand.TODDLER))
        // The same objects are fine for older bands.
        assertTrue(SafetyFilter.isAllowed(tiny, AgeBand.MIDDLE))
        assertTrue(SafetyFilter.isAllowed(unknown, AgeBand.MIDDLE))
    }

    @Test
    fun apply_removesDeniedObjectsFromWorld() {
        val world = CFix.world(
            objects = listOf(CFix.obj(1, label = "knife"), CFix.obj(2, label = "cup", color = ColorTag.GREEN)),
            affordances = listOf(CFix.aff(1, movable = true), CFix.aff(2, movable = true, container = true))
        )
        val safe = SafetyFilter.apply(world, AgeBand.MIDDLE)
        assertEquals(1, safe.objects.size)
        assertEquals(2, safe.objects.first().trackId)
        assertEquals(1, safe.affordances.size)
    }

    private fun assertNotNull(value: Any?) = assertTrue(value != null)
}
