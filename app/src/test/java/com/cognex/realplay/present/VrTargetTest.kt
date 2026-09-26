package com.cognex.realplay.present

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** VrTarget — the area-to-parallax depth proxy (Architecture v3.6-D). */
class VrTargetTest {

    @Test
    fun parallaxGrowsWithArea() {
        val near = VrTarget.parallaxFor(1f)
        val mid = VrTarget.parallaxFor(0.5f)
        val far = VrTarget.parallaxFor(0f)
        assertTrue(near > mid)
        assertTrue(mid > far)
    }

    @Test
    fun boundsAreRespectedEvenOutOfRange() {
        assertEquals(VrTarget.parallaxFor(0f), VrTarget.parallaxFor(-5f), 1e-6f)
        assertEquals(VrTarget.parallaxFor(1f), VrTarget.parallaxFor(5f), 1e-6f)
    }
}
