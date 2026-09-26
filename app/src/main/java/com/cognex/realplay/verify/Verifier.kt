package com.cognex.realplay.verify

import com.cognex.realplay.challenge.ChallengeSpec
import com.cognex.realplay.challenge.VerificationStep
import com.cognex.realplay.world.WorldState

/**
 * A deterministic verifier for a single [RuleId] (Architecture §4, S4). Pure JVM.
 *
 * [evaluate] judges ONE frame: it returns [VerificationOutcome.Pass] only when this frame's
 * evidence satisfies the rule, [VerificationOutcome.Fail] only when the evidence is confidently
 * false, and [VerificationOutcome.Unsure] for everything uncertain (missing/ambiguous/low-confidence
 * actor, poor frame, insufficient evidence). A single satisfying frame is NOT enough for the step
 * to pass — the [TemporalGate] enforces that (§4.2 rule 5).
 *
 * Outcomes MUST be deterministic for identical inputs.
 */
interface Verifier {
    val rule: RuleId

    fun evaluate(
        step: VerificationStep,
        spec: ChallengeSpec,
        world: WorldState,
        baseline: VerificationBaseline
    ): StepEvaluation
}
