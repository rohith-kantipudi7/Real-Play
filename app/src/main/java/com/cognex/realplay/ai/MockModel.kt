package com.cognex.realplay.ai

/**
 * The always-available offline model (Architecture §6.5, §S11 AI-OFF gate). Pure JVM.
 *
 * [MockModel] never proposes anything — [propose] returns null — so the composer always falls back
 * to the deterministic spec. It exists to (a) prove the AI-OFF path in unit tests and (b) be the
 * default [LanguageModel] when no cloud key is configured, so the pipeline shape is identical
 * whether or not a real model is present.
 */
class MockModel : LanguageModel {
    override val name: String = "mock"
    override val isAvailable: Boolean = false
    override suspend fun propose(prompt: String): String? = null
}
