package com.cognex.realplay.ui.modeselect

import androidx.compose.runtime.Composable
import com.cognex.realplay.ui.common.PlaceholderScreen

/** Placeholder — real mode/age/player selection arrives in S6. */
@Composable
fun ModeSelectScreen(onContinue: () -> Unit, onBack: () -> Unit) {
    PlaceholderScreen(
        title = "Mode Select",
        subtitle = "Objects / Body / Mixed, age band and players land in S6.",
        primaryLabel = "Continue",
        onPrimary = onContinue,
        secondaryLabel = "Back",
        onSecondary = onBack
    )
}
