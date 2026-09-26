package com.cognex.realplay.present

import com.cognex.realplay.engine.GameUiState
import com.cognex.realplay.engine.PlayStatus
import com.cognex.realplay.world.ColorTag
import com.cognex.realplay.world.NormPoint
import com.cognex.realplay.world.NormRect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/** JVM tests for the pure GameUiState + perception → RenderModel composition (Architecture §3.6, §27). */
class SceneComposerTest {

    private val highlight = Overlay.Highlight(
        box = NormRect.fromCenter(0.5f, 0.5f, 0.2f, 0.2f),
        label = "#1 cup",
        color = ColorTag.RED
    )
    private val zone = Overlay.ZoneShape(
        polygon = listOf(NormPoint(0f, 0f), NormPoint(0.2f, 0f), NormPoint(0.2f, 0.2f)),
        color = ColorTag.BLUE,
        zoneId = "z1"
    )
    private val skeleton = Overlay.Skeleton(
        joints = listOf(NormPoint(0.5f, 0.5f)),
        connections = emptyList()
    )
    private val cue = Overlay.PathArrow(NormPoint(0f, 0f), NormPoint(1f, 1f), ColorTag.GREEN)

    private val perception = ScenePerception(
        highlights = listOf(highlight),
        zones = listOf(zone),
        skeletons = listOf(skeleton)
    )

    @Test
    fun `all HUD fields map straight from the ui state`() {
        val ui = GameUiState.INITIAL.copy(
            status = PlayStatus.PLAYING,
            instruction = "Put the red cup in the blue zone",
            stepIndex = 2,
            stepCount = 4,
            completedSteps = 2,
            stepProgress = 0.5f,
            score = 120,
            streak = 3,
            challengeIndex = 5,
            lastGain = 20,
            perfect = true,
            coachingHint = "Move it left",
            timeRemainingMs = 4200L,
            timeFraction = 0.7f,
            retriesLeft = 1
        )
        val hud = SceneComposer.compose(ui, ScenePerception.EMPTY).hud
        assertEquals(PlayStatus.PLAYING, hud.status)
        assertEquals("Put the red cup in the blue zone", hud.instruction)
        assertEquals(2, hud.stepIndex)
        assertEquals(4, hud.stepCount)
        assertEquals(2, hud.completedSteps)
        assertEquals(0.5f, hud.stepProgress, 0f)
        assertEquals(120, hud.score)
        assertEquals(3, hud.streak)
        assertEquals(5, hud.challengeIndex)
        assertEquals(20, hud.lastGain)
        assertTrue(hud.perfect)
        assertEquals("Move it left", hud.coachingHint)
        assertEquals(4200L, hud.timeRemainingMs)
        assertEquals(0.7f, hud.timeFraction!!, 0f)
        assertEquals(1, hud.retriesLeft)
    }

    @Test
    fun `overlays are ordered back-to-front zones, skeletons, highlights, cues`() {
        val model = SceneComposer.compose(GameUiState.INITIAL, perception, cues = listOf(cue))
        assertEquals(listOf(zone, skeleton, highlight, cue), model.scene.overlays)
    }

    @Test
    fun `scene uses the camera passthrough and cues are carried through`() {
        val model = SceneComposer.compose(GameUiState.INITIAL, perception, cues = listOf(cue))
        assertEquals(PassthroughLayer.CAMERA, model.scene.passthrough)
        assertEquals(listOf<Overlay>(cue), model.cues)
    }

    @Test
    fun `no cues yields no cue overlays`() {
        val model = SceneComposer.compose(GameUiState.INITIAL, perception)
        assertTrue(model.cues.isEmpty())
        assertEquals(listOf(zone, skeleton, highlight), model.scene.overlays)
    }

    @Test
    fun `EMPTY is an empty scene over the camera`() {
        assertTrue(SceneComposer.EMPTY.scene.overlays.isEmpty())
        assertEquals(PassthroughLayer.CAMERA, SceneComposer.EMPTY.scene.passthrough)
    }
}
