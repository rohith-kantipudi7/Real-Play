package com.cognex.realplay.perception

import com.cognex.realplay.util.RpLog
import com.cognex.realplay.verify.RuleId

/** The perception subsystems the scheduler can enable per frame (Architecture §3.4, §12). */
enum class Perceiver { OBJECT, POSE }

/**
 * Decides which perceivers to run for the ACTIVE challenge (Architecture §12, S8 prompt step 2).
 * Only the object detector and/or pose detector the current spec actually references are enabled —
 * both only when the challenge needs both. Keeping pose off on an object-only challenge protects
 * the §3.3 threading budget and honours §20 invariant 12 (the object-only path runs with pose fully
 * disabled).
 *
 * The classification is pure ([perceiversFor]); [update] adds the stateful "log when the set
 * changes" behaviour used at runtime.
 */
class PerceptionScheduler(initial: Set<Perceiver> = setOf(Perceiver.OBJECT)) {

    /** The perceiver set active as of the last [update]. */
    var current: Set<Perceiver> = initial
        private set

    /**
     * Recomputes the active perceiver set from the [rules] the current spec references. Logs the
     * active set whenever it changes. Returns the (possibly unchanged) set.
     */
    fun update(rules: Collection<RuleId>): Set<Perceiver> {
        val next = perceiversFor(rules)
        if (next != current) {
            current = next
            RpLog.i(RpLog.Tag.PERCEPTION, "Active perceivers: ${next.sorted().joinToString(",") { it.name }}")
        }
        return current
    }

    companion object {
        /**
         * Rules whose evidence comes ONLY from the pose detector (no object needed). The
         * player↔object rules deliberately fall through to also require [Perceiver.OBJECT].
         */
        private val PURE_POSE_RULES = setOf(
            RuleId.POSE_MATCH, RuleId.JOINT_ANGLE_WITHIN, RuleId.LIMB_RAISED,
            RuleId.MOTION_BELOW, RuleId.MOTION_ABOVE
        )

        /** Rules that need the pose detector (pure-pose plus the player↔object/zone rules). */
        private val POSE_RULES = PURE_POSE_RULES + setOf(
            RuleId.PLAYER_NEAR_OBJECT, RuleId.PLAYER_HOLDS_OBJECT, RuleId.PLAYER_IN_ZONE
        )

        /**
         * The minimal set of perceivers needed to evaluate [rules]. A rule that needs a player uses
         * [Perceiver.POSE]; any rule that is not pure-pose also needs [Perceiver.OBJECT] (object,
         * zone or arrangement evidence). An empty rule set defaults to [Perceiver.OBJECT] so the
         * pipeline always has something running.
         */
        fun perceiversFor(rules: Collection<RuleId>): Set<Perceiver> {
            if (rules.isEmpty()) return setOf(Perceiver.OBJECT)
            val set = LinkedHashSet<Perceiver>()
            if (rules.any { it in POSE_RULES }) set.add(Perceiver.POSE)
            if (rules.any { it !in PURE_POSE_RULES }) set.add(Perceiver.OBJECT)
            if (set.isEmpty()) set.add(Perceiver.OBJECT)
            return set
        }
    }
}
