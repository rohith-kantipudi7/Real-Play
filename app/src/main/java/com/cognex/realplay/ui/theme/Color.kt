package com.cognex.realplay.ui.theme

import androidx.compose.ui.graphics.Color

// ─────────────────────────────────────────────────────────────────────────────
// RealPlay palette — "modern playful": a deep indigo canvas with vibrant, high-
// contrast, kid-friendly accents. Dark base keeps the UI legible over the live
// camera feed; saturated primaries pop for young players (§S6, §19 quality bar).
// Names are stable across the app — only the values changed from the old navy set.
// ─────────────────────────────────────────────────────────────────────────────

// Base surfaces — deep indigo/violet, not flat black, so cards feel warm.
val RpNavy = Color(0xFF161033)          // background / base surface
val RpNavyElevated = Color(0xFF241A4D)  // elevated cards, chips, panels

// Primary — vibrant turquoise. Fresh, energetic, high contrast on the dark base.
val RpCyan = Color(0xFF25E0C8)
val RpCyanDim = Color(0xFF12897E)

// Secondary — sunny tangerine. Warm, celebratory, pairs with the turquoise.
val RpAmber = Color(0xFFFFB13A)
val RpAmberDim = Color(0xFFB86E12)

// Foreground — near-white with a faint violet tint for a softer, playful feel.
val RpOnDark = Color(0xFFF3EEFF)
val RpOnDarkMuted = Color(0xFFB4A7DE)

// Feedback — clear, friendly, never harsh.
val RpError = Color(0xFFFF5C7A)         // coral-red: readable but not scary
val RpSuccess = Color(0xFF57E39B)       // mint-green: pass / positive

// Extra playful accents (available for HUD, bursts, stat cards, overlays).
val RpViolet = Color(0xFF9B6BFF)        // tertiary accent
val RpPink = Color(0xFFFF5CA8)
val RpLime = Color(0xFFB6F35C)
