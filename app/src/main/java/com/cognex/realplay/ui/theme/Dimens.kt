package com.cognex.realplay.ui.theme

import androidx.compose.ui.unit.dp

/** Spacing scale (Architecture §13 UX) — one source of truth instead of scattered `.dp` literals. */
object RpSpace {
    val xs = 4.dp
    val sm = 8.dp
    val md = 16.dp
    val lg = 24.dp
    val xl = 32.dp
}

/** Corner-radius scale, paired with [RpSpace] across every card/chip/button. */
object RpRadius {
    val sm = 10.dp
    val md = 14.dp
    val lg = 16.dp
    val xl = 20.dp
}
