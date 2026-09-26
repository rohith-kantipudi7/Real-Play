package com.cognex.realplay.ai

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** CompositeModel — tries each backend in order, falls through on null (Architecture v3.7). */
class CompositeModelTest {

    private class FakeModel(
        override val name: String,
        override val isAvailable: Boolean,
        private val response: String? = null
    ) : LanguageModel {
        var calls = 0
        override suspend fun propose(prompt: String): String? {
            calls++
            return response
        }
    }

    @Test
    fun firstAvailableModel_thatAnswers_wins() = runBlocking {
        val cloud = FakeModel("cloud", isAvailable = true, response = "cloud-answer")
        val gemma = FakeModel("gemma", isAvailable = true, response = "gemma-answer")
        val composite = CompositeModel(listOf(cloud, gemma))

        assertEquals("cloud-answer", composite.propose("prompt"))
        assertEquals(1, cloud.calls)
        assertEquals(0, gemma.calls)
    }

    @Test
    fun fallsThrough_whenFirstReturnsNull() = runBlocking {
        val cloud = FakeModel("cloud", isAvailable = true, response = null)
        val gemma = FakeModel("gemma", isAvailable = true, response = "gemma-answer")
        val composite = CompositeModel(listOf(cloud, gemma))

        assertEquals("gemma-answer", composite.propose("prompt"))
        assertEquals(1, cloud.calls)
        assertEquals(1, gemma.calls)
    }

    @Test
    fun skipsUnavailableModels_withoutCallingThem() = runBlocking {
        val cloud = FakeModel("cloud", isAvailable = false, response = "should never be seen")
        val gemma = FakeModel("gemma", isAvailable = true, response = "gemma-answer")
        val composite = CompositeModel(listOf(cloud, gemma))

        assertEquals("gemma-answer", composite.propose("prompt"))
        assertEquals(0, cloud.calls)
    }

    @Test
    fun isAvailable_trueWhenAnyChildIsAvailable() {
        val cloud = FakeModel("cloud", isAvailable = false)
        val gemma = FakeModel("gemma", isAvailable = true)
        assertTrue(CompositeModel(listOf(cloud, gemma)).isAvailable)
        assertFalse(CompositeModel(listOf(cloud, FakeModel("g2", isAvailable = false))).isAvailable)
    }

    @Test
    fun returnsNull_whenEveryModelFails() = runBlocking {
        val cloud = FakeModel("cloud", isAvailable = true, response = null)
        val gemma = FakeModel("gemma", isAvailable = true, response = null)
        assertNull(CompositeModel(listOf(cloud, gemma)).propose("prompt"))
    }
}
