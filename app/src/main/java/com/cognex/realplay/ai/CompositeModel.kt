package com.cognex.realplay.ai

/**
 * Tries each [models] entry in order until one returns a non-null proposal (Architecture v3.7 —
 * cloud → on-device → deterministic). [isAvailable] is true when ANY child is available, so
 * [ChallengeComposer.active] still gates correctly; a model that is unavailable or returns null
 * (timeout, network error, load failure) simply falls through to the next one. If every model
 * fails, [propose] returns null and [ChallengeComposer] uses the deterministic spec unchanged —
 * the chain never has to know or care which backend actually answered.
 */
class CompositeModel(private val models: List<LanguageModel>) : LanguageModel {

    override val name: String get() = models.joinToString(" > ") { it.name }

    override val isAvailable: Boolean get() = models.any { it.isAvailable }

    override suspend fun propose(prompt: String): String? {
        for (model in models) {
            if (!model.isAvailable) continue
            model.propose(prompt)?.let { return it }
        }
        return null
    }
}
