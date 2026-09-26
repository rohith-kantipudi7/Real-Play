package com.cognex.realplay.ui.result

import androidx.compose.runtime.Composable
import com.cognex.realplay.ui.common.PlaceholderScreen

/** Placeholder — score, streak and per-challenge breakdown arrive in S6. */
@Composable
fun ResultScreen(onPlayAgain: () -> Unit, onHome: () -> Unit) {
    PlaceholderScreen(
        title = "Results",
        subtitle = "Score, best streak and measurements land in S6.",
        primaryLabel = "Play Again",
        onPrimary = onPlayAgain,
        secondaryLabel = "Home",
        onSecondary = onHome
    )
}
