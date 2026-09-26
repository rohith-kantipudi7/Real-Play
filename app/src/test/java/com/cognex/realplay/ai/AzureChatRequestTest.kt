package com.cognex.realplay.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** AzureChatRequest — request/response shaping for the v3.7 cloud composer. Pure JVM. */
class AzureChatRequestTest {

    @Test
    fun completionsUrl_overrideWins() {
        val url = AzureChatRequest.completionsUrl(
            endpoint = "https://example.services.ai.azure.com",
            deployment = "gpt-5.5",
            apiVersion = "2024-08-01-preview",
            overrideUrl = "https://custom.example/completions"
        )
        assertEquals("https://custom.example/completions", url)
    }

    @Test
    fun completionsUrl_buildsFromEndpointAndDeployment_whenNoOverride() {
        val url = AzureChatRequest.completionsUrl(
            endpoint = "https://example.services.ai.azure.com/",
            deployment = "gpt-5.5",
            apiVersion = "2024-08-01-preview",
            overrideUrl = null
        )
        assertEquals(
            "https://example.services.ai.azure.com/openai/deployments/gpt-5.5/chat/completions?api-version=2024-08-01-preview",
            url
        )
    }

    @Test
    fun completionsUrl_blankOverrideIsIgnored() {
        val url = AzureChatRequest.completionsUrl("https://x.example", "d", "v1", "   ")
        assertTrue(url.contains("/openai/deployments/d/chat/completions"))
    }

    @Test
    fun buildBody_containsPromptAndStructuredOutputSchema() {
        val body = AzureChatRequest.buildBody("Choose a skill.", maxTokens = 800)
        assertTrue(body.contains("Choose a skill."))
        assertTrue(body.contains("\"max_completion_tokens\":800"))
        assertTrue(body.contains("\"generatorId\""))
        assertTrue(body.contains("json_schema"))
    }

    @Test
    fun buildBody_usesMaxCompletionTokensAndNoTemperature() {
        val body = AzureChatRequest.buildBody("x", maxTokens = 100)
        assertTrue(body.contains("\"max_completion_tokens\":100"))
        assertTrue(!body.contains("\"max_tokens\""))
        assertTrue(!body.contains("\"temperature\""))
    }

    @Test
    fun buildBody_escapesQuotesAndNewlinesInPrompt() {
        val body = AzureChatRequest.buildBody("He said \"hi\"\nline2", maxTokens = 10)
        assertTrue(body.contains("He said \\\"hi\\\"\\nline2"))
    }

    @Test
    fun extractContent_parsesAssistantMessage() {
        val response = """
            {"choices":[{"message":{"role":"assistant","content":"{\"generatorId\":\"G1\"}"}}]}
        """.trimIndent()
        assertEquals("""{"generatorId":"G1"}""", AzureChatRequest.extractContent(response))
    }

    @Test
    fun extractContent_returnsNull_onGarbage() {
        assertNull(AzureChatRequest.extractContent("not json at all"))
        assertNull(AzureChatRequest.extractContent("""{"choices":[]}"""))
    }
}
