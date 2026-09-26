package com.cognex.realplay.challenge

import com.cognex.realplay.world.Affordance
import com.cognex.realplay.world.SceneCapability
import com.cognex.realplay.world.WorldState

/**
 * A challenge generator (Architecture §6.3). Pure JVM. One generator per [ChallengeType].
 *
 * The registry scores every generator, picks a winner per [SelectionMode], then calls [generate]
 * with the resolved [stepBudget]. A generator NEVER produces a spec referencing actors or
 * capabilities absent from the world (§20 invariant 4) and NEVER exceeds [stepBudget] steps
 * (§20 invariant 17).
 */
interface ChallengeGenerator {

    val type: ChallengeType

    /** Stable identity for the deterministic tie-break in RECOMMENDED (§20 invariant 11). */
    val id: String

    /** Eligible in RECOMMENDED mode. Experimental generators set this false. */
    val proven: Boolean

    /** Scene pre-conditions (§6.3). G0 keeps this [Requirement.NONE]. */
    val requires: Requirement

    /**
     * How well this generator fits the scene, 0.0 = impossible. Computed AFTER the requirement
     * pre-filter passes, so it can assume its minimums are met (§6.1).
     */
    fun feasibility(cap: SceneCapability, ctx: GenerationContext): Float

    /**
     * The STRUCTURAL difficulty axis (§8.1b). How many chained steps of this generator's own
     * primitive may be emitted at [tier]. Default 1 at every tier; a generator opts into chaining
     * by returning 2 at HARD. Never exceeds 3.
     */
    fun maxStepsForTier(tier: Tier): Int = 1

    /** Age-band gate multiplier (§10): 1.0 when this game is offered to [band], else 0.0. */
    fun ageGate(band: AgeBand): Float = 1f

    /**
     * Builds a fully-bound, verifiable spec against the live world, honouring [stepBudget].
     * [aff] is the per-object affordance list for the same frame.
     */
    fun generate(
        world: WorldState,
        aff: List<Affordance>,
        ctx: GenerationContext,
        stepBudget: Int
    ): ChallengeSpec
}
