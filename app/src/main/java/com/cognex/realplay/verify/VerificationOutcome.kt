package com.cognex.realplay.verify

/**
 * The result of verifying one step against one frame (Architecture §4). Pure JVM.
 *
 * ⚠ [VerificationOutcome] is a SEALED INTERFACE, not an enum. Each variant carries its own
 * payload. [Unsure] is STRUCTURALLY distinct from [Fail] and can never be converted to [Pass] by
 * any downstream engine logic (§20 invariant 15). There is deliberately no public API that widens
 * an [Unsure] into a [Pass].
 *
 * `Unsure` is the most important type in the codebase: low confidence, missing actor, poor frame,
 * ambiguous identity, or insufficient evidence all become `Unsure` — coach, never pass, never
 * punish.
 */
sealed interface VerificationOutcome {
    /** Produced ONLY by deterministic verification after all required evidence is satisfied. */
    data class Pass(val confidence: Float, val evidence: List<Evidence>) : VerificationOutcome

    /** Produced ONLY when required evidence is confidently false. */
    data class Fail(val reason: String, val evidence: List<Evidence>) : VerificationOutcome

    /** Everything uncertain. Carries a coaching hint; cannot become [Pass]. */
    data class Unsure(val coachingHint: String) : VerificationOutcome
}

/**
 * One frame's evaluation of a step (Architecture §4). [evidence] items each carry their
 * [MeasurementDomain]. [coachingHint] mirrors an [VerificationOutcome.Unsure] hint when present.
 */
data class StepEvaluation(
    val outcome: VerificationOutcome,
    val confidence: Float,
    val evidence: List<Evidence>,
    val coachingHint: String? = null
) {
    /** True when THIS frame's evidence satisfies the rule (feeds the [TemporalGate]). */
    val satisfied: Boolean get() = outcome is VerificationOutcome.Pass
}

/**
 * A single measured fact backing an outcome (Architecture §4, §12). Always records its
 * [domain] so the UI can word it honestly ("0.18 — needed under 0.30" while NORMALIZED).
 */
data class Evidence(
    val label: String,
    val measured: Float,
    val required: Float,
    val comparator: String,
    val satisfied: Boolean,
    val domain: MeasurementDomain
)
