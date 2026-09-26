package com.cognex.realplay.ui.game

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * The top status bar (Architecture §13 S6): score (counting up), a streak flame that grows with the
 * streak, the challenge counter, and a time bar that appears ONLY when the current challenge has a
 * limit. Readable at arm's length — big weights, high contrast.
 */
@Composable
fun HudBar(
    score: Int,
    streak: Int,
    challengeIndex: Int,
    timeFraction: Float?,
    timeRemainingMs: Long?,
    modifier: Modifier = Modifier
) {
    val animatedScore by animateIntAsState(targetValue = score, label = "score")

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Color(0xB3000000), RoundedCornerShape(bottomStartPercent = 0, bottomEndPercent = 0))
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "$animatedScore",
                color = Color.White,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Black
            )
            StreakFlame(streak)
            Text(
                text = "Game $challengeIndex",
                color = Color(0xFF94A3B8),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold
            )
        }

        if (timeFraction != null) {
            TimeBar(timeFraction, timeRemainingMs)
        }
    }
}

@Composable
private fun StreakFlame(streak: Int) {
    if (streak <= 0) {
        Text(
            text = "no streak",
            color = Color(0xFF64748B),
            style = MaterialTheme.typography.labelMedium
        )
        return
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(text = "\uD83D\uDD25", style = MaterialTheme.typography.titleLarge)
        Text(
            text = " $streak",
            color = Color(0xFFFDE047),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Black
        )
    }
}

@Composable
private fun TimeBar(fraction: Float, remainingMs: Long?) {
    val animated by animateFloatAsState(fraction.coerceIn(0f, 1f), label = "timeBar")
    val barColor = when {
        animated > 0.5f -> Color(0xFF4ADE80)
        animated > 0.25f -> Color(0xFFFBBF24)
        else -> Color(0xFFF87171)
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .weight(1f)
                .height(10.dp)
                .clip(RoundedCornerShape(5.dp))
                .background(Color(0x33FFFFFF)),
            contentAlignment = Alignment.CenterStart
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(animated)
                    .height(10.dp)
                    .clip(RoundedCornerShape(5.dp))
                    .background(barColor)
            )
        }
        if (remainingMs != null) {
            Text(
                text = " ${remainingMs / 1000}s",
                color = Color.White,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}
