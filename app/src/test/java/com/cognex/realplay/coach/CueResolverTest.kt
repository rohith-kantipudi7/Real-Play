package com.cognex.realplay.coach

import com.cognex.realplay.challenge.CFix
import com.cognex.realplay.present.Emphasis
import com.cognex.realplay.present.Overlay
import com.cognex.realplay.world.ColorTag
import com.cognex.realplay.world.NormPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** JVM tests for resolving semantic cues into drawable overlays (Architecture §26). */
class CueResolverTest {

    @Test
    fun `strong highlight resolves to a TARGET overlay with the live box and colour`() {
        val obj = CFix.obj(trackId = 5, cx = 0.3f, cy = 0.3f, color = ColorTag.RED)
        val world = CFix.world(objects = listOf(obj))
        val out = CueResolver.resolve(listOf(VisualCue.Highlight(5, Pulse.STRONG)), world)
        val h = out.single() as Overlay.Highlight
        assertEquals(5, h.trackId)
        assertEquals(Emphasis.TARGET, h.emphasis)
        assertEquals(ColorTag.RED, h.color)
        assertEquals(obj.box, h.box)
    }

    @Test
    fun `gentle highlight resolves to SECONDARY emphasis`() {
        val world = CFix.world(objects = listOf(CFix.obj(trackId = 1)))
        val h = CueResolver.resolve(listOf(VisualCue.Highlight(1, Pulse.GENTLE)), world)
            .single() as Overlay.Highlight
        assertEquals(Emphasis.SECONDARY, h.emphasis)
    }

    @Test
    fun `an unresolvable highlight or zone is dropped`() {
        val world = CFix.world(objects = emptyList(), zones = emptyList())
        val out = CueResolver.resolve(
            listOf(VisualCue.Highlight(9, Pulse.STRONG), VisualCue.ZonePulse("zX")),
            world
        )
        assertTrue(out.isEmpty())
    }

    @Test
    fun `path arrow resolves to a cyan overlay arrow with the same endpoints`() {
        val out = CueResolver.resolve(
            listOf(VisualCue.PathArrow(NormPoint(0.1f, 0.2f), NormPoint(0.8f, 0.9f))),
            CFix.world()
        )
        val a = out.single() as Overlay.PathArrow
        assertEquals(NormPoint(0.1f, 0.2f), a.from)
        assertEquals(NormPoint(0.8f, 0.9f), a.to)
        assertEquals(ColorTag.CYAN, a.color)
    }

    @Test
    fun `zone pulse resolves to the live zone polygon`() {
        val zone = CFix.zone(zoneId = "zA", color = ColorTag.BLUE)
        val world = CFix.world(zones = listOf(zone))
        val z = CueResolver.resolve(listOf(VisualCue.ZonePulse("zA")), world)
            .single() as Overlay.ZoneShape
        assertEquals("zA", z.zoneId)
        assertEquals(ColorTag.BLUE, z.color)
        assertEquals(zone.polygon, z.polygon)
    }

    @Test
    fun `colour swatch and target pictographs resolve to language-free glyphs`() {
        val green = CueResolver.resolve(
            listOf(VisualCue.Pictograph(Symbol.ColorSwatch(ColorTag.GREEN))), CFix.world()
        ).single() as Overlay.Pictograph
        assertEquals("\uD83D\uDFE2", green.glyph) // 🟢

        val target = CueResolver.resolve(
            listOf(VisualCue.Pictograph(Symbol.Target)), CFix.world()
        ).single() as Overlay.Pictograph
        assertEquals("\uD83C\uDFAF", target.glyph) // 🎯
    }

    @Test
    fun `ghost demo resolves to a ghost overlay carrying the demo kind`() {
        val g = CueResolver.resolve(
            listOf(VisualCue.GhostDemo(DemoKind.RAISE_LIMB)), CFix.world()
        ).single() as Overlay.Ghost
        assertEquals(DemoKind.RAISE_LIMB.name, g.kind)
    }

    @Test
    fun `resolution preserves cue order`() {
        val world = CFix.world(objects = listOf(CFix.obj(trackId = 1)), zones = listOf(CFix.zone(zoneId = "zA")))
        val out = CueResolver.resolve(
            listOf(
                VisualCue.ZonePulse("zA"),
                VisualCue.Highlight(1, Pulse.STRONG),
                VisualCue.PathArrow(NormPoint(0f, 0f), NormPoint(1f, 1f))
            ),
            world
        )
        assertTrue(out[0] is Overlay.ZoneShape)
        assertTrue(out[1] is Overlay.Highlight)
        assertTrue(out[2] is Overlay.PathArrow)
    }
}
