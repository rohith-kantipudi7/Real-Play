package com.cognex.realplay.verify

/**
 * The CLOSED rule set — the entire anti-hallucination story (Architecture §4.1). Pure JVM.
 *
 * These are the ONLY verification primitives that exist. The LLM may emit only these enum values;
 * the validator rejects anything else. Adding a rule here without a corresponding [Verifier]
 * makes [VerifierRegistry] throw at construction — the registry is never allowed to silently skip.
 *
 * The base set is EXACTLY §4.1; the multi-object geometry primitives [COLLINEAR], [SIZE_ORDER] and
 * [GROUP_CLUSTERED] extend it for the demo game library (line-up, sort-by-size, group-by-colour/
 * kind). Each is deterministic, camera-space, and — like every other rule — backed by a registered
 * [Verifier], so the anti-hallucination guarantee (no rule ever passes unverified) still holds.
 */
enum class RuleId {
    // Distance
    DISTANCE_LESS_THAN,
    DISTANCE_GREATER_THAN,

    // Direction
    LEFT_OF,
    RIGHT_OF,
    ABOVE,
    BELOW,

    // Zones / overlap
    POINT_IN_ZONE,
    OVERLAP_RATIO_ABOVE,
    OBJECT_VANISHED_IN_ZONE,

    // Presence / attributes
    OBJECT_PRESENT,
    OBJECT_ABSENT,
    COLOR_MATCH,
    SHAPE_MATCH,
    COUNT_EQUALS,

    // Multi-object geometry
    NON_DEGENERATE_TRIANGLE,
    ARRANGEMENT_MATCH,
    COLLINEAR,
    SIZE_ORDER,
    GROUP_CLUSTERED,

    // Pose
    POSE_MATCH,
    JOINT_ANGLE_WITHIN,
    LIMB_RAISED,

    // Motion
    MOTION_BELOW,
    MOTION_ABOVE,

    // Player ↔ object / zone
    PLAYER_NEAR_OBJECT,
    PLAYER_HOLDS_OBJECT,
    PLAYER_IN_ZONE
}
