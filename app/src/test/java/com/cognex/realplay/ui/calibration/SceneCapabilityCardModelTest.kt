package com.cognex.realplay.ui.calibration

import com.cognex.realplay.challenge.CFix
import com.cognex.realplay.challenge.ChallengeRegistry
import com.cognex.realplay.challenge.ChallengeType
import com.cognex.realplay.world.ColorTag
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** SceneCapabilityCard model arithmetic (Architecture §15, invariant 14). */
class SceneCapabilityCardModelTest {

    private val registry = ChallengeRegistry.default()

    @Test
    fun denominator_isTheLiveRegistrySize_neverHardCoded() {
        val cap = CFix.cap(movableCount = 4, containerCount = 1, distinctColors = setOf(ColorTag.RED, ColorTag.BLUE, ColorTag.GREEN), richness = 0.7f)
        val model = SceneCapabilityCardModel.from(cap, CFix.ctx(), registry)
        assertEquals(registry.generators.size, model.total)

        // A registry with a DIFFERENT generator count must move the denominator with it — proving it
        // is read from the live registry, not a literal.
        val smaller = ChallengeRegistry(
            listOf(com.cognex.realplay.challenge.generators.G0LastResortGenerator())
        )
        assertEquals(1, SceneCapabilityCardModel.from(cap, CFix.ctx(), smaller).total)
    }

    @Test
    fun possible_countsOnlyFeasibleGames_andOmitsZeroRows() {
        // 4 movable, 1 zone, 3 colours → G0,G1,G2,G3 all feasible = 4 possible.
        val cap = CFix.cap(
            movableCount = 4, handheldCount = 2, zoneCount = 1,
            distinctColors = setOf(ColorTag.RED, ColorTag.BLUE, ColorTag.GREEN),
            spread = 0.6f, richness = 0.7f
        )
        val model = SceneCapabilityCardModel.from(cap, CFix.ctx(), registry)
        assertEquals(4, model.possible)
        assertEquals(ChallengeType.DROP_ZONE, model.playingType)   // G2 (0.9 with a zone) wins
        // No zero-valued rows (landmark etc. never appear here).
        assertTrue(model.rows.all { it.value > 0 })
        assertEquals(3, model.rows.size)                            // move / container / colours
    }

    @Test
    fun sparseScene_setsTheBareBranch() {
        val cap = CFix.cap(movableCount = 1, distinctColors = setOf(ColorTag.RED), richness = 0.2f)
        val model = SceneCapabilityCardModel.from(cap, CFix.ctx(), registry)
        assertTrue(model.sparse)
    }

    @Test
    fun humanOnlyScene_showsPeopleRows_notObjectRows() {
        val cap = CFix.cap(movableCount = 0, playerCount = 1, richness = 0.5f)
        val model = SceneCapabilityCardModel.from(cap, CFix.ctx(), registry)
        assertTrue(model.humanOnly)
        assertTrue(model.rows.any { it.label.contains("person") || it.label.contains("people") })
        assertFalse(model.rows.any { it.label.contains("things you can move") })
    }
}
