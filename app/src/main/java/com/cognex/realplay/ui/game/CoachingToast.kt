package com.cognex.realplay.ui.game

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * The coaching toast for an Unsure outcome (Architecture §13 S6, §8). A deliberately CALM blue —
 * never red, never anything that reads as failure — because Unsure means "I can't tell yet, let me
 * help", not "you failed". Fades in/out so it never jars.
 */
@Composable
fun CoachingToast(
    hint: String?,
    visible: Boolean,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = visible && hint != null,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = modifier
    ) {
        Text(
            text = hint.orEmpty(),
            color = Color(0xFFBAE6FD),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Medium,
            modifier = Modifier
                .background(Color(0xCC0C4A6E), RoundedCornerShape(14.dp))
                .border(1.dp, Color(0x66BAE6FD), RoundedCornerShape(14.dp))
                .padding(horizontal = 18.dp, vertical = 12.dp)
        )
    }
}
