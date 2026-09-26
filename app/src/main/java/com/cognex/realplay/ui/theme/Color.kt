package com.cognex.realplay.ui.theme

import androidx.compose.ui.graphics.Color

// ─────────────────────────────────────────────────────────────────────────────
// RealPlay palette v2 — deeper, richer canvas with a single confident accent
// pair instead of many competing saturated colours (§13 UX). Dark base keeps
// the UI legible over the live camera feed. Names are stable across the app —
// only the values changed.
// ─────────────────────────────────────────────────────────────────────────────

// Base surfaces — near-black indigo, not flat navy, for real contrast depth.
val RpNavyDeep = Color(0xFF07061A)      // gradient floor / darkest corners
val RpNavy = Color(0xFF0E0C24)          // background / base surface
val RpNavyElevated = Color(0xFF1B1838)  // elevated cards, chips, panels
val RpNavyElevated2 = Color(0xFF241F49) // a second, lighter elevation step

// Primary — a confident teal, dialled back from neon so it reads as premium.
val RpCyan = Color(0xFF2DD4BF)
val RpCyanDim = Color(0xFF0F766E)

// Secondary — warm amber, used sparingly as a genuine accent, not a second primary.
val RpAmber = Color(0xFFF5A524)
val RpAmberDim = Color(0xFFB5790E)

// Foreground — near-white with a faint violet tint for a softer, playful feel.
val RpOnDark = Color(0xFFF3EEFF)
val RpOnDarkMuted = Color(0xFFA79FC7)

// Feedback — clear, friendly, never harsh.
val RpError = Color(0xFFFF5C7A)         // coral-red: readable but not scary
val RpSuccess = Color(0xFF57E39B)       // mint-green: pass / positive

// Extra playful accents (available for HUD, bursts, stat cards, overlays).
val RpViolet = Color(0xFF9B6BFF)        // tertiary accent
val RpPink = Color(0xFFFF5CA8)
val RpLime = Color(0xFFB6F35C)
