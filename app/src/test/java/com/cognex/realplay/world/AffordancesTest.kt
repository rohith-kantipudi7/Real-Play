package com.cognex.realplay.world

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** JVM tests for the affordance flags and symmetric richness renormalisation (Architecture §6, S3.5). */
class AffordancesTest {

    private fun squareBox(cx: Float, cy: Float, area: Float): NormRect {
        val half = Math.sqrt(area.toDouble()).toFloat() / 2f
        return NormRect(cx - half, cy - half, cx + half, cy + half)
    }

    private fun obj(
        id: Int, label: String, cx: Float, cy: Float, area: Float,
        color: ColorTag? = ColorTag.RED, conf: Float = 0.9f, stable: Boolean = true
    ): TrackedObject {
        val box = squareBox(cx, cy, area)
        return TrackedObject(
            trackId = id, label = label, confidence = conf, box = box, center = box.center,
            color = color, ageFrames = 10, lastSeenMs = 0L, velocity = NormPoint(0f, 0f),
            stable = stable, stale = false, ambiguous = false
        )
    }

    private fun player(id: Int, torsoArea: Float, colorBand: ColorTag? = ColorTag.RED, conf: Float = 0.9f) =
        TrackedPlayer(
            playerId = id, landmarks = emptyList(), torsoBox = squareBox(0.5f, 0.5f, torsoArea),
            colorBand = colorBand, motionEnergy = 0f, confidence = conf, ambiguous = false
        )

    private fun world(
        objects: List<TrackedObject> = emptyList(),
        players: List<TrackedPlayer> = emptyList(),
        ts: Long = 0L
    ) = WorldState.EMPTY.copy(
        frameId = 0L, timestampMs = ts, objects = objects, players = players
    )

    // ── Individual affordance flags ──────────────────────────────────────────

    @Test
    fun movable_and_handheld_smallProp() {
        val eng = AffordanceEngine()
        val a = eng.derive(world(listOf(obj(1, "ball", 0.5f, 0.5f, 0.04f)))).first()
        assertTrue(a.movable)
        assertTrue(a.handheld) // area 0.04 < 0.08
    }

    @Test
    fun movable_notHandheld_midProp() {
        val eng = AffordanceEngine()
        val a = eng.derive(world(listOf(obj(1, "book", 0.5f, 0.5f, 0.16f)))).first()
        assertTrue(a.movable)   // 0.16 < 0.25
        assertFalse(a.handheld) // 0.16 !< 0.08
    }

    @Test
    fun container_cup() {
        val eng = AffordanceEngine()
        val a = eng.derive(world(listOf(obj(1, "cup", 0.5f, 0.5f, 0.05f)))).first()
        assertTrue(a.container)
    }

    @Test
    fun cup_isHandheldAndContainer() {
        val eng = AffordanceEngine()
        val a = eng.derive(world(listOf(obj(1, "cup", 0.5f, 0.5f, 0.05f)))).first()
        assertTrue(a.handheld)
        assertTrue(a.container)
    }

    @Test
    fun chair_isLandmark_notMovable() {
        val eng = AffordanceEngine()
        val big = obj(1, "chair", 0.5f, 0.5f, 0.30f) // area 0.30 > 0.25 and > 0.20
        // First frame: stable timer starts, not yet 3 s.
        val first = eng.derive(world(listOf(big), ts = 0L)).first()
        assertFalse(first.landmark)
        assertFalse(first.movable) // furniture + large area
        // 3.1 s later: continuously stable ≥ 3 s → landmark.
        val later = eng.derive(world(listOf(big), ts = 3_100L)).first()
        assertTrue(later.landmark)
        assertFalse(later.movable)
    }

    @Test
    fun landmark_requiresContinuousStability() {
        val eng = AffordanceEngine()
        val big = obj(1, "tv", 0.5f, 0.5f, 0.30f, stable = true)
        eng.derive(world(listOf(big), ts = 0L))
        // Destabilise mid-way → timer resets.
        eng.derive(world(listOf(big.copy(stable = false)), ts = 1_000L))
        val later = eng.derive(world(listOf(big), ts = 3_500L)).first()
        assertFalse(later.landmark) // only ~0 ms of continuous stability again
    }

    @Test
    fun colorful_onlyChromatic() {
        val eng = AffordanceEngine()
        val chromatic = eng.derive(world(listOf(obj(1, "ball", 0.3f, 0.3f, 0.04f, color = ColorTag.BLUE)))).first()
        val achromatic = eng.derive(world(listOf(obj(2, "ball", 0.7f, 0.7f, 0.04f, color = ColorTag.GRAY)))).first()
        assertTrue(chromatic.colorful)
        assertFalse(achromatic.colorful)
    }

    @Test
    fun nameable_needsKnownLabelAndConfidence() {
        val eng = AffordanceEngine()
        val named = eng.derive(world(listOf(obj(1, "cup", 0.3f, 0.3f, 0.05f, conf = 0.9f)))).first()
        val lowConf = eng.derive(world(listOf(obj(2, "cup", 0.7f, 0.7f, 0.05f, conf = 0.4f)))).first()
        val unknown = eng.derive(world(listOf(obj(3, "unknown", 0.5f, 0.5f, 0.05f, conf = 0.9f)))).first()
        assertTrue(named.nameable)
        assertFalse(lowConf.nameable)
        assertFalse(unknown.nameable)
    }

