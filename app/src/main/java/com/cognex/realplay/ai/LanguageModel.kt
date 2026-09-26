package com.cognex.realplay.ai

/**
 * The optional runtime AI (Architecture §3.1, §3.2, §6.5). Pure interface — no Android.
 *
 * A [LanguageModel] is a **composer proposal source**: given a fully-built prompt (the feasible
 * skill digest + a live WorldState digest, produced by [PromptBuilder]), it returns raw model text
 * that is then parsed and gated by [SchemaValidator]. It is NEVER on the truth line — its output is
 * always a *proposal* that must survive validation, and a null/blank/invalid result simply means the
 * deterministic composer's spec is used unchanged (the §S11 "AI-OFF" gate, §20 invariants 18–20).
 *
 * Threading (§3.3): [propose] is a suspend function meant to run on the model's own dispatcher with
 * a hard timeout. It must never be awaited by the camera analyzer.
 */
interface LanguageModel {

    /** Human-readable model identity, for logs (e.g. "mock", "cloud:gpt-4o-mini"). */
    val name: String

    /** True only when the model can actually be called (e.g. a cloud key is configured). */
    val isAvailable: Boolean

    /**
     * Returns raw proposal text for [prompt], or null when unavailable / failed / timed out.
     * Implementations must swallow their own errors and return null rather than throw — the caller
     * treats any null as "use the deterministic spec".
     */
    suspend fun propose(prompt: String): String?
}
