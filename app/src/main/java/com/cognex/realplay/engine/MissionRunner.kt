package com.cognex.realplay.engine

import com.cognex.realplay.challenge.ChallengeSpec
import com.cognex.realplay.challenge.scopedTo
import com.cognex.realplay.verify.Evidence
import com.cognex.realplay.verify.StepEvaluation
import com.cognex.realplay.verify.TemporalGate
import com.cognex.realplay.verify.VerificationBaseline
import com.cognex.realplay.verify.VerificationOutcome
import com.cognex.realplay.verify.VerifierRegistry
import com.cognex.realplay.world.WorldState

/**
 * Runs one [ChallengeSpec] step-by-step against the live world (Architecture §13 S5, §8.1b). Pure
 * JVM — no Android, so it is fully unit-tested.
 *
 * Each step has its own [TemporalGate]; the runner feeds only the CURRENT step's gate, so a later
 * step can never fire before its predecessor (ordering is enforced structurally — honouring
 * `mustFollowPreviousStep`). The runner advances only when the active gate fires. It exposes the
 * current step index and per-step progress for the UI hold-ring.
 *
 * It handles multi-step ordered missions already, even though G1 ships single-step first (§13 S5).
 */
class MissionRunner(
    val spec: ChallengeSpec,
    private val registry: VerifierRegistry = VerifierRegistry.default(),
    private val baselineProvider: (stepIndex: Int) -> VerificationBaseline = { VerificationBaseline.NONE }
) {
    private val gates: List<TemporalGate> = spec.steps.map { TemporalGate(it.holdMs) }
    private val done: BooleanArray = BooleanArray(spec.steps.size)

    /** The step currently being evaluated. Advances only after the previous step's gate fires. */
    var currentStepIndex: Int = 0
        private set

    val stepCount: Int get() = spec.steps.size

    fun completedSteps(): Int = done.count { it }

    val missionComplete: Boolean get() = spec.steps.isEmpty() || done.all { it }

    /** One frame's result (Architecture §13 S5). */
    data class Tick(
        val stepIndex: Int,
        val stepProgress: Float,
        val outcome: VerificationOutcome,
        val stepFired: Boolean,
        val missionComplete: Boolean,
        val completedSteps: Int,
        val coachingHint: String?
    )

    /**
     * Feeds one frame. Evaluates only the active step, records it in that step's gate, and advances
     * when the gate fires. Returns a [Tick] describing this frame.
     */
    fun onFrame(world: WorldState, timestampMs: Long): Tick {
        if (missionComplete) {
            return Tick(
                stepIndex = currentStepIndex.coerceAtMost(stepCount - 1).coerceAtLeast(0),
                stepProgress = 1f,
                outcome = VerificationOutcome.Pass(1f, emptyList()),
                stepFired = false,
                missionComplete = true,
                completedSteps = completedSteps(),
                coachingHint = null
            )
        }

        val idx = currentStepIndex
        val step = spec.steps[idx]
        val eval = registry.evaluate(step, spec.scopedTo(step), world, baselineProvider(idx))
        val gate = gates[idx]
        // Near-miss grace: a confidently-close FAIL counts toward the hold, so "near enough"
        // completes the level and moves on (never an Unsure — missing/uncertain evidence still
        // can't pass, §20 invariant 15).
        val eligible = eval.outcome !is VerificationOutcome.Unsure
        gate.record(TemporalGate.Sample(timestampMs, eval.satisfied || nearEnough(eval), eligible))

        var fired = false
        if (gate.fired()) {
            done[idx] = true
            fired = true
            advance()
        }

        val hint = (eval.outcome as? VerificationOutcome.Unsure)?.coachingHint ?: eval.coachingHint
        return Tick(
            stepIndex = idx,
            stepProgress = gate.progress(),
            outcome = eval.outcome,
            stepFired = fired,
            missionComplete = missionComplete,
            completedSteps = completedSteps(),
            coachingHint = hint
        )
    }

    /** Restarts the mission (e.g. on retry) — clears every gate and completion flag. */
    fun reset() {
        gates.forEach { it.reset() }
        done.fill(false)
        currentStepIndex = 0
    }

    private fun advance() {
        var next = currentStepIndex + 1
        while (next < spec.steps.size && done[next]) next++
        currentStepIndex = next.coerceAtMost(spec.steps.size)
        if (currentStepIndex >= spec.steps.size) currentStepIndex = spec.steps.size - 1
    }

    /**
     * A confidently-close FAIL — every threshold-style evidence item is within [GRACE] of its
     * target. Exact comparisons (colour/shape/presence, "==" / "~=") get no grace. Unsure never
     * reaches here, so uncertain evidence can still never pass.
     */
    private fun nearEnough(eval: StepEvaluation): Boolean {
        if (eval.outcome !is VerificationOutcome.Fail) return false
        val ev = eval.evidence
        return ev.isNotEmpty() && ev.all { it.satisfied || withinGrace(it) }
    }

    private fun withinGrace(e: Evidence): Boolean = when (e.comparator) {
        ">", ">=" -> e.measured >= e.required * (1f - GRACE)
        "<", "<=" -> e.measured <= e.required * (1f + GRACE)
        else -> false
    }

    private companion object {
        /** Demo leniency: how far past a threshold still counts as "near enough" to complete. */
        const val GRACE = 0.25f
    }
}
