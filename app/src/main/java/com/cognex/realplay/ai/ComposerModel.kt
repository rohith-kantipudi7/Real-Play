package com.cognex.realplay.ai

/**
 * The composer's view of ONE feasible skill (Architecture §6.5). Pure JVM.
 *
 * This is the *only* material the AI composer sees about a skill — a compact, safe description built
 * by [PromptBuilder] from the registry's ranked candidates. It intentionally exposes just enough to
 * arrange skills (id, what the primitive does, why it's feasible) and nothing that would let the
 * model invent thresholds or actors: the deterministic generator still binds the real spec.
 */
data class SkillDigest(
    val generatorId: String,
    val type: String,
    val feasibility: Float,
    /** A short, human-readable summary of the verifiable primitive, e.g. "move two objects closer". */
    val primitive: String
)

/**
 * The compact WorldState digest the composer sees (Architecture §6.5). Pure JVM.
 *
 * Present actors + affordance/capability summary only. No raw geometry, no thresholds — the composer
 * arranges skills over what exists; it never measures.
 */
data class WorldDigest(
    val objectLabels: List<String>,
    val distinctColors: List<String>,
    val playerCount: Int,
    val zoneCount: Int,
    val ageBand: String,
    val tier: String,
    val trackOnlyMode: Boolean
)

/**
 * A parsed AI composer proposal (Architecture §6.5). Pure JVM.
 *
 * The composer may only choose WHICH feasible skill to play and HOW it is worded — never how it is
 * judged. So a proposal carries a [generatorId] (must be one of the feasible skills) and optional
 * reworded [instruction] / [hints]. The verifiable steps, params and actor bindings are always
 * produced by the deterministic generator, never by the model (§20 invariants 18–20).
 */
data class ComposerProposal(
    val generatorId: String,
    val instruction: String?,
    val hints: List<String>?
)
