package com.cognex.realplay.verify

import com.cognex.realplay.challenge.ActorRef
import com.cognex.realplay.challenge.ChallengeSpec
import com.cognex.realplay.challenge.VerificationStep
import com.cognex.realplay.world.NormPoint

/** Positional actor accessor — steps consume [ChallengeSpec.actors] by role/index (§S4). */
internal fun ChallengeSpec.actorAt(index: Int): ActorRef? = actors.getOrNull(index)

/** Numeric parameter accessor with a default (params are float-only per §4). */
internal fun VerificationStep.p(key: String, default: Float = 0f): Float = params[key] ?: default

/** All verify evidence is NORMALIZED while a planar surface isn't calibrated (§12, §7.1). */
internal fun evidence(
    label: String,
    measured: Float,
    required: Float,
    comparator: String,
    satisfied: Boolean
): Evidence = Evidence(label, measured, required, comparator, satisfied, MeasurementDomain.NORMALIZED)

internal fun NormPoint.distanceTo(other: NormPoint): Float {
    val dx = x - other.x
    val dy = y - other.y
    return kotlin.math.sqrt(dx * dx + dy * dy)
}
