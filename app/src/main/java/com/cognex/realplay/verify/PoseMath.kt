package com.cognex.realplay.verify

import com.cognex.realplay.world.Landmark
import com.cognex.realplay.world.NormPoint
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Standard MediaPipe Pose landmark indices used by [PoseMath] (Architecture §S4). Only the joints
 * the feature vector needs are named; others are addressed by raw index.
 */
enum class PoseJoint(val index: Int) {
    NOSE(0),
    LEFT_SHOULDER(11), RIGHT_SHOULDER(12),
    LEFT_ELBOW(13), RIGHT_ELBOW(14),
    LEFT_WRIST(15), RIGHT_WRIST(16),
    LEFT_HIP(23), RIGHT_HIP(24),
    LEFT_KNEE(25), RIGHT_KNEE(26),
    LEFT_ANKLE(27), RIGHT_ANKLE(28)
}

/**
 * A pose reduced to joint ANGLES only (Architecture §S4). Because it is built from angles it is
 * invariant to camera distance and translation — the same pose at 1 m and 2 m yields (nearly) the
 * same vector. Each angle carries the minimum visibility of its three constituent landmarks so
 * [PoseMath.poseDistance] can down-weight or exclude unreliable joints.
 *
 * [angles] and [visibilities] are index-aligned; angle order is fixed by [PoseMath.ANGLE_NAMES].
 */
data class PoseFeatureVector(
    val angles: FloatArray,        // radians
    val visibilities: FloatArray   // 0..1, min of the joint's constituent landmarks
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is PoseFeatureVector) return false
        return angles.contentEquals(other.angles) && visibilities.contentEquals(other.visibilities)
    }

    override fun hashCode(): Int = angles.contentHashCode() * 31 + visibilities.contentHashCode()
}

/**
 * Pure pose geometry (Architecture §S4). No Android; every function is a total function of its
 * inputs. All landmark coordinates are NORMALIZED analysis-image space.
 */
object PoseMath {

    /** Fixed order of the feature-vector angles. */
    val ANGLE_NAMES = listOf(
        "leftElbow", "rightElbow", "leftShoulder", "rightShoulder",
        "leftHip", "rightHip", "leftKnee", "rightKnee", "torsoLean"
    )

    private const val VISIBILITY_FLOOR = 0.5f

    /**
     * Interior angle (radians, 0..π) at vertex [b] formed by the rays b→a and b→c. Degenerate
     * (zero-length) rays yield 0.
     */
    fun jointAngle(a: NormPoint, b: NormPoint, c: NormPoint): Float {
        val v1x = a.x - b.x; val v1y = a.y - b.y
        val v2x = c.x - b.x; val v2y = c.y - b.y
        val n1 = hypot(v1x, v1y); val n2 = hypot(v2x, v2y)
        if (n1 == 0f || n2 == 0f) return 0f
        val dot = (v1x * v2x + v1y * v2y) / (n1 * n2)
        return kotlin.math.acos(dot.coerceIn(-1f, 1f))
    }

    /**
     * Builds the [PoseFeatureVector] from [landmarks] (33-element MediaPipe pose). Missing joints
     * (index out of range) contribute a 0 angle with 0 visibility so they are excluded downstream.
     */
    fun poseFeatureVector(landmarks: List<Landmark>): PoseFeatureVector {
        fun lm(j: PoseJoint): Landmark? = landmarks.getOrNull(j.index)
        fun pt(j: PoseJoint): NormPoint = lm(j)?.point ?: NormPoint(0f, 0f)
        fun vis(vararg js: PoseJoint): Float =
            js.minOf { lm(it)?.visibility ?: 0f }

        val angles = FloatArray(ANGLE_NAMES.size)
        val vis = FloatArray(ANGLE_NAMES.size)

        angles[0] = jointAngle(pt(PoseJoint.LEFT_SHOULDER), pt(PoseJoint.LEFT_ELBOW), pt(PoseJoint.LEFT_WRIST))
        vis[0] = vis(PoseJoint.LEFT_SHOULDER, PoseJoint.LEFT_ELBOW, PoseJoint.LEFT_WRIST)

        angles[1] = jointAngle(pt(PoseJoint.RIGHT_SHOULDER), pt(PoseJoint.RIGHT_ELBOW), pt(PoseJoint.RIGHT_WRIST))
        vis[1] = vis(PoseJoint.RIGHT_SHOULDER, PoseJoint.RIGHT_ELBOW, PoseJoint.RIGHT_WRIST)

        angles[2] = jointAngle(pt(PoseJoint.LEFT_ELBOW), pt(PoseJoint.LEFT_SHOULDER), pt(PoseJoint.LEFT_HIP))
        vis[2] = vis(PoseJoint.LEFT_ELBOW, PoseJoint.LEFT_SHOULDER, PoseJoint.LEFT_HIP)

        angles[3] = jointAngle(pt(PoseJoint.RIGHT_ELBOW), pt(PoseJoint.RIGHT_SHOULDER), pt(PoseJoint.RIGHT_HIP))
        vis[3] = vis(PoseJoint.RIGHT_ELBOW, PoseJoint.RIGHT_SHOULDER, PoseJoint.RIGHT_HIP)

        angles[4] = jointAngle(pt(PoseJoint.LEFT_SHOULDER), pt(PoseJoint.LEFT_HIP), pt(PoseJoint.LEFT_KNEE))
        vis[4] = vis(PoseJoint.LEFT_SHOULDER, PoseJoint.LEFT_HIP, PoseJoint.LEFT_KNEE)

        angles[5] = jointAngle(pt(PoseJoint.RIGHT_SHOULDER), pt(PoseJoint.RIGHT_HIP), pt(PoseJoint.RIGHT_KNEE))
        vis[5] = vis(PoseJoint.RIGHT_SHOULDER, PoseJoint.RIGHT_HIP, PoseJoint.RIGHT_KNEE)

        angles[6] = jointAngle(pt(PoseJoint.LEFT_HIP), pt(PoseJoint.LEFT_KNEE), pt(PoseJoint.LEFT_ANKLE))
        vis[6] = vis(PoseJoint.LEFT_HIP, PoseJoint.LEFT_KNEE, PoseJoint.LEFT_ANKLE)

        angles[7] = jointAngle(pt(PoseJoint.RIGHT_HIP), pt(PoseJoint.RIGHT_KNEE), pt(PoseJoint.RIGHT_ANKLE))
        vis[7] = vis(PoseJoint.RIGHT_HIP, PoseJoint.RIGHT_KNEE, PoseJoint.RIGHT_ANKLE)

        // Torso lean: angle of the shoulder-midpoint → hip-midpoint vector from vertical.
        val shoulderMid = midpoint(pt(PoseJoint.LEFT_SHOULDER), pt(PoseJoint.RIGHT_SHOULDER))
        val hipMid = midpoint(pt(PoseJoint.LEFT_HIP), pt(PoseJoint.RIGHT_HIP))
        angles[8] = abs(atan2(hipMid.x - shoulderMid.x, hipMid.y - shoulderMid.y))
        vis[8] = vis(PoseJoint.LEFT_SHOULDER, PoseJoint.RIGHT_SHOULDER, PoseJoint.LEFT_HIP, PoseJoint.RIGHT_HIP)

        return PoseFeatureVector(angles, vis)
    }

