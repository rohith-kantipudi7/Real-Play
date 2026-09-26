package com.cognex.realplay.ui.party

import androidx.compose.ui.graphics.Color
import com.cognex.realplay.world.ColorTag

/** Fixed colour-band palette for PARTY entrant identity (Architecture §5, §25) — reused, no new tracking. */
internal val PARTY_PALETTE: List<ColorTag> = listOf(
    ColorTag.RED, ColorTag.BLUE, ColorTag.GREEN, ColorTag.YELLOW,
    ColorTag.PURPLE, ColorTag.ORANGE, ColorTag.PINK, ColorTag.CYAN
)

internal fun ColorTag.toComposeColor(): Color = when (this) {
    ColorTag.RED -> Color(0xFFF87171)
    ColorTag.ORANGE -> Color(0xFFFB923C)
    ColorTag.YELLOW -> Color(0xFFFDE047)
    ColorTag.GREEN -> Color(0xFF4ADE80)
    ColorTag.CYAN -> Color(0xFF22D3EE)
    ColorTag.BLUE -> Color(0xFF60A5FA)
    ColorTag.PURPLE -> Color(0xFFA78BFA)
    ColorTag.PINK -> Color(0xFFF472B6)
    ColorTag.WHITE -> Color(0xFFF1F5F9)
    ColorTag.GRAY -> Color(0xFF94A3B8)
    ColorTag.BLACK -> Color(0xFF334155)
    ColorTag.UNKNOWN -> Color(0xFF22D3EE)
}
