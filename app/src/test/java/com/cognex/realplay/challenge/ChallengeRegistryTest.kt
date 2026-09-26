package com.cognex.realplay.challenge

import com.cognex.realplay.challenge.generators.G0LastResortGenerator
import com.cognex.realplay.challenge.generators.G1MoveNearGenerator
import com.cognex.realplay.world.ColorTag
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/** Registry totality + determinism + pre-filter reasons (Architecture §6, §20 invariants 11 & 13). */
class ChallengeRegistryTest {

    private fun registry() = ChallengeRegistry(listOf(G0LastResortGenerator(), G1MoveNearGenerator()))

    @Test
    fun neverReturnsNull_overManyRandomScenes() {
        val reg = registry()
        val rng = Random(42)
        repeat(500) {
            val objCount = rng.nextInt(0, 5)
            val playerCount = rng.nextInt(0, 2)
            val objects = (0 until objCount).map {
                CFix.obj(it, cx = rng.nextFloat(), cy = rng.nextFloat(), confidence = 0.6f + rng.nextFloat() * 0.4f)
            }
            val players = (0 until playerCount).map { CFix.player(it, confidence = 0.6f + rng.nextFloat() * 0.4f) }
            val affs = objects.map { CFix.aff(it.trackId, movable = true) }
            val world = CFix.world(objects = objects, players = players, affordances = affs)
            val cap = CFix.cap(
                movableCount = objCount,
                nameableCount = objCount,
                playerCount = playerCount,
                distinctColors = if (objCount > 0) setOf(ColorTag.BLUE) else emptySet(),
                spread = rng.nextFloat(),
                richness = rng.nextFloat()
            )
            val result = reg.select(world, cap, CFix.ctx(seed = it.toLong()), SelectionMode.RECOMMENDED)
            assertNotNull("registry returned no spec for scene #$it", result.spec)
            assertTrue("spec must have at least one step", result.spec.steps.isNotEmpty())
        }
    }

    @Test
    fun emptyScene_stillSelectsG0() {
        val reg = registry()
        val result = reg.select(CFix.world(), CFix.cap(), CFix.ctx(), SelectionMode.RECOMMENDED)
        assertEquals("G0", result.winnerId)
        assertEquals(ChallengeType.LAST_RESORT, result.spec.type)
        assertTrue(result.spec.steps.isNotEmpty())
    }

    @Test
    fun g0_selectsAndGenerates_withOneUnlabelledObject() {
        val reg = registry()
        // One object, not enough for G1 (needs 2 movable). No zone, no player, no semantic label.
        val obj = CFix.obj(7, label = "", color = ColorTag.GREEN, cx = 0.5f, cy = 0.5f, w = 0.3f, h = 0.3f)
        val world = CFix.world(objects = listOf(obj), affordances = listOf(CFix.aff(7, movable = true, nameable = false)))
        val cap = CFix.cap(movableCount = 1, nameableCount = 0, semanticLabelsAvailable = false)
        val result = reg.select(world, cap, CFix.ctx(), SelectionMode.RECOMMENDED)
        assertEquals("G0", result.winnerId)
        assertEquals(com.cognex.realplay.verify.RuleId.OBJECT_PRESENT, result.spec.steps.first().rule)
        assertTrue(result.spec.actors.first() is ActorRef.ByTrackId)
    }

    @Test
    fun recommended_isDeterministic_tenTimesOnSameScene() {
        val reg = registry()
        val objects = listOf(CFix.obj(1, cx = 0.2f), CFix.obj(2, cx = 0.8f))
        val affs = objects.map { CFix.aff(it.trackId, movable = true) }
        val world = CFix.world(objects = objects, affordances = affs)
        val cap = CFix.cap(movableCount = 2, nameableCount = 2, distinctColors = setOf(ColorTag.BLUE), spread = 0.6f)
        val first = reg.select(world, cap, CFix.ctx(), SelectionMode.RECOMMENDED)
        repeat(10) {
            val again = reg.select(world, cap, CFix.ctx(), SelectionMode.RECOMMENDED)
            assertEquals(first.winnerId, again.winnerId)
            assertEquals(first.spec.id, again.spec.id)
            assertEquals(first.spec.instruction, again.spec.instruction)
        }
    }

    @Test
    fun g1_beatsG0_whenFeasible() {
        val reg = registry()
        val objects = listOf(CFix.obj(1, cx = 0.2f), CFix.obj(2, cx = 0.8f))
        val affs = objects.map { CFix.aff(it.trackId, movable = true) }
        val world = CFix.world(objects = objects, affordances = affs)
        val cap = CFix.cap(movableCount = 2, nameableCount = 2, spread = 0.7f)
        val result = reg.select(world, cap, CFix.ctx(), SelectionMode.RECOMMENDED)
        assertEquals("G1", result.winnerId)
    }

    @Test
    fun prefilter_carriesExplicitReasons() {
        val reg = registry()
        // Only one movable object → G1 requirement (2 movable) unmet with a specific reason.
        val cap = CFix.cap(movableCount = 1)
        val world = CFix.world(objects = listOf(CFix.obj(1)), affordances = listOf(CFix.aff(1, movable = true)))
        val result = reg.select(world, cap, CFix.ctx(), SelectionMode.RECOMMENDED)
        val g1 = result.ranked.first { it.generatorId == "G1" }
        assertEquals(0f, g1.score, 0f)
        assertNotNull(g1.zeroReason)
        assertTrue(g1.zeroReason!!.contains("movable"))
    }

    @Test
    fun pinned_forcesGenerator_evenWhenNotArgmax() {
        val reg = registry()
        val objects = listOf(CFix.obj(1, cx = 0.2f), CFix.obj(2, cx = 0.8f))
        val affs = objects.map { CFix.aff(it.trackId, movable = true) }
        val world = CFix.world(objects = objects, affordances = affs)
        val cap = CFix.cap(movableCount = 2, nameableCount = 2, spread = 0.7f)
        val result = reg.select(world, cap, CFix.ctx(), SelectionMode.PINNED, pinnedGeneratorId = "G0")
        assertEquals("G0", result.winnerId)
    }

    @Test
    fun stepBudget_clampedToOne_forEarlyBand_evenAtHard() {
        val reg = registry()
        val objects = listOf(CFix.obj(1, cx = 0.1f), CFix.obj(2, cx = 0.9f))
        val affs = objects.map { CFix.aff(it.trackId, movable = true) }
        val world = CFix.world(objects = objects, affordances = affs)
        val cap = CFix.cap(movableCount = 2, nameableCount = 2, spread = 0.9f, richness = 0.9f)
        val ctx = CFix.ctx(ageBand = AgeBand.EARLY, tier = Tier.HARD)
        val result = reg.select(world, cap, ctx, SelectionMode.PINNED, pinnedGeneratorId = "G1")
        assertEquals("G1", result.winnerId)
        assertEquals(1, result.stepBudget)
    }
}
