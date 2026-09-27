package com.cognex.realplay.perception

import com.cognex.realplay.world.ColorTag
import com.cognex.realplay.world.NormRect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** JVM synthetic-sequence tests for the [Tracker] (Architecture §12, S3). */
class TrackerTest {

    private fun det(label: String, cx: Float, cy: Float, w: Float = 0.2f, h: Float = 0.2f,
                    color: ColorTag? = ColorTag.RED, conf: Float = 0.9f) =
        RawDetection(label, conf, NormRect(cx - w / 2, cy - h / 2, cx + w / 2, cy + h / 2), color)

    @Test
    fun steadyState_promotesAfterThreeFrames_stableId() {
        val t = Tracker()
        assertTrue(t.update(listOf(det("cup", 0.5f, 0.5f)), 1000).isEmpty()) // hit 1
        assertTrue(t.update(listOf(det("cup", 0.5f, 0.5f)), 1100).isEmpty()) // hit 2
        val out = t.update(listOf(det("cup", 0.5f, 0.5f)), 1200)             // hit 3 → confirmed
        assertEquals(1, out.size)
        val id = out[0].trackId
        assertFalse(out[0].stale)
        assertFalse(out[0].ambiguous)
        // Id persists on the next frame.
        val out2 = t.update(listOf(det("cup", 0.5f, 0.5f)), 1300)
        assertEquals(id, out2[0].trackId)
    }

    @Test
    fun linearMotion_keepsIdAndHasVelocity() {
        val t = Tracker()
        var out = emptyList<com.cognex.realplay.world.TrackedObject>()
        var x = 0.3f
        var ts = 1000L
        repeat(5) {
            out = t.update(listOf(det("ball", x, 0.5f)), ts)
            x += 0.06f
            ts += 100
        }
        assertEquals(1, out.size)
        assertTrue("velocity should be positive rightward", out[0].velocity.x > 0f)
    }

    @Test
    fun occlusion_fourFrames_keepsSameId() {
        val t = Tracker()
        // Confirm a static object.
        t.update(listOf(det("cup", 0.5f, 0.5f)), 1000)
        t.update(listOf(det("cup", 0.5f, 0.5f)), 1100)
        val id = t.update(listOf(det("cup", 0.5f, 0.5f)), 1200)[0].trackId
        // 4 frames of occlusion (no detections) — the track coasts and is flagged stale.
        var ts = 1300L
        repeat(4) {
            val out = t.update(emptyList(), ts)
            assertEquals(1, out.size)
            assertTrue(out[0].stale)
            assertEquals(id, out[0].trackId)
            ts += 100
        }
        // Reappears → same id, no longer stale.
        val back = t.update(listOf(det("cup", 0.5f, 0.5f)), ts)
        assertEquals(id, back[0].trackId)
        assertFalse(back[0].stale)
    }

    @Test
    fun crossing_twoSimilarObjects_keepDistinctIds_bothAmbiguous() {
        val t = Tracker()
        // Two same-label, same-colour objects approaching each other; boxes overlap frame-to-frame.
        val leftX = floatArrayOf(0.30f, 0.375f, 0.45f, 0.50f)
        val rightX = floatArrayOf(0.70f, 0.625f, 0.55f, 0.50f)
        var out = emptyList<com.cognex.realplay.world.TrackedObject>()
        var ts = 1000L
        for (i in leftX.indices) {
            out = t.update(
                listOf(det("ball", leftX[i], 0.5f), det("ball", rightX[i], 0.5f)),
                ts
            )
            ts += 100
        }
        // At the crossing frame both tracks survive with distinct ids...
        assertEquals(2, out.size)
        assertNotEquals(out[0].trackId, out[1].trackId)
        // ...and both are flagged ambiguous.
        assertEquals(2, out.count { it.ambiguous })
    }

    @Test
    fun appearing_newObject_confirmsAfterThreeFrames() {
        val t = Tracker()
        // Object A confirmed.
        t.update(listOf(det("cup", 0.3f, 0.3f)), 1000)
        t.update(listOf(det("cup", 0.3f, 0.3f)), 1100)
        assertEquals(1, t.update(listOf(det("cup", 0.3f, 0.3f)), 1200).size)
        // Object B enters; needs 3 detections to appear.
        t.update(listOf(det("cup", 0.3f, 0.3f), det("book", 0.7f, 0.7f)), 1300)
        t.update(listOf(det("cup", 0.3f, 0.3f), det("book", 0.7f, 0.7f)), 1400)
        val out = t.update(listOf(det("cup", 0.3f, 0.3f), det("book", 0.7f, 0.7f)), 1500)
        assertEquals(2, out.size)
    }

    @Test
    fun reappearsWithinCoastWindow_keepsSameId() {
        val t = Tracker()
        // Confirm a stationary cup.
        t.update(listOf(det("cup", 0.5f, 0.5f)), 1000)
        t.update(listOf(det("cup", 0.5f, 0.5f)), 1100)
        val id = t.update(listOf(det("cup", 0.5f, 0.5f)), 1200)[0].trackId
        // Detection drops out for several frames (a mid-game occlusion) within the coast window.
        var ts = 1300L
        repeat(6) { t.update(emptyList(), ts); ts += 100 }
        // The same object reappears in the same spot — it must re-match its OWN track, not a new id.
        val back = t.update(listOf(det("cup", 0.5f, 0.5f)), ts)
        assertEquals(1, back.size)
        assertEquals(id, back[0].trackId)
    }

    @Test
    fun carriedToNewLocation_reIdsSameId() {
        val t = Tracker()
        // Confirm a red cup on the left.
        t.update(listOf(det("cup", 0.2f, 0.5f, color = ColorTag.RED)), 1000)
        t.update(listOf(det("cup", 0.2f, 0.5f, color = ColorTag.RED)), 1100)
        val id = t.update(listOf(det("cup", 0.2f, 0.5f, color = ColorTag.RED)), 1200)[0].trackId
        // It's picked up (occluded) briefly, then reappears FAR to the right — the same object at a
        // new location must keep its id (re-identification), not spawn a new one.
        t.update(emptyList(), 1300)
        t.update(emptyList(), 1400)
        val moved = t.update(listOf(det("cup", 0.85f, 0.5f, color = ColorTag.RED)), 1500)
        assertEquals(1, moved.size)
        assertEquals(id, moved[0].trackId)
    }

    @Test
    fun leaving_afterCoastWindow_isDeleted() {
        val t = Tracker()
        t.update(listOf(det("cup", 0.5f, 0.5f)), 1000)
        t.update(listOf(det("cup", 0.5f, 0.5f)), 1100)
        assertEquals(1, t.update(listOf(det("cup", 0.5f, 0.5f)), 1200).size)
        // Empty frames — coasts for COAST_FRAMES (20), deleted once missed exceeds it.
        var ts = 1300L
        var out = emptyList<com.cognex.realplay.world.TrackedObject>()
        repeat(21) {
            out = t.update(emptyList(), ts)
            ts += 100
        }
        assertTrue(out.isEmpty())
    }
}
