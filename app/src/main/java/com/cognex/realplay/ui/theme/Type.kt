package com.cognex.realplay.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// Kept close to Material3 defaults; the display styles are tuned for arm's-length reading.
val RealPlayTypography = Typography(
    displayLarge = TextStyle(fontWeight = FontWeight.Bold, fontSize = 48.sp, letterSpacing = 2.sp),
    headlineMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 28.sp),
    titleLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 22.sp),
    bodyLarge = TextStyle(fontWeight = FontWeight.Normal, fontSize = 16.sp),
    labelLarge = TextStyle(fontWeight = FontWeight.Bold, fontSize = 16.sp, letterSpacing = 1.sp)
)
