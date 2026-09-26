package com.cognex.realplay.ai

import android.content.Context

/**
 * Process-wide provider for the optional on-device composer model (Architecture §6.5).
 *
 * [init] is called once from the Application/Activity with the application context so the
 * [GemmaModel] can load the side-loaded Tier-B bundle. Everything downstream (the engine, the
 * [ChallengeComposer]) pulls the shared [model] — a single [LlmInference] is expensive, so we keep
 * exactly one. Before [init] (e.g. in pure-JVM unit tests) [model] is an always-off [MockModel], so
 * the deterministic path is used and nothing touches Android.
 */
object AiRuntime {

    @Volatile private var shared: LanguageModel? = null

    /** Idempotent. Call once with the application context. */
    fun init(context: Context) {
        if (shared == null) shared = GemmaModel(context.applicationContext)
    }

    /** The shared model, or an always-off [MockModel] if not initialised (tests / AI-off). */
    fun model(): LanguageModel = shared ?: MockModel()
}
