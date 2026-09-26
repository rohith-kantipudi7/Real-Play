package com.cognex.realplay.verify

/**
 * The eight target poses for G4 · Statue Match (Architecture §7, S8 prompt step 6). Pure JVM.
 *
 * Each pose is a [PoseFeatureVector] of the nine [PoseMath.ANGLE_NAMES] joint angles (radians),
 * with full visibility. Because [PoseMath.poseDistance] compares ANGLES only, these targets are
 * invariant to the player's distance and position — the same shape matches at 1.5 m and 3 m
 * (§22 acceptance: "POSE_MATCH works at 1.5 m AND 3 m").
 *
 * The angle order is fixed by [PoseMath.ANGLE_NAMES]:
 *   leftElbow, rightElbow, leftShoulder, rightShoulder, leftHip, rightHip, leftKnee, rightKnee, torsoLean
 */
object PoseLibrary {

    /** A named target pose. [id] is stable and is what a G4 spec stores in its `poseId` step param. */
    data class TargetPose(val id: Int, val name: String, val vector: PoseFeatureVector)

    /** The eight shipped poses, in id order. */
    val poses: List<TargetPose> = listOf(
        pose(0, "T-pose", elbows = 180 to 180, shoulders = 90 to 90),
        pose(1, "Both hands up", elbows = 180 to 180, shoulders = 170 to 170),
        pose(2, "One hand up", elbows = 180 to 180, shoulders = 170 to 20),
        pose(3, "Star jump", elbows = 180 to 180, shoulders = 135 to 135, hips = 160 to 160),
        pose(4, "Hands on hips", elbows = 90 to 90, shoulders = 40 to 40),
        pose(5, "Arms crossed", elbows = 45 to 45, shoulders = 25 to 25),
        pose(6, "One leg up", shoulders = 20 to 20, hips = 90 to 180, knees = 90 to 180),
        pose(7, "Reaching left", elbows = 180 to 180, shoulders = 100 to 70, hips = 170 to 170, torsoLeanDeg = 23)
    )

    /** The pose with the given [id]; falls back to the first pose for an unknown id. */
    fun byId(id: Int): TargetPose = poses.firstOrNull { it.id == id } ?: poses.first()

    /** A [VerificationBaseline] carrying [byId]'s vector as the POSE_MATCH reference. */
    fun baselineFor(poseId: Int): VerificationBaseline =
        VerificationBaseline(referencePose = byId(poseId).vector)

    /**
     * Builds a [TargetPose] from anatomical joint angles in DEGREES. Unspecified joints default to a
     * relaxed standing posture (straight arms at the sides, straight legs, upright torso). Each pair
     * is (left, right).
     */
    private fun pose(
        id: Int,
        name: String,
        elbows: Pair<Int, Int> = 180 to 180,
        shoulders: Pair<Int, Int> = 15 to 15,
        hips: Pair<Int, Int> = 180 to 180,
        knees: Pair<Int, Int> = 180 to 180,
        torsoLeanDeg: Int = 0
    ): TargetPose {
        val angles = floatArrayOf(
            rad(elbows.first), rad(elbows.second),
            rad(shoulders.first), rad(shoulders.second),
            rad(hips.first), rad(hips.second),
            rad(knees.first), rad(knees.second),
            rad(torsoLeanDeg)
        )
        val visibilities = FloatArray(PoseMath.ANGLE_NAMES.size) { 1f }
        return TargetPose(id, name, PoseFeatureVector(angles, visibilities))
    }

    private fun rad(degrees: Int): Float = Math.toRadians(degrees.toDouble()).toFloat()
}
