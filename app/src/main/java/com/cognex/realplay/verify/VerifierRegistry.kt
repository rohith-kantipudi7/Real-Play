package com.cognex.realplay.verify

import com.cognex.realplay.verify.verifiers.AboveVerifier
import com.cognex.realplay.verify.verifiers.ArrangementMatchVerifier
import com.cognex.realplay.verify.verifiers.BelowVerifier
import com.cognex.realplay.verify.verifiers.ColorMatchVerifier
import com.cognex.realplay.verify.verifiers.CollinearVerifier
import com.cognex.realplay.verify.verifiers.CountEqualsVerifier
import com.cognex.realplay.verify.verifiers.DistanceGreaterThanVerifier
import com.cognex.realplay.verify.verifiers.DistanceLessThanVerifier
import com.cognex.realplay.verify.verifiers.GroupClusteredVerifier
import com.cognex.realplay.verify.verifiers.JointAngleWithinVerifier
import com.cognex.realplay.verify.verifiers.LeftOfVerifier
import com.cognex.realplay.verify.verifiers.LimbRaisedVerifier
import com.cognex.realplay.verify.verifiers.MotionAboveVerifier
import com.cognex.realplay.verify.verifiers.MotionBelowVerifier
import com.cognex.realplay.verify.verifiers.NonDegenerateTriangleVerifier
import com.cognex.realplay.verify.verifiers.ObjectAbsentVerifier
import com.cognex.realplay.verify.verifiers.ObjectPresentVerifier
import com.cognex.realplay.verify.verifiers.ObjectVanishedInZoneVerifier
import com.cognex.realplay.verify.verifiers.OverlapRatioAboveVerifier
import com.cognex.realplay.verify.verifiers.PlayerHoldsObjectVerifier
import com.cognex.realplay.verify.verifiers.PlayerInZoneVerifier
import com.cognex.realplay.verify.verifiers.PlayerNearObjectVerifier
import com.cognex.realplay.verify.verifiers.PointInZoneVerifier
import com.cognex.realplay.verify.verifiers.PoseMatchVerifier
import com.cognex.realplay.verify.verifiers.RightOfVerifier
import com.cognex.realplay.verify.verifiers.ShapeMatchVerifier
import com.cognex.realplay.verify.verifiers.SizeOrderVerifier

/**
 * Maps every [RuleId] to its [Verifier] (Architecture §4.1, S4). Pure JVM.
 *
 * The registry is the enforcement point for the CLOSED rule set: it verifies at CONSTRUCTION that
 * EVERY [RuleId] has exactly one verifier and that no verifier claims a rule twice. A rule added to
 * [RuleId] without a matching verifier makes construction throw — the registry is never allowed to
 * silently skip a rule (which could otherwise let a step pass unverified).
 */
class VerifierRegistry private constructor(private val verifiers: Map<RuleId, Verifier>) {

    /** Returns the verifier for [rule]. Always present — construction guarantees totality. */
    fun verifierFor(rule: RuleId): Verifier =
        verifiers.getValue(rule)

    /** Evaluates one step against one frame using the correct verifier for its rule. */
    fun evaluate(
        step: com.cognex.realplay.challenge.VerificationStep,
        spec: com.cognex.realplay.challenge.ChallengeSpec,
        world: com.cognex.realplay.world.WorldState,
        baseline: VerificationBaseline = VerificationBaseline.NONE
    ): StepEvaluation = verifierFor(step.rule).evaluate(step, spec, world, baseline)

    companion object {
        /** The canonical registry with every shipped verifier. */
        fun default(): VerifierRegistry = of(
            DistanceLessThanVerifier(),
            DistanceGreaterThanVerifier(),
            LeftOfVerifier(),
            RightOfVerifier(),
            AboveVerifier(),
            BelowVerifier(),
            PointInZoneVerifier(),
            OverlapRatioAboveVerifier(),
            ObjectVanishedInZoneVerifier(),
            ObjectPresentVerifier(),
            ObjectAbsentVerifier(),
            ColorMatchVerifier(),
            ShapeMatchVerifier(),
            CountEqualsVerifier(),
            NonDegenerateTriangleVerifier(),
            ArrangementMatchVerifier(),
            CollinearVerifier(),
            SizeOrderVerifier(),
            GroupClusteredVerifier(),
            PoseMatchVerifier(),
            JointAngleWithinVerifier(),
            LimbRaisedVerifier(),
            MotionBelowVerifier(),
            MotionAboveVerifier(),
            PlayerNearObjectVerifier(),
            PlayerHoldsObjectVerifier(),
            PlayerInZoneVerifier()
        )

        /**
         * Builds a registry from [verifiers], throwing if any rule is duplicated or if any [RuleId]
         * is left without a verifier.
         */
        fun of(vararg verifiers: Verifier): VerifierRegistry {
            val map = LinkedHashMap<RuleId, Verifier>()
            for (v in verifiers) {
                val existing = map.put(v.rule, v)
                check(existing == null) { "Duplicate verifier for ${v.rule}" }
            }
            val missing = RuleId.entries.filter { it !in map }
            check(missing.isEmpty()) { "No verifier registered for: $missing" }
            return VerifierRegistry(map)
        }
    }
}
