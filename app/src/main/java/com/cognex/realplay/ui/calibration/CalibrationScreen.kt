package com.cognex.realplay.ui.calibration

import androidx.compose.runtime.Composable
import com.cognex.realplay.ui.common.PlaceholderScreen

/** Placeholder — live framing/lighting coaching and the calibration rect arrive in S1/S6. */
@Composable
fun CalibrationScreen(onReady: () -> Unit, onBack: () -> Unit) {
    PlaceholderScreen(
        title = "Calibration",
        subtitle = "Camera preview, framing and lighting checks land in S1.",
        primaryLabel = "Ready",
        onPrimary = onReady,
        secondaryLabel = "Back",
        onSecondary = onBack
    )
}