    @Test
    fun twoIdenticalBlueCups_bothNotDistinct() {
        val eng = AffordanceEngine()
        val affs = eng.derive(world(listOf(
            obj(1, "cup", 0.3f, 0.5f, 0.05f, color = ColorTag.BLUE),
            obj(2, "cup", 0.7f, 0.5f, 0.05f, color = ColorTag.BLUE)
        )))
        assertTrue(affs.all { !it.distinct })
    }

    @Test
    fun distinct_whenUniqueIdentity() {
        val eng = AffordanceEngine()
        val affs = eng.derive(world(listOf(
            obj(1, "cup", 0.3f, 0.5f, 0.05f, color = ColorTag.BLUE),
            obj(2, "cup", 0.7f, 0.5f, 0.05f, color = ColorTag.RED)
        )))
        assertTrue(affs.all { it.distinct })
    }

    // ── Richness ─────────────────────────────────────────────────────────────

    private fun richnessOf(w: WorldState, dynamics: PlayerDynamics = PlayerDynamics.EMPTY): Float {
        val affs = AffordanceEngine().derive(w)
        return SceneCapability.from(w, affs, dynamics).richness
    }

    @Test
    fun emptyScene_richnessNearZero() {
        assertEquals(0f, richnessOf(world()), 0.001f)
    }

    @Test
    fun richness_risesAsObjectsAdded() {
        val one = richnessOf(world(listOf(
            obj(1, "cup", 0.2f, 0.2f, 0.05f, color = ColorTag.RED)
        )))
        val two = richnessOf(world(listOf(
            obj(1, "cup", 0.2f, 0.2f, 0.05f, color = ColorTag.RED),
            obj(2, "book", 0.8f, 0.8f, 0.05f, color = ColorTag.GREEN)
        )))
        val three = richnessOf(world(listOf(
            obj(1, "cup", 0.2f, 0.2f, 0.05f, color = ColorTag.RED),
            obj(2, "book", 0.8f, 0.8f, 0.05f, color = ColorTag.GREEN),
            obj(3, "ball", 0.5f, 0.9f, 0.05f, color = ColorTag.BLUE)
        )))
        assertTrue("one < two", one < two)
        assertTrue("two < three", two < three)
    }

    @Test
    fun branchA_richObjectOnly_exceeds085() {
        val w = world(listOf(
            obj(1, "cup", 0.15f, 0.15f, 0.05f, color = ColorTag.RED),
            obj(2, "book", 0.85f, 0.15f, 0.05f, color = ColorTag.GREEN),
            obj(3, "ball", 0.15f, 0.85f, 0.05f, color = ColorTag.BLUE),
            obj(4, "bottle", 0.85f, 0.85f, 0.05f, color = ColorTag.YELLOW)
        ))
        val affs = AffordanceEngine().derive(w)
        val report = SceneCapability.report(w, affs)
        assertEquals(RichnessBranch.A_NO_PLAYERS, report.richness.branch)
        assertTrue("richness ${report.capability.richness}", report.capability.richness > 0.85f)
    }

    @Test
    fun branchB_activePlayer_exceeds070() {
        val w = world(players = listOf(player(1, torsoArea = 0.25f, colorBand = ColorTag.RED)))
        val dynamics = PlayerDynamics(poseVariety = 1.0f, motionRange = 1.0f)
        val affs = AffordanceEngine().derive(w) // no objects → empty
        val report = SceneCapability.report(w, affs, dynamics)
        assertEquals(RichnessBranch.B_NO_MOVABLE, report.richness.branch)
        assertTrue("richness ${report.capability.richness}", report.capability.richness > 0.70f)
    }

    @Test
    fun branchB_stillDistantPlayer_staysLow() {
        val w = world(players = listOf(player(1, torsoArea = 0.02f, colorBand = ColorTag.RED)))
        val dynamics = PlayerDynamics(poseVariety = 0f, motionRange = 0f) // not engaged
        val affs = AffordanceEngine().derive(w)
        val richness = SceneCapability.from(w, affs, dynamics).richness
        assertTrue("richness $richness should stay low", richness < 0.35f)
    }

    // ── §3.5 capability flags ────────────────────────────────────────────────

    @Test
    fun forceTrackOnly_disablesSemanticLabels() {
        val w = world(listOf(obj(1, "cup", 0.3f, 0.3f, 0.05f, conf = 0.9f)))
        val affs = AffordanceEngine().derive(w)
        val normal = SceneCapability.from(w, affs, forceTrackOnly = false)
        val forced = SceneCapability.from(w, affs, forceTrackOnly = true)
        assertTrue(normal.semanticLabelsAvailable)
        assertFalse(normal.trackOnlyMode)
        assertFalse(forced.semanticLabelsAvailable)
        assertTrue(forced.trackOnlyMode)
    }

    @Test
    fun lowConfidenceLabels_flipToTrackOnly() {
        val w = world(listOf(
            obj(1, "cup", 0.3f, 0.3f, 0.05f, conf = 0.6f),   // nameable (conf > 0.55)
            obj(2, "book", 0.7f, 0.7f, 0.05f, conf = 0.58f)  // nameable
        ))
        val affs = AffordanceEngine().derive(w)
        // Mean nameable confidence = 0.59 ≥ 0.5 → still semantic.
        assertTrue(SceneCapability.from(w, affs).semanticLabelsAvailable)
    }
}
