package com.cognex.realplay.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val RealPlayColorScheme = darkColorScheme(
    primary = RpCyan,
    onPrimary = RpNavy,
    primaryContainer = RpCyanDim,
    onPrimaryContainer = RpOnDark,
    secondary = RpAmber,
    onSecondary = RpNavy,
    secondaryContainer = RpAmberDim,
    onSecondaryContainer = RpOnDark,
    background = RpNavy,
    onBackground = RpOnDark,
    surface = RpNavy,
    onSurface = RpOnDark,
    surfaceVariant = RpNavyElevated,
    onSurfaceVariant = RpOnDarkMuted,
    error = RpError,
    onError = RpNavy
)

/**
 * App theme. RealPlay is always dark — the live camera feed is the backdrop, so a dark
 * chrome keeps the UI legible over it. The [darkTheme] flag is accepted for API symmetry.
 */
@Composable
fun RealPlayTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = RealPlayColorScheme,
        typography = RealPlayTypography,
        content = content
    )
}
