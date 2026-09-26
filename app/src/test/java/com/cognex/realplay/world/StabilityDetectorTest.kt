package com.cognex.realplay.world

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** JVM tests for [StabilityDetector] (Architecture §12, S3). */
class StabilityDetectorTest {

    private fun obj(id: Int, cx: Float, cy: Float) = TrackedObject(
        trackId = id, label = "cup", confidence = 0.9f,
        box = NormRect(cx - 0.05f, cy - 0.05f, cx + 0.05f, cy + 0.05f),
        center = NormPoint(cx, cy), color = ColorTag.RED, ageFrames = 5,
        lastSeenMs = 0L, velocity = NormPoint(0f, 0f),
        stable = false, stale = false, ambiguous = false
    )

    @Test
    fun static_becomesStableAfterWindow() {
        val s = StabilityDetector(windowMs = 500L, moveThreshold = 0.02f)
        var stable = emptySet<Int>()
        var ts = 0L
        // Feed a static object every 100 ms for 600 ms.
        repeat(7) {
            stable = s.update(listOf(obj(1, 0.5f, 0.5f)), ts)
            ts += 100
        }
        assertTrue(1 in stable)
    }

    @Test
    fun freshObject_isNotImmediatelyStable() {
        val s = StabilityDetector(windowMs = 500L, moveThreshold = 0.02f)
        val stable = s.update(listOf(obj(1, 0.5f, 0.5f)), 0L)
        assertFalse(1 in stable)
    }

    @Test
    fun movingObject_neverStable() {
        val s = StabilityDetector(windowMs = 500L, moveThreshold = 0.02f)
        var stable = emptySet<Int>()
        var ts = 0L
        var x = 0.2f
        repeat(8) {
            stable = s.update(listOf(obj(1, x, 0.5f)), ts)
            x += 0.05f // 0.05 per step ≫ 0.02 threshold
            ts += 100
        }
        assertFalse(1 in stable)
    }

    @Test
    fun sceneStable_requiresAllObjectsStable() {
        val stableObj = obj(1, 0.5f, 0.5f).copy(stable = true)
        val unstableObj = obj(2, 0.3f, 0.3f).copy(stable = false)
        val s = StabilityDetector()
        assertTrue(s.isSceneStable(listOf(stableObj)))
        assertFalse(s.isSceneStable(listOf(stableObj, unstableObj)))
        assertFalse(s.isSceneStable(emptyList()))
    }
}
