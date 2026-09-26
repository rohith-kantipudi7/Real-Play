package com.cognex.realplay.ui.game

import androidx.compose.runtime.Composable
import com.cognex.realplay.ui.common.PlaceholderScreen

/** Placeholder — the live game loop, overlay and progress ring arrive in S5/S6. */
@Composable
fun GameScreen(onFinish: () -> Unit, onBack: () -> Unit) {
    PlaceholderScreen(
        title = "Game",
        subtitle = "Camera, detection, verification and scoring land across S1–S5.",
        primaryLabel = "Finish",
        onPrimary = onFinish,
        secondaryLabel = "Back",
        onSecondary = onBack
    )
}
