package com.cognex.realplay.verify

import com.cognex.realplay.world.NormPoint

/**
 * A reference object position for [RuleId.ARRANGEMENT_MATCH] (Architecture §S4). [key] is the
 * matching key (label or "track:<id>"); [center] is its NORMALIZED centroid in the reference
 * arrangement.
 */
data class ReferenceObject(val key: String, val center: NormPoint)

/**
 * Extra facts a verifier needs that the current frame alone cannot supply (Architecture §S4).
 * Most verifiers ignore it; only [RuleId.ARRANGEMENT_MATCH], [RuleId.POSE_MATCH] and
 * [RuleId.OBJECT_VANISHED_IN_ZONE] consume it. Deterministic given its inputs.
 *
 *  - [referenceObjects] — the captured target arrangement for ARRANGEMENT_MATCH.
 *  - [referencePose]    — the captured target pose for POSE_MATCH.
 *  - [vanished]         — last-known state of a target object for OBJECT_VANISHED_IN_ZONE.
 */
data class VerificationBaseline(
    val referenceObjects: List<ReferenceObject> = emptyList(),
    val referencePose: PoseFeatureVector? = null,
    val vanished: VanishedInfo? = null
) {
    companion object {
        val NONE = VerificationBaseline()
    }
}

/**
 * Last-known state of an object that is no longer detected (Architecture §4.1
 * OBJECT_VANISHED_IN_ZONE). [exitedAtEdge] is true when the object was last seen touching a frame
 * edge — in that case the rule must return Unsure (it may simply have left the frame), never Fail.
 */
data class VanishedInfo(
    val lastCenter: NormPoint,
    val framesUndetected: Int,
    val exitedAtEdge: Boolean
)
