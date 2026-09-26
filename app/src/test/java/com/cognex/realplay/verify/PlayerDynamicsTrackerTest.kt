package com.cognex.realplay.verify

import com.cognex.realplay.world.ColorTag
import com.cognex.realplay.world.Landmark
import com.cognex.realplay.world.NormPoint
import com.cognex.realplay.world.NormRect
import com.cognex.realplay.world.TrackedPlayer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** JVM tests for session-accumulated player dynamics (Architecture §6.2). */
class PlayerDynamicsTrackerTest {

    /** A player whose four torso joints (11/12/23/24) form a box around (cx,cy) plus wrists. */
    private fun player(
        id: Int, cx: Float, cy: Float, armLift: Float = 0f,
        half: Float = 0.08f, vis: Float = 1f, confidence: Float = 0.9f, ambiguous: Boolean = false
    ): TrackedPlayer {
        val pts = MutableList(33) { Landmark(NormPoint(cx, cy), vis) }
        pts[11] = Landmark(NormPoint(cx - half, cy - half), vis)
        pts[12] = Landmark(NormPoint(cx + half, cy - half), vis)
        pts[23] = Landmark(NormPoint(cx - half, cy + half), vis)
        pts[24] = Landmark(NormPoint(cx + half, cy + half), vis)
        // Wrists rise AND swing outward with armLift, sweeping the elbow angle (not collinear) so
        // distinct lifts land in distinct pose clusters and produce real frame-to-frame motion.
        pts[13] = Landmark(NormPoint(cx - half, cy - half * 0.5f), vis)
        pts[14] = Landmark(NormPoint(cx + half, cy - half * 0.5f), vis)
        pts[15] = Landmark(NormPoint(cx - half - armLift, cy - armLift), vis)
        pts[16] = Landmark(NormPoint(cx + half + armLift, cy - armLift), vis)
        pts[25] = Landmark(NormPoint(cx - half, cy + half * 2f), vis)
        pts[26] = Landmark(NormPoint(cx + half, cy + half * 2f), vis)
        pts[27] = Landmark(NormPoint(cx - half, cy + half * 3f), vis)
        pts[28] = Landmark(NormPoint(cx + half, cy + half * 3f), vis)
        return TrackedPlayer(
            playerId = id, landmarks = pts,
            torsoBox = NormRect(cx - half, cy - half, cx + half, cy + half),
            colorBand = ColorTag.RED, motionEnergy = 0f, confidence = confidence, ambiguous = ambiguous
        )
    }

    @Test
    fun `a perfectly still player has near-zero variety and motion`() {
        val t = PlayerDynamicsTracker()
        var ts = 0L
        repeat(30) { t.observe(listOf(player(1, 0.5f, 0.5f)), ts); ts += 100L }
        val d = t.dynamics()
        assertTrue("one held pose is minimal variety", d.poseVariety <= 0.2f)
        assertEquals("no movement", 0f, d.motionRange, 1e-3f)
    }

    @Test
    fun `moving through many distinct shapes raises poseVariety`() {
        val t = PlayerDynamicsTracker()
        var ts = 0L
        // Cycle the arm lift through many different heights → distinct pose clusters.
        for (lift in listOf(0.0f, 0.05f, 0.10f, 0.16f, 0.22f, 0.28f, 0.34f, 0.40f)) {
            t.observe(listOf(player(1, 0.5f, 0.5f, armLift = lift)), ts); ts += 100L
        }
        assertTrue("varied shapes should lift poseVariety", t.dynamics().poseVariety >= 0.6f)
    }

    @Test
    fun `large landmark motion raises motionRange above a still player`() {
        val still = PlayerDynamicsTracker()
        val moving = PlayerDynamicsTracker()
        var ts = 0L
        repeat(12) {
            still.observe(listOf(player(1, 0.5f, 0.5f)), ts)
            // Big alternating arm swings frame to frame.
            val lift = if (it % 2 == 0) 0.0f else 0.35f
            moving.observe(listOf(player(1, 0.5f, 0.5f, armLift = lift)), ts)
            ts += 100L
        }
        assertTrue("still player has ~no motion", still.dynamics().motionRange <= 0.05f)
        assertTrue("swinging player has real motion", moving.dynamics().motionRange >= 0.3f)
    }

    @Test
    fun `ambiguous and low-confidence players are ignored`() {
        val t = PlayerDynamicsTracker()
        var ts = 0L
        repeat(10) {
            t.observe(listOf(player(1, 0.5f, 0.5f, armLift = 0.3f * it, ambiguous = true)), ts)
            t.observe(listOf(player(2, 0.5f, 0.5f, armLift = 0.3f * it, confidence = 0.2f)), ts)
            ts += 100L
        }
        val d = t.dynamics()
        assertEquals("ignored players contribute no variety", 0f, d.poseVariety, 1e-3f)
        assertEquals("ignored players contribute no motion", 0f, d.motionRange, 1e-3f)
    }

    @Test
    fun `reset clears accumulated dynamics`() {
        val t = PlayerDynamicsTracker()
        var ts = 0L
        for (lift in listOf(0.0f, 0.1f, 0.2f, 0.3f, 0.4f)) {
            t.observe(listOf(player(1, 0.5f, 0.5f, armLift = lift)), ts); ts += 100L
        }
        assertTrue(t.dynamics().poseVariety > 0f)
        t.reset()
        val d = t.dynamics()
        assertEquals(0f, d.poseVariety, 1e-3f)
        assertEquals(0f, d.motionRange, 1e-3f)
    }
}
