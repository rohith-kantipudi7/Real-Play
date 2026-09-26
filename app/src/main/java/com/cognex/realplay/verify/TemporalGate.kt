package com.cognex.realplay.verify

/**
 * Enforces the seven §4.2 temporal rules over a sliding window (Architecture §4.2, S4). Pure JVM.
 *
 * A per-frame [StepEvaluation] is reduced to a [Sample] and fed in via [record]. The gate fires a
 * PASS only when ALL of these hold:
 *   1. FrameQuality was GOOD and required actors present/unambiguous → the sample is *eligible*
 *      (the verifier encodes this by returning something other than [VerificationOutcome.Pass]
 *      with an eligible=false marker; here we take eligibility explicitly).
 *   3. ≥ [SATISFIED_RATIO] of eligible frames in the window satisfy the rule.
 *   4. The window spans at least [VerificationStep.holdMs] of real time.
 *   5. A single positive frame can NEVER pass — at least [MIN_ELIGIBLE] eligible samples are
 *      required, so one true frame in a stream of false frames is impossible to pass.
 *   6. Missing/ambiguous evidence is ineligible, not a failure.
 *
 * [progress] exposes a 0..1 value for the UI hold-ring.
 */
class TemporalGate(private val holdMs: Long) {

    /** One frame's contribution to the gate. */
    data class Sample(val timestampMs: Long, val satisfied: Boolean, val eligible: Boolean)

    private val samples = ArrayDeque<Sample>()

    /** Records one frame. Samples older than the window (relative to the newest) are evicted. */
    fun record(sample: Sample) {
        samples.addLast(sample)
        val newest = sample.timestampMs
        // Keep a little more than holdMs so ratio is measured over the full required window.
        val keepFrom = newest - windowMs()
        while (samples.size > 1 && samples.first().timestampMs < keepFrom) {
            samples.removeFirst()
        }
    }

    /** Convenience: record from a [StepEvaluation]. Eligible = not Unsure this frame. */
    fun record(evaluation: StepEvaluation, timestampMs: Long) {
        val eligible = evaluation.outcome !is VerificationOutcome.Unsure
        record(Sample(timestampMs, evaluation.satisfied, eligible))
    }

    /** True when the §4.2 window conditions are met and the step should PASS. */
    fun fired(): Boolean {
        val eligible = samples.filter { it.eligible }
        if (eligible.size < MIN_ELIGIBLE) return false            // rule 5: never single-frame
        val latest = samples.last()
        if (!latest.eligible || !latest.satisfied) return false   // rule: latest must satisfy
        val span = samples.last().timestampMs - samples.first().timestampMs
        if (span < holdMs) return false                           // rule 4: hold duration
        return satisfiedRatio(eligible) >= SATISFIED_RATIO        // rule 3: ≥80%
    }

    /** 0..1 progress toward firing, for the UI hold-ring. 0 when the latest frame isn't satisfying. */
    fun progress(): Float {
        val latest = samples.lastOrNull() ?: return 0f
        if (!latest.eligible || !latest.satisfied) return 0f
        val eligible = samples.filter { it.eligible }
        if (eligible.size < MIN_ELIGIBLE) {
            // One eligible frame is at most halfway to the 2-frame minimum.
            return 0f
        }
        val span = samples.last().timestampMs - samples.first().timestampMs
        val fill = if (holdMs <= 0L) 1f else clamp01(span.toFloat() / holdMs)
        val ratio = clamp01(satisfiedRatio(eligible) / SATISFIED_RATIO)
        return minOf(fill, ratio)
    }

    /** Clears the window (e.g. when a step (re)starts). */
    fun reset() = samples.clear()

    private fun satisfiedRatio(eligible: List<Sample>): Float {
        if (eligible.isEmpty()) return 0f
        return eligible.count { it.satisfied }.toFloat() / eligible.size
    }

    /** The buffer retention window: at least the hold, plus a floor so short holds keep 2+ frames. */
    private fun windowMs(): Long = maxOf(holdMs, MIN_WINDOW_MS)

    companion object {
        const val SATISFIED_RATIO = 0.8f
        const val MIN_ELIGIBLE = 2
        const val MIN_WINDOW_MS = 100L

        private fun clamp01(v: Float): Float = if (v < 0f) 0f else if (v > 1f) 1f else v
    }
}