    /**
     * Weighted angular distance between two poses (radians). Each angle is weighted by the minimum
     * of the two vectors' visibilities; joints whose visibility is below [VISIBILITY_FLOOR] in
     * either vector are EXCLUDED and the remaining weights are renormalised. Returns
     * [Float.MAX_VALUE] when no joint is reliable enough to compare (→ Unsure upstream).
     *
     * Because it operates purely on angles it is invariant to camera distance and translation.
     */
    fun poseDistance(a: PoseFeatureVector, b: PoseFeatureVector): Float {
        val n = minOf(a.angles.size, b.angles.size)
        var weightSum = 0f
        var acc = 0f
        for (i in 0 until n) {
            val vA = a.visibilities[i]
            val vB = b.visibilities[i]
            if (vA < VISIBILITY_FLOOR || vB < VISIBILITY_FLOOR) continue
            val w = minOf(vA, vB)
            val d = abs(a.angles[i] - b.angles[i])
            acc += w * d * d
            weightSum += w
        }
        if (weightSum <= 0f) return Float.MAX_VALUE
        return sqrt(acc / weightSum)
    }

    /**
     * A coarse cluster id for a pose (Architecture §6.2 — S3.5's `poseVariety` counts distinct
     * clusters). Only joints above [VISIBILITY_FLOOR] contribute; each reliable angle is quantised
     * into [BUCKETS] buckets over 0..π and hashed. Two visually similar poses map to the same id.
     */
    fun poseClusterId(vector: PoseFeatureVector): Int {
        var h = 1
        for (i in vector.angles.indices) {
            val bucket = if (vector.visibilities[i] < VISIBILITY_FLOOR) -1
                else (vector.angles[i] / Math.PI.toFloat() * BUCKETS).roundToInt().coerceIn(0, BUCKETS)
            h = h * 31 + bucket
        }
        return h
    }

    /**
     * Visibility-weighted EMA of landmark speed between two frames (Architecture §S4). [dtMs] is the
     * inter-frame time; [prevEnergy] is the previous EMA value. Landmarks with low visibility in
     * either frame contribute little. Returns the new energy.
     */
    fun motionEnergy(
        previous: List<Landmark>,
        current: List<Landmark>,
        dtMs: Long,
        prevEnergy: Float,
        alpha: Float = 0.5f
    ): Float {
        val dt = (dtMs.coerceAtLeast(1L)) / 1000f
        val n = minOf(previous.size, current.size)
        var weightSum = 0f
        var speedAcc = 0f
        for (i in 0 until n) {
            val w = minOf(previous[i].visibility, current[i].visibility)
            if (w <= 0f) continue
            val d = hypot(current[i].point.x - previous[i].point.x, current[i].point.y - previous[i].point.y)
            speedAcc += w * (d / dt)
            weightSum += w
        }
        val instant = if (weightSum <= 0f) 0f else speedAcc / weightSum
        return alpha * instant + (1f - alpha) * prevEnergy
    }

    private fun midpoint(a: NormPoint, b: NormPoint) = NormPoint((a.x + b.x) / 2f, (a.y + b.y) / 2f)

    const val BUCKETS = 6
}
