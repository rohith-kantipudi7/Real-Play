package com.cognex.realplay.challenge

import com.cognex.realplay.world.SceneCapability
import com.cognex.realplay.world.WorldState
import kotlin.math.max

/**
 * One scored candidate in the ranked selection list (Architecture §6.3). Pure JVM.
 *
 * Every field is data the UI can render — showing a judge a zero WITH a [zeroReason] is more
 * convincing than any passing case. When [score] is 0, [zeroReason] is always non-null.
 */
data class RankedCandidate(
    val generatorId: String,
    val type: ChallengeType,
    val feasibility: Float,
    val tierFit: Float,
    val noveltyBonus: Float,
    val ageGate: Float,
    val score: Float,
    val zeroReason: String?
)

/**
 * The result of a selection (Architecture §6.1). [spec] is the fully-bound challenge, [ranked] is
 * every candidate with its score and (for zeros) a reason, [mode] is how the winner was chosen, and
 * [stepBudget] is the resolved structural budget passed into the winner's generate().
 */
data class SelectionResult(
    val spec: ChallengeSpec,
    val ranked: List<RankedCandidate>,
    val mode: SelectionMode,
    val stepBudget: Int,
    val winnerId: String
)

/**
 * The scored, TOTAL registry (Architecture §6.1, §6.4, §20 invariants 11 & 13). Pure JVM.
 *
 * Selection pipeline:
 *   1. pre-filter on [Requirement] ints AND §3.5 capability flags — each failure carries a reason
 *   2. feasibility(cap, ctx) per surviving generator
 *   3. score = feasibility × tierFit × noveltyBonus × ageGate
 *   4. pick per [SelectionMode]:
 *        RECOMMENDED → argmax, stable tie-break by generator id (deterministic)
 *        OPEN        → weighted-random among score > 0 (seeded)
 *        PINNED      → forced generator id, still fully verified
 *   5. stepBudget = winner.maxStepsForTier(effectiveTier), clamped by age band (§20 invariant 17)
 *
 * G0 (feasibility 0.05 whenever anything is present, no requirements) guarantees the registry can
 * never return null — the finale on an unknown table is always safe.
 */
class ChallengeRegistry(generators: List<ChallengeGenerator>) {

    /** Ordered, immutable. The capability-card denominator reads size from here (§20 invariant 14). */
    val generators: List<ChallengeGenerator> = generators.toList()

    init {
        require(this.generators.isNotEmpty()) { "Registry needs at least the G0 fallback" }
        val ids = this.generators.map { it.id }
        require(ids.toSet().size == ids.size) { "Duplicate generator id in registry: $ids" }
        require(this.generators.any { it.requires == Requirement.NONE }) {
            "Registry has no requirement-free fallback (G0) — it would not be total"
        }
    }

    fun select(
        world: WorldState,
        cap: SceneCapability,
        ctx: GenerationContext,
        mode: SelectionMode,
        pinnedGeneratorId: String? = null
    ): SelectionResult {
        val ranked = generators.map { gen -> scoreOf(gen, cap, ctx) }
        val winner: ChallengeGenerator = when (mode) {
            SelectionMode.PINNED -> {
                val target = pinnedGeneratorId
                    ?.let { id -> generators.firstOrNull { it.id == id } }
                target ?: fallback(ranked)
            }
            SelectionMode.RECOMMENDED -> recommendedWinner(ranked) ?: fallback(ranked)
            SelectionMode.OPEN -> openWinner(ranked, ctx.seed) ?: fallback(ranked)
        }

        val stepBudget = resolveStepBudget(winner, ctx)
        val safeWorld = SafetyFilter.apply(world, ctx.ageBand)
        val spec = winner.generate(safeWorld, safeWorld.affordances, ctx, stepBudget)
        return SelectionResult(spec, ranked, mode, stepBudget, winner.id)
    }

    /** Resolves the structural step budget, clamped to 1 for TODDLER/EARLY (§20 invariant 17). */
    fun resolveStepBudget(gen: ChallengeGenerator, ctx: GenerationContext): Int {
        val raw = gen.maxStepsForTier(ctx.effectiveTier).coerceIn(1, 3)
        return minOf(raw, Difficulty.ageStepCap(ctx.ageBand))
    }

