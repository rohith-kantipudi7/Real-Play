package com.cognex.realplay.ai

import com.cognex.realplay.util.RpLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets

/**
 * The optional CLOUD composer backend (Architecture v3.7). Sits ABOVE [GemmaModel] in the fallback
 * chain via [CompositeModel] — tried first since it can run a larger model, but it is still just a
 * proposal source: every output passes through the same [SchemaValidator] as Gemma's, and a
 * timeout/network error/invalid response falls straight through with zero user-visible difference
 * (§20 invariants 18–20). Sends ONLY the text prompt [PromptBuilder] builds — object labels,
 * colours, counts and capability flags — NEVER a camera frame.
 *
 * Threading (§3.3): runs on [Dispatchers.IO] under a hard ~2 s timeout; never awaited by the
 * CameraX analyzer (the caller, [ChallengeComposer], already runs off-thread).
 *
 * SECURITY: [apiKey] must come from local, uncommitted config (local.properties → BuildConfig),
 * never a literal in source. Embedding a raw cloud key in a client APK is still not safe for public
 * distribution — anyone can decompile the APK and extract it. A production build would proxy this
 * call through a backend that holds the key server-side instead of the phone.
 */
class CloudModel(
    private val endpoint: String,
    private val apiKey: String,
    private val deployment: String,
    private val apiVersion: String,
    private val overrideUrl: String? = null
) : LanguageModel {

    override val name: String = "cloud:$deployment"

    override val isAvailable: Boolean
        get() = endpoint.isNotBlank() && apiKey.isNotBlank() && deployment.isNotBlank()

    override suspend fun propose(prompt: String): String? {
        if (!isAvailable) return null
        return withContext(Dispatchers.IO) {
            withTimeoutOrNull(TIMEOUT_MS) {
                runCatching { call(prompt) }
                    .onFailure { RpLog.w(RpLog.Tag.AI, "cloud propose failed: ${it.message}") }
                    .getOrNull()
            }
        }
    }

    private fun call(prompt: String): String? {
        val url = URL(AzureChatRequest.completionsUrl(endpoint, deployment, apiVersion, overrideUrl))
        val body = AzureChatRequest.buildBody(prompt, MAX_TOKENS, TEMPERATURE)
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("api-key", apiKey)
        }
        return try {
            conn.outputStream.use { it.write(body.toByteArray(StandardCharsets.UTF_8)) }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val text = stream?.bufferedReader(StandardCharsets.UTF_8)?.use { it.readText() }
            if (code !in 200..299) {
                RpLog.w(RpLog.Tag.AI, "cloud HTTP $code: ${text?.take(200)}")
                return null
            }
            text?.let { AzureChatRequest.extractContent(it) }
        } finally {
            conn.disconnect()
        }
    }

    private companion object {
        const val TIMEOUT_MS = 2_000L
        const val CONNECT_TIMEOUT_MS = 1_500
        const val READ_TIMEOUT_MS = 1_800
        const val MAX_TOKENS = 200
        const val TEMPERATURE = 0.5
    }
}
