package com.cognex.realplay.ai

import android.content.Context
import com.cognex.realplay.BuildConfig

/**
 * Process-wide provider for the optional composer model chain (Architecture §6.5, v3.7).
 *
 * [init] is called once from the Application/Activity with the application context. The chain
 * tried, in order, is CLOUD (Azure AI Foundry, if [BuildConfig] has endpoint/key/deployment
 * configured) → on-device Gemma (if a Tier-B `.task` is side-loaded) → nothing, in which case
 * [ChallengeComposer] falls back to the always-present deterministic composer (§20 invariants
 * 18–20). Before [init] (e.g. in pure-JVM unit tests) [model] is an always-off [MockModel], so the
 * deterministic path is used and nothing touches Android or the network.
 */
object AiRuntime {

    @Volatile private var shared: LanguageModel? = null

    /** Idempotent. Call once with the application context. */
    fun init(context: Context) {
        if (shared != null) return
        val cloud = CloudModel(
            endpoint = BuildConfig.AZURE_ENDPOINT,
            apiKey = BuildConfig.AZURE_API_KEY,
            deployment = BuildConfig.AZURE_DEPLOYMENT,
            apiVersion = BuildConfig.AZURE_API_VERSION,
            overrideUrl = BuildConfig.AZURE_CHAT_COMPLETIONS_URL.takeIf { it.isNotBlank() }
        )
        val gemma = GemmaModel(context.applicationContext)
        shared = CompositeModel(listOf(cloud, gemma))
    }

    /** The shared model, or an always-off [MockModel] if not initialised (tests / AI-off). */
    fun model(): LanguageModel = shared ?: MockModel()
}