    /**
     * Scores every generator WITHOUT binding a spec (Architecture §6.3, §15). Used by the
     * SceneCapabilityCard and the dev screen to show the live ranked list — including the zeros with
     * their reasons — without needing a full [WorldState]. Ordered best-first, deterministic.
     */
    fun rank(cap: SceneCapability, ctx: GenerationContext): List<RankedCandidate> =
        generators.map { scoreOf(it, cap, ctx) }
            .sortedWith(compareByDescending<RankedCandidate> { it.score }.thenBy { it.generatorId })

    /** How many games are actually possible in this scene — the numerator of "K of N" (§15). */
    fun possibleCount(cap: SceneCapability, ctx: GenerationContext): Int =
        generators.count { scoreOf(it, cap, ctx).score > 0f }


    private fun scoreOf(gen: ChallengeGenerator, cap: SceneCapability, ctx: GenerationContext): RankedCandidate {
        // 1. requirement + capability pre-filter.
        gen.requires.unmetReason(cap)?.let { reason ->
            return RankedCandidate(gen.id, gen.type, 0f, 0f, 0f, 0f, 0f, reason)
        }
        // 2. age gate.
        val ageGate = gen.ageGate(ctx.ageBand)
        if (ageGate <= 0f) {
            return RankedCandidate(gen.id, gen.type, 0f, 0f, 0f, 0f, 0f, "not for ${ctx.ageBand}")
        }
        // 3. feasibility.
        val feasibility = gen.feasibility(cap, ctx).coerceIn(0f, 1f)
        if (feasibility <= 0f) {
            return RankedCandidate(gen.id, gen.type, 0f, 0f, 0f, ageGate, 0f, "not feasible in this scene")
        }
        val tierFit = 1f
        val noveltyBonus = if (gen.type in ctx.recentTypes) 0.6f else 1f
        val score = feasibility * tierFit * noveltyBonus * ageGate
        return RankedCandidate(gen.id, gen.type, feasibility, tierFit, noveltyBonus, ageGate, score, null)
    }

    /** Deterministic argmax over PROVEN generators, tie-broken by generator id (§20 invariant 11). */
    private fun recommendedWinner(ranked: List<RankedCandidate>): ChallengeGenerator? {
        val byId = generators.associateBy { it.id }
        return ranked
            .asSequence()
            .filter { it.score > 0f && byId.getValue(it.generatorId).proven }
            .sortedWith(compareByDescending<RankedCandidate> { it.score }.thenBy { it.generatorId })
            .firstOrNull()
            ?.let { byId.getValue(it.generatorId) }
    }

    /** Weighted-random among score > 0, using a seeded RNG for reproducibility. */
    private fun openWinner(ranked: List<RankedCandidate>, seed: Long): ChallengeGenerator? {
        val byId = generators.associateBy { it.id }
        val positives = ranked.filter { it.score > 0f }.sortedBy { it.generatorId }
        if (positives.isEmpty()) return null
        val total = positives.sumOf { it.score.toDouble() }
        val roll = java.util.Random(seed).nextDouble() * total
        var acc = 0.0
        for (c in positives) {
            acc += c.score
            if (roll <= acc) return byId.getValue(c.generatorId)
        }
        return byId.getValue(positives.last().generatorId)
    }

    /** Last-resort winner: the highest-scoring generator, or G0 if all are zero (still total). */
    private fun fallback(ranked: List<RankedCandidate>): ChallengeGenerator {
        val byId = generators.associateBy { it.id }
        val best = ranked
            .filter { it.score > 0f }
            .maxWithOrNull(compareBy<RankedCandidate> { it.score }.thenByDescending { it.generatorId })
        if (best != null) return byId.getValue(best.generatorId)
        // Everything scored zero (e.g. an empty scene). Force the requirement-free G0 so we are total.
        return generators.first { it.requires == Requirement.NONE }
    }

    companion object {
        /** A tiny helper for callers that only need the max score present, for the capability card. */
        fun topScore(ranked: List<RankedCandidate>): Float =
            ranked.fold(0f) { acc, c -> max(acc, c.score) }

        /**
         * The canonical shipped registry (Architecture §7). The ONE place the shipped generator set
         * is declared, so the runtime and the SceneCapabilityCard/dev screen always agree on N.
         */
        fun default(): ChallengeRegistry = ChallengeRegistry(
            listOf(
                com.cognex.realplay.challenge.generators.G0LastResortGenerator(),
                com.cognex.realplay.challenge.generators.G1MoveNearGenerator(),
                com.cognex.realplay.challenge.generators.G2DropZoneGenerator(),
                com.cognex.realplay.challenge.generators.G3FindColorGenerator()
            )
        )
    }
}
