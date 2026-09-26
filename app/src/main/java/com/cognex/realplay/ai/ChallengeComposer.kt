package com.cognex.realplay.ai

import com.cognex.realplay.challenge.ChallengeRegistry
import com.cognex.realplay.challenge.GenerationContext
import com.cognex.realplay.challenge.SelectionMode
import com.cognex.realplay.challenge.SelectionResult
import com.cognex.realplay.engine.AppSettings
import com.cognex.realplay.util.RpLog
import com.cognex.realplay.world.SceneCapability
import com.cognex.realplay.world.WorldState

/**
 * The composition line (Architecture §3.1, §6.5). Sits between the deterministic registry and the
 * game engine.
 *
 * Given the deterministic [SelectionResult] (always valid), it optionally asks the [LanguageModel]
 * to arrange the *same* feasible skills into a possibly-different mission and reword it. Every
 * proposal is gated by [SchemaValidator]; the chosen skill is re-bound by the deterministic
 * generator (via a PINNED re-selection) so the verifiable steps/params/actors are ALWAYS produced
 * by the registry, never by the model. Any failure — disabled, unavailable, timeout, invalid,
 * unsafe — returns the deterministic result unchanged (§20 invariants 18–20).
 *
 * The model output only ever changes WHICH feasible game is played and HOW it is worded. It can
 * never change how the game is judged.
 */
class ChallengeComposer(
    private val registry: ChallengeRegistry,
    private val model: LanguageModel = MockModel(),
    private val enabled: () -> Boolean = { AppSettings.aiComposerEnabled.value }
) {

    /** True when a real proposal could be produced (gate + availability). Cheap, non-suspending. */
    fun active(): Boolean = enabled() && model.isAvailable

    /**
     * Returns a composed [SelectionResult], or [deterministic] unchanged on any failure. Suspends on
     * the model's own dispatcher (inside [LanguageModel.propose]); the caller must run this off the
     * analyzer thread and never block perception on it (§3.3).
     */
    suspend fun compose(
        world: WorldState,
        cap: SceneCapability,
        ctx: GenerationContext,
        deterministic: SelectionResult
    ): SelectionResult {
        if (!active()) return deterministic

        val bundle = PromptBuilder.build(deterministic.ranked, world, cap, ctx)
        val feasibleIds = bundle.skills.mapTo(HashSet()) { it.generatorId }
        if (feasibleIds.isEmpty()) return deterministic

        val raw = model.propose(bundle.prompt)
        return when (val r = SchemaValidator.validate(raw, feasibleIds)) {
            is SchemaValidator.Result.Rejected -> {
                RpLog.i(RpLog.Tag.AI, "proposal rejected (${r.reason}) — using deterministic ${deterministic.winnerId}")
                deterministic
            }
            is SchemaValidator.Result.Accepted -> apply(world, cap, ctx, deterministic, r.proposal)
        }
    }

    private fun apply(
        world: WorldState,
        cap: SceneCapability,
        ctx: GenerationContext,
        deterministic: SelectionResult,
        proposal: ComposerProposal
    ): SelectionResult {
        // Re-bind the chosen skill through the deterministic generator so the spec's steps, params
        // and actors are registry-produced. If the model picked the same skill, reuse the spec.
        val base = if (proposal.generatorId == deterministic.winnerId) {
            deterministic
        } else {
            registry.select(world, cap, ctx, SelectionMode.PINNED, proposal.generatorId)
        }
        val reworded = base.spec.copy(
            instruction = proposal.instruction ?: base.spec.instruction,
            hints = proposal.hints ?: base.spec.hints
        )
        RpLog.i(
            RpLog.Tag.AI,
            "composed ${proposal.generatorId} (${reworded.type})" +
                (if (proposal.instruction != null) " reworded" else "")
        )
        return SelectionResult(
            spec = reworded,
            ranked = deterministic.ranked,
            mode = deterministic.mode,
            stepBudget = base.stepBudget,
            winnerId = proposal.generatorId
        )
    }
}
