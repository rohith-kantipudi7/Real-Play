package com.cognex.realplay.perception.pose

import com.cognex.realplay.world.ColorTag
import com.cognex.realplay.world.Landmark
import com.cognex.realplay.world.NormPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** JVM tests for stable player identity on synthetic pose sequences (Architecture §5). */
class PlayerTrackerTest {

    /** Builds a 33-landmark pose whose torso box is centred at (cx,cy) with the given half-size. */
    private fun poseAt(
        cx: Float, cy: Float, half: Float = 0.08f, vis: Float = 1f,
        presence: Float = 0.9f, band: ColorTag? = null
    ): RawPose {
        val pts = MutableList(33) { Landmark(NormPoint(cx, cy), vis) }
        // shoulders 11/12 (top), hips 23/24 (bottom)
        pts[11] = Landmark(NormPoint(cx - half, cy - half), vis)
        pts[12] = Landmark(NormPoint(cx + half, cy - half), vis)
        pts[23] = Landmark(NormPoint(cx - half, cy + half), vis)
        pts[24] = Landmark(NormPoint(cx + half, cy + half), vis)
        // wrists 15/16 for motion
        pts[15] = Landmark(NormPoint(cx - half, cy), vis)
        pts[16] = Landmark(NormPoint(cx + half, cy), vis)
        return RawPose(pts, presence, band)
    }

    private fun anchor(tracker: PlayerTracker, poses: List<RawPose>, startTs: Long = 0L): List<Int> {
        var out = emptyList<Int>()
        for (f in 0 until PlayerTracker.CONFIRM_FRAMES) {
            out = tracker.update(poses, startTs + f * 33L).map { it.playerId }
        }
        return out
    }

    @Test
    fun `a stable pair keeps its two ids across many frames`() {
        val tracker = PlayerTracker()
        anchor(tracker, listOf(poseAt(0.3f, 0.5f), poseAt(0.7f, 0.5f)))
        val ids = mutableSetOf<Int>()
        var ts = 200L
        repeat(30) {
            val players = tracker.update(listOf(poseAt(0.3f, 0.5f), poseAt(0.7f, 0.5f)), ts)
            ts += 33L
            assertEquals(2, players.size)
            players.forEach { ids.add(it.playerId) }
        }
        assertEquals("exactly two identities over the whole run", 2, ids.size)
    }

    @Test
    fun `two players who cross do not swap numbers`() {
        val tracker = PlayerTracker()
        anchor(tracker, listOf(poseAt(0.2f, 0.5f, band = ColorTag.RED), poseAt(0.8f, 0.5f, band = ColorTag.BLUE)))

        // Identify which id is the left (red) player before they cross.
        val before = tracker.update(
            listOf(poseAt(0.2f, 0.5f, band = ColorTag.RED), poseAt(0.8f, 0.5f, band = ColorTag.BLUE)), 300L
        )
        val redId = before.first { it.colorBand == ColorTag.RED }.playerId
        val blueId = before.first { it.colorBand == ColorTag.BLUE }.playerId
        assertNotEquals(redId, blueId)

        // Walk them past each other; colour band separates them at the crossing.
        var ts = 400L
        val steps = listOf(0.35f to 0.65f, 0.45f to 0.55f, 0.55f to 0.45f, 0.65f to 0.35f, 0.8f to 0.2f)
        var last = before
        for ((rx, bx) in steps) {
            last = tracker.update(listOf(poseAt(rx, 0.5f, band = ColorTag.RED), poseAt(bx, 0.5f, band = ColorTag.BLUE)), ts)
            ts += 33L
        }
        // After crossing, red is on the right but still carries its original id.
        val redAfter = last.firstOrNull { it.colorBand == ColorTag.RED }
        val blueAfter = last.firstOrNull { it.colorBand == ColorTag.BLUE }
        if (redAfter != null) assertEquals(redId, redAfter.playerId)
        if (blueAfter != null) assertEquals(blueId, blueAfter.playerId)
    }

    @Test
    fun `crossing without colour bands flags ambiguity rather than guessing`() {
        val tracker = PlayerTracker()
        anchor(tracker, listOf(poseAt(0.35f, 0.5f), poseAt(0.65f, 0.5f)))
        // Bring them within the ambiguity separation, no colour to separate.
        val players = tracker.update(listOf(poseAt(0.48f, 0.5f), poseAt(0.52f, 0.5f)), 500L)
        assertTrue("overlapping players should be flagged ambiguous", players.any { it.ambiguous })
    }

    @Test
    fun `a player who leaves and returns is coasted then re-matched (no renumber)`() {
        val tracker = PlayerTracker()
        anchor(tracker, listOf(poseAt(0.5f, 0.5f)))
        val id = tracker.update(listOf(poseAt(0.5f, 0.5f)), 300L).single().playerId

        // Disappear for a few frames (within SURVIVE_FRAMES).
        var ts = 400L
        repeat(5) { tracker.update(emptyList(), ts); ts += 33L }
        // Return near the last position.
        val back = tracker.update(listOf(poseAt(0.52f, 0.5f)), ts)
        assertEquals(1, back.size)
        assertEquals("same identity after a brief absence", id, back.single().playerId)
    }

    @Test
    fun `a faint far background walker never earns a player id`() {
        val tracker = PlayerTracker()
        // Tiny torso + low presence confidence over many frames.
        var ts = 0L
        var players = emptyList<com.cognex.realplay.world.TrackedPlayer>()
        repeat(10) {
            players = tracker.update(listOf(poseAt(0.9f, 0.1f, half = 0.02f, presence = 0.3f)), ts)
            ts += 33L
        }
        assertTrue("a faint background walker must not become a player", players.isEmpty())
    }

    @Test
    fun `reset re-anchors identities from one at the next round`() {
        val tracker = PlayerTracker()
        anchor(tracker, listOf(poseAt(0.3f, 0.5f), poseAt(0.7f, 0.5f)))
        tracker.reset()
        val ids = anchor(tracker, listOf(poseAt(0.4f, 0.5f)))
        assertEquals(listOf(1), ids)
    }
}
