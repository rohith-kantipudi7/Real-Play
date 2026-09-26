package com.cognex.realplay.ai

import com.cognex.realplay.challenge.AgeBand
import com.cognex.realplay.challenge.CFix
import com.cognex.realplay.challenge.ChallengeRegistry
import com.cognex.realplay.challenge.ChallengeType
import com.cognex.realplay.challenge.DifficultyKnobs
import com.cognex.realplay.challenge.GenerationContext
import com.cognex.realplay.challenge.SelectionMode
import com.cognex.realplay.challenge.SelectionResult
import com.cognex.realplay.challenge.Tier
import com.cognex.realplay.challenge.generators.G0LastResortGenerator
import com.cognex.realplay.challenge.generators.G1MoveNearGenerator
import com.cognex.realplay.world.ColorTag
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

/**
 * Tests the composition line (Architecture §6.5, §20 invariants 18–20): a valid proposal re-arranges
 * or rewords a REGISTERED skill; anything else falls back to the deterministic spec unchanged.
 */
class ChallengeComposerTest {

    private class StubModel(
        private val response: String?,
        override val isAvailable: Boolean = true
    ) : LanguageModel {
        override val name = "stub"
        override suspend fun propose(prompt: String): String? = response
    }

    private val registry = ChallengeRegistry(listOf(G0LastResortGenerator(), G1MoveNearGenerator()))

    private fun scene(): Triple<com.cognex.realplay.world.WorldState, com.cognex.realplay.world.SceneCapability, GenerationContext> {
        val o1 = CFix.obj(1, label = "cup", cx = 0.2f)
        val o2 = CFix.obj(2, label = "book", cx = 0.8f)
        val affs = listOf(CFix.aff(1, movable = true), CFix.aff(2, movable = true))
        val world = CFix.world(objects = listOf(o1, o2), affordances = affs)
        val cap = CFix.cap(
            movableCount = 2, nameableCount = 2,
            distinctColors = setOf(ColorTag.BLUE, ColorTag.RED), spread = 0.6f, richness = 0.6f
        )
        val ctx = GenerationContext(
            ageBand = AgeBand.MIDDLE,
            effectiveTier = Tier.MEDIUM,
            knobs = DifficultyKnobs.forTier(Tier.MEDIUM, cap.spread, cap.stability),
            trackOnlyMode = false
        )
        return Triple(world, cap, ctx)
    }

    private fun deterministic(): SelectionResult {
        val (w, c, ctx) = scene()
        return registry.select(w, c, ctx, SelectionMode.RECOMMENDED)
    }

    @Test fun deterministic_winner_is_G1() {
        assertEquals("G1", deterministic().winnerId)
    }

    @Test fun valid_reword_keeps_skill_changes_instruction() = runBlocking {
        val (w, c, ctx) = scene()
        val det = registry.select(w, c, ctx, SelectionMode.RECOMMENDED)
        val composer = ChallengeComposer(
            registry,
            StubModel("""{"generatorId":"G1","instruction":"Push them together!"}"""),
            enabled = { true }
        )
        val out = composer.compose(w, c, ctx, det)
        assertEquals("G1", out.winnerId)
        assertEquals("Push them together!", out.spec.instruction)
    }

    @Test fun valid_different_skill_is_rebound_by_registry() = runBlocking {
        val (w, c, ctx) = scene()
        val det = registry.select(w, c, ctx, SelectionMode.RECOMMENDED)
        val composer = ChallengeComposer(
            registry, StubModel("""{"generatorId":"G0"}"""), enabled = { true }
        )
        val out = composer.compose(w, c, ctx, det)
        assertEquals("G0", out.winnerId)
        assertEquals(ChallengeType.LAST_RESORT, out.spec.type)
    }

    @Test fun invalid_proposal_falls_back_to_deterministic() = runBlocking {
        val (w, c, ctx) = scene()
        val det = registry.select(w, c, ctx, SelectionMode.RECOMMENDED)
        val composer = ChallengeComposer(
            registry, StubModel("""{"generatorId":"G9"}"""), enabled = { true }
        )
        val out = composer.compose(w, c, ctx, det)
        assertSame(det, out)
    }

    @Test fun disabled_composer_returns_deterministic() = runBlocking {
        val (w, c, ctx) = scene()
        val det = registry.select(w, c, ctx, SelectionMode.RECOMMENDED)
        val composer = ChallengeComposer(
            registry,
            StubModel("""{"generatorId":"G0"}"""),
            enabled = { false }
        )
        assertSame(det, composer.compose(w, c, ctx, det))
    }

    @Test fun unavailable_model_returns_deterministic() = runBlocking {
        val (w, c, ctx) = scene()
        val det = registry.select(w, c, ctx, SelectionMode.RECOMMENDED)
        val composer = ChallengeComposer(
            registry,
            StubModel("""{"generatorId":"G0"}""", isAvailable = false),
            enabled = { true }
        )
        assertSame(det, composer.compose(w, c, ctx, det))
    }

    @Test fun mock_model_is_never_available() = runBlocking {
        assertEquals(false, MockModel().isAvailable)
        assertEquals(null, MockModel().propose("anything"))
    }

    @Test fun repeated_same_scene_uses_cache_not_a_second_model_call() = runBlocking {
        var calls = 0
        val countingModel = object : LanguageModel {
            override val name = "counting"
            override val isAvailable = true
            override suspend fun propose(prompt: String): String? {
                calls++
                return """{"generatorId":"G1","instruction":"Push them together!"}"""
            }
        }
        val (w, c, ctx) = scene()
        val det = registry.select(w, c, ctx, SelectionMode.RECOMMENDED)
        val composer = ChallengeComposer(registry, countingModel, enabled = { true })

        composer.compose(w, c, ctx, det)
        composer.compose(w, c, ctx, det)

        assertEquals(1, calls)
    }
}
