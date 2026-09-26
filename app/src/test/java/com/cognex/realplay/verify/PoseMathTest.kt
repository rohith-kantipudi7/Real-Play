package com.cognex.realplay.verify

import com.cognex.realplay.world.Landmark
import com.cognex.realplay.world.NormPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pure pose-geometry tests (Architecture §S4). The key property: invariance to camera distance. */
class PoseMathTest {

    private fun lm(x: Float, y: Float, v: Float = 1f) = Landmark(NormPoint(x, y), v)

    private fun samplePose(scale: Float, tx: Float, ty: Float): List<Landmark> {
        // A fixed pose, then uniformly scaled (camera distance) and translated (camera pan).
        val base = listOf(
            11 to Pair(0.4f, 0.3f), 12 to Pair(0.6f, 0.3f),   // shoulders
            13 to Pair(0.35f, 0.45f), 14 to Pair(0.65f, 0.45f), // elbows
            15 to Pair(0.35f, 0.60f), 16 to Pair(0.65f, 0.60f), // wrists
            23 to Pair(0.45f, 0.6f), 24 to Pair(0.55f, 0.6f),  // hips
            25 to Pair(0.45f, 0.8f), 26 to Pair(0.55f, 0.8f),  // knees
            27 to Pair(0.45f, 0.95f), 28 to Pair(0.55f, 0.95f) // ankles
        )
        val out = MutableList(33) { lm(0f, 0f, 0f) }
        for ((i, p) in base) out[i] = lm(p.first * scale + tx, p.second * scale + ty)
        return out
    }

    @Test
    fun poseDistance_invariantToCameraDistanceAndTranslation() {
        val near = PoseMath.poseFeatureVector(samplePose(scale = 1.0f, tx = 0f, ty = 0f))
        val far = PoseMath.poseFeatureVector(samplePose(scale = 0.5f, tx = 0.2f, ty = 0.2f))
        // Same pose at "1 m" and "2 m" (half the size, shifted) → near-zero angular distance.
        assertEquals(0f, PoseMath.poseDistance(near, far), 1e-3f)
    }

    @Test
    fun poseDistance_noReliableJoints_isMaxValue() {
        val a = PoseMath.poseFeatureVector(List(33) { lm(0.5f, 0.5f, 0.1f) })   // all invisible
        val b = PoseMath.poseFeatureVector(samplePose(1f, 0f, 0f))
        assertEquals(Float.MAX_VALUE, PoseMath.poseDistance(a, b), 0f)
    }

    @Test
    fun poseClusterId_samePose_sameId() {
        val a = PoseMath.poseFeatureVector(samplePose(1f, 0f, 0f))
        val b = PoseMath.poseFeatureVector(samplePose(0.6f, 0.1f, 0.1f))
        assertEquals(PoseMath.poseClusterId(a), PoseMath.poseClusterId(b))
    }

    @Test
    fun jointAngle_rightAngle() {
        val angle = PoseMath.jointAngle(NormPoint(0f, 1f), NormPoint(0f, 0f), NormPoint(1f, 0f))
        assertEquals((Math.PI / 2).toFloat(), angle, 1e-4f)
    }

    @Test
    fun motionEnergy_stillBodyIsLow_movingBodyIsHigh() {
        val a = List(33) { lm(0.5f, 0.5f) }
        val still = PoseMath.motionEnergy(a, a, dtMs = 33, prevEnergy = 0f)
        assertTrue(still < 1e-4f)
        val moved = List(33) { lm(0.7f, 0.7f) }
        val moving = PoseMath.motionEnergy(a, moved, dtMs = 33, prevEnergy = 0f)
        assertTrue(moving > still)
    }
}
