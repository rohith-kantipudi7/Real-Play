package com.cognex.realplay.coach

import com.cognex.realplay.present.Emphasis
import com.cognex.realplay.present.Overlay
import com.cognex.realplay.world.ColorTag
import com.cognex.realplay.world.NormRect
import com.cognex.realplay.world.WorldState

/**
 * Resolves the semantic [VisualCue] track into drawable [Overlay] cues (Architecture §26, §27).
 * Pure JVM — turns "highlight track 7" into an actual box from the live [WorldState], "arrow" into
 * coordinates, a colour symbol into a language-free glyph. The result is the [ ][com.cognex.realplay.present.RenderModel.cues]
 * track a presentation target draws on top of the scene; it never changes a verdict (§20 invariant 23).
 *
 * A cue whose actor no longer resolves this frame is dropped — never a crash, never a stale box.
 */
object CueResolver {

    /** A fixed top-centre placard for a goal pictograph, in NORMALIZED space. */
    private val PICTOGRAPH_BOX = NormRect(0.40f, 0.05f, 0.60f, 0.23f)

    /** A centred stage for a looping ghost demonstration (pose games, S8). */
    private val GHOST_BOX = NormRect(0.34f, 0.28f, 0.66f, 0.82f)

    /** The accent colour for coaching routes. */
    private val ARROW_COLOR = ColorTag.CYAN

    fun resolve(cues: List<VisualCue>, world: WorldState): List<Overlay> = cues.mapNotNull { cue ->
        when (cue) {
            is VisualCue.Highlight -> {
                val obj = world.objects.firstOrNull { it.trackId == cue.trackId } ?: return@mapNotNull null
                Overlay.Highlight(
                    box = obj.box,
                    label = null,
                    color = obj.color,
                    emphasis = if (cue.style == Pulse.STRONG) Emphasis.TARGET else Emphasis.SECONDARY,
                    trackId = obj.trackId
                )
            }

            is VisualCue.PathArrow -> Overlay.PathArrow(cue.from, cue.to, ARROW_COLOR)

            is VisualCue.ZonePulse -> {
                val zone = world.zones.firstOrNull { it.zoneId == cue.zoneId } ?: return@mapNotNull null
                Overlay.ZoneShape(zone.polygon, zone.color, zone.zoneId)
            }

            is VisualCue.Pictograph -> Overlay.Pictograph(PICTOGRAPH_BOX, glyphFor(cue.symbol))

            is VisualCue.GhostDemo -> Overlay.Ghost(GHOST_BOX, cue.kind.name)
        }
    }

    /** Language-free glyph for a goal symbol. Colour swatches use colour emoji (self-colouring). */
    private fun glyphFor(symbol: Symbol): String = when (symbol) {
        is Symbol.Target -> "\uD83C\uDFAF" // 🎯
        is Symbol.Shape -> when (symbol.kind) {
            ShapeKind.CIRCLE -> "\u2B24"    // ⬤
            ShapeKind.SQUARE -> "\u2B1B"    // ⬛
            ShapeKind.TRIANGLE -> "\uD83D\uDD3A" // 🔺
        }
        is Symbol.ColorSwatch -> when (symbol.color) {
            ColorTag.RED -> "\uD83D\uDD34"    // 🔴
            ColorTag.ORANGE -> "\uD83D\uDFE0" // 🟠
            ColorTag.YELLOW -> "\uD83D\uDFE1" // 🟡
            ColorTag.GREEN -> "\uD83D\uDFE2"  // 🟢
            ColorTag.CYAN -> "\uD83D\uDD35"   // 🔵 (nearest solid)
            ColorTag.BLUE -> "\uD83D\uDD35"   // 🔵
            ColorTag.PURPLE -> "\uD83D\uDFE3" // 🟣
            ColorTag.PINK -> "\uD83D\uDFE3"   // 🟣 (nearest)
            ColorTag.WHITE -> "\u26AA"        // ⚪
            ColorTag.GRAY -> "\u26AA"         // ⚪
            ColorTag.BLACK -> "\u26AB"        // ⚫
            else -> "\uD83C\uDFAF"            // 🎯 fallback
        }
    }
}
