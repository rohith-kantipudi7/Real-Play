package com.cognex.realplay.ui.present

import com.cognex.realplay.present.Overlay
import com.cognex.realplay.present.PassthroughLayer
import com.cognex.realplay.present.RenderModel
import com.cognex.realplay.present.SceneComposer
import com.cognex.realplay.present.SceneGraph
import com.cognex.realplay.present.ScenePerception
import com.cognex.realplay.present.Emphasis
import com.cognex.realplay.engine.GameUiState
import com.cognex.realplay.ui.overlay.OverlayCue
import com.cognex.realplay.world.ColorTag
import com.cognex.realplay.world.NormPoint
import com.cognex.realplay.world.NormRect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** JVM tests that the mobile target maps every render-model element (Architecture §3.6, §27). */
class MobileTargetTest {

    private val highlight = Overlay.Highlight(
        box = NormRect(0.1f, 0.2f, 0.3f, 0.4f),
        label = "#1 cup",
        color = ColorTag.RED
    )
    private val zone = Overlay.ZoneShape(
        polygon = listOf(NormPoint(0f, 0f), NormPoint(0.2f, 0f), NormPoint(0.2f, 0.2f)),
        color = ColorTag.BLUE,
        zoneId = "z1"
    )

    private fun model(vararg overlays: Overlay) = RenderModel(
        scene = SceneGraph(PassthroughLayer.CAMERA, overlays.toList()),
        hud = SceneComposer.EMPTY.hud
    )

    @Test
    fun `highlights map to detections with box, label and colour`() {
        val d = MobileTarget.detections(model(highlight)).single()
        assertEquals(0.1f, d.left, 0f)
        assertEquals(0.2f, d.top, 0f)
        assertEquals(0.3f, d.right, 0f)
        assertEquals(0.4f, d.bottom, 0f)
        assertEquals("#1 cup", d.label)
        assertEquals(MobileTarget.colorForTag(ColorTag.RED), d.color)
    }

    @Test
    fun `zone shapes map to zones with polygon and colour`() {
        val z = MobileTarget.zones(model(zone)).single()
        assertEquals(listOf(0f to 0f, 0.2f to 0f, 0.2f to 0.2f), z.polygon)
        assertEquals(MobileTarget.colorForTag(ColorTag.BLUE), z.color)
    }

    @Test
    fun `detections ignore non-highlight overlays and zones ignore non-zone overlays`() {
        val m = model(highlight, zone)
        assertEquals(1, MobileTarget.detections(m).size)
        assertEquals(1, MobileTarget.zones(m).size)
    }

    @Test
    fun `every highlight and zone in the model is mapped one-to-one`() {
        val m = model(
            highlight, zone,
            highlight.copy(label = "#2 ball", color = ColorTag.GREEN),
            zone.copy(zoneId = "z2", color = ColorTag.YELLOW)
        )
        assertEquals(2, MobileTarget.detections(m).size)
        assertEquals(2, MobileTarget.zones(m).size)
    }

    @Test
    fun `distinct colour tags map to distinct colours`() {
        assertNotEquals(MobileTarget.colorForTag(ColorTag.RED), MobileTarget.colorForTag(ColorTag.BLUE))
    }

    @Test
    fun `a fully composed model round-trips through the target`() {
        val perception = ScenePerception(listOf(highlight), listOf(zone), emptyList())
        val rm = SceneComposer.compose(GameUiState.INITIAL, perception)
        assertEquals(1, MobileTarget.detections(rm).size)
        assertEquals(1, MobileTarget.zones(rm).size)
        assertTrue(rm.hud.instruction.isNotEmpty())
    }

    @Test
    fun `coaching cues map to animated overlay cues and are read only from the cue track`() {
        val cueHighlight = highlight.copy(emphasis = Emphasis.TARGET)
        val arrow = Overlay.PathArrow(NormPoint(0f, 0f), NormPoint(1f, 1f), ColorTag.CYAN)
        val glyph = Overlay.Pictograph(NormRect(0.4f, 0.05f, 0.6f, 0.23f), "\uD83C\uDFAF")
        val rm = RenderModel(
            scene = SceneGraph(PassthroughLayer.CAMERA, listOf(highlight, zone)), // perception only
            hud = SceneComposer.EMPTY.hud,
            cues = listOf(cueHighlight, arrow, glyph)
        )
        val cues = MobileTarget.cues(rm)
        assertEquals(3, cues.size)
        val pulse = cues[0] as OverlayCue.PulseBox
        assertTrue(pulse.strong)
        assertTrue(cues[1] is OverlayCue.Arrow)
        assertEquals("\uD83C\uDFAF", (cues[2] as OverlayCue.Glyph).text)

        // Perception overlays in the scene are NOT drawn as cues (no double-draw).
        assertEquals(1, MobileTarget.detections(rm).size)
        assertEquals(1, MobileTarget.zones(rm).size)
    }
}
