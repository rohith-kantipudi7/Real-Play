package com.cognex.realplay.ai

/**
 * Pure request/response shaping for the Azure AI Foundry (OpenAI-compatible) chat completions call
 * (Architecture v3.7). No Android, no network — [CloudModel] does the actual HTTP I/O; this is the
 * testable part. JSON is hand-built/hand-parsed (no org.json) to match [SchemaValidator]'s existing
 * dependency-free convention, which also keeps it usable in local JVM unit tests.
 */
object AzureChatRequest {

    /** The completions URL: an explicit [overrideUrl] wins, else built from endpoint+deployment. */
    fun completionsUrl(endpoint: String, deployment: String, apiVersion: String, overrideUrl: String?): String {
        if (!overrideUrl.isNullOrBlank()) return overrideUrl
        val base = endpoint.trimEnd('/')
        return "$base/openai/deployments/$deployment/chat/completions?api-version=$apiVersion"
    }

    /**
     * The request body: a system + user message (the composer [prompt] from [PromptBuilder]), JSON
     * schema structured output constrained to the [ComposerProposal] shape (§6.5) — the model can
     * only return `generatorId` + optional `instruction`/`hints`, nothing else. Low token budget,
     * moderate temperature for varied-but-safe wording.
     */
    fun buildBody(prompt: String, maxTokens: Int, temperature: Double): String {
        val escapedPrompt = escape(prompt)
        return """
            {"messages":[{"role":"system","content":"Reply with ONLY one JSON object matching the schema. No prose."},{"role":"user","content":"$escapedPrompt"}],"max_tokens":$maxTokens,"temperature":$temperature,"response_format":{"type":"json_schema","json_schema":{"name":"composer_proposal","strict":true,"schema":{"type":"object","properties":{"generatorId":{"type":"string"},"instruction":{"type":["string","null"]},"hints":{"type":["array","null"],"items":{"type":"string"}}},"required":["generatorId","instruction","hints"],"additionalProperties":false}}}}
        """.trimIndent()
    }

    /** Extracts `choices[0].message.content` from a chat-completions response body. */
    fun extractContent(responseBody: String): String? {
        val m = Regex("\"content\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\"").find(responseBody) ?: return null
        return unescape(m.groupValues[1]).takeIf { it.isNotBlank() }
    }

    private fun escape(s: String): String =
        s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "")

    private fun unescape(s: String): String =
        s.replace("\\n", "\n").replace("\\\"", "\"").replace("\\\\", "\\")
}
