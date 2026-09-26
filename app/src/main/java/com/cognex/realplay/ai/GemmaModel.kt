package com.cognex.realplay.ai

import android.content.Context
import com.cognex.realplay.util.RpLog
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File

/**
 * The on-device, fully-offline composer backend (Architecture §2 SLM, §3.1, §3.3, §6.5).
 *
 * Wraps MediaPipe `tasks-genai` [LlmInference] running a **side-loaded Tier-B** Gemma `.task`
 * bundle. NO network, NO API key — everything runs on the phone (§20 invariant 7, offline-first).
 * The model is OPTIONAL by definition: if the bundle is absent the app is fully playable and
 * [isAvailable] is false, so the deterministic composer is always used (the §S11 "AI-OFF" gate).
 *
 * MODEL RESOLUTION (§1.2 Tier-B, side-loaded to /sdcard/realplay/models/, never committed):
 *   1. gemma-3n-E4B-it-int4.task  (primary — effective 4B, mobile-optimised)
 *   2. gemma3-1b-it-int4.task     (smaller/faster fallback)
 *   3. qwen2.5-1.5b-instruct.task (backup)
 *
 * Threading (§3.3): the model is lazily loaded and inference runs on [Dispatchers.IO], serialised
 * by a [Mutex] (one generation at a time), under a hard [TIMEOUT_MS]. A timeout returns null and
 * the deterministic spec is used — perception is never blocked (the analyzer never awaits this).
 */
class GemmaModel(private val context: Context) : LanguageModel {

    override val name: String get() = "gemma-local"

    /** Cheap check: a Tier-B bundle is present on disk. Actual load happens lazily in [propose]. */
    override val isAvailable: Boolean get() = resolveModelFile() != null

    @Volatile private var engine: LlmInference? = null
    @Volatile private var loadFailed = false
    private val mutex = Mutex()

    override suspend fun propose(prompt: String): String? {
        if (loadFailed) return null
        val engine = ensureLoaded() ?: return null
        return withContext(Dispatchers.IO) {
            withTimeoutOrNull(TIMEOUT_MS) {
                mutex.withLock {
                    runCatching { engine.generateResponse(prompt) }
                        .onFailure { RpLog.w(RpLog.Tag.AI, "gemma generate failed: ${it.message}") }
                        .getOrNull()
                }
            }
        }
    }

    private suspend fun ensureLoaded(): LlmInference? {
        engine?.let { return it }
        return mutex.withLock {
            engine?.let { return it }
            if (loadFailed) return null
            val file = resolveModelFile() ?: run { loadFailed = true; return null }
            withContext(Dispatchers.IO) {
                runCatching {
                    val options = LlmInference.LlmInferenceOptions.builder()
                        .setModelPath(file.absolutePath)
                        .setMaxTokens(MAX_TOKENS)
                        .build()
                    LlmInference.createFromOptions(context, options)
                }.onSuccess {
                    engine = it
                    RpLog.i(RpLog.Tag.MODEL, "Gemma loaded: ${file.name} (${file.length()} bytes)")
                }.onFailure {
                    loadFailed = true
                    RpLog.e(RpLog.Tag.MODEL, "Gemma load failed", it)
                }.getOrNull()
            }
        }
    }

    private fun resolveModelFile(): File? =
        TIER_B_CANDIDATES.map { File(it) }.firstOrNull { it.exists() && it.length() > 0L }

    private companion object {
        val TIER_B_CANDIDATES = listOf(
            "/sdcard/realplay/models/gemma-3n-E4B-it-int4.task",
            "/sdcard/realplay/models/gemma3-1b-it-int4.task",
            "/sdcard/realplay/models/qwen2.5-1.5b-instruct.task"
        )
        const val TIMEOUT_MS = 8_000L   // §3.3 hard timeout — 4B on-device is slower; only a fallback
        const val MAX_TOKENS = 512
    }
}
