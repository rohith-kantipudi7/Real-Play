package com.cognex.realplay.ui.game

import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.cognex.realplay.ui.theme.RpNavyDeep
import com.cognex.realplay.ui.theme.RpRadius

/**
 * The structural-difficulty display (Architecture §13 S6, §8.1b). For a multi-step mission it shows
 * "Step X of N" prominently and renders ONE progress segment per step: completed steps carry a
 * persistent tick, the active step fills with the live hold progress, and future steps read as
 * dim/locked — making it visually obvious that step 2 only becomes active after step 1 fires.
 *
 * A single-step mission renders nothing (the ring alone carries it), keeping the HUD calm until the
 * structural axis actually matters.
 */
@Composable
fun StepTracker(
    stepIndex: Int,
    stepCount: Int,
    completedSteps: Int,
    stepProgress: Float,
    modifier: Modifier = Modifier,
    activeColor: Color = Color(0xFF25E0C8)
) {
    if (stepCount <= 1) return
    val shape = RoundedCornerShape(RpRadius.md)

    Column(
        modifier = modifier
            .shadow(4.dp, shape, clip = false)
            .background(RpNavyDeep.copy(alpha = 0.85f), shape)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {        Text(
            text = "Step ${(stepIndex + 1).coerceAtMost(stepCount)} of $stepCount",
            color = Color.White,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            for (i in 0 until stepCount) {
                val done = i < completedSteps
                val active = i == stepIndex && !done
                val fill = when {
                    done -> 1f
                    active -> stepProgress.coerceIn(0f, 1f)
                    else -> 0f
                }
                val animatedFill by animateFloatAsState(fill, label = "stepFill$i")
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(14.dp)
                        .clip(RoundedCornerShape(7.dp))
                        .background(Color(0x33FFFFFF)),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(animatedFill)
                            .height(14.dp)
                            .clip(RoundedCornerShape(7.dp))
                            .background(if (done) Color(0xFF57E39B) else activeColor)
                    )
                    if (done) {
                        Text(
                            text = "\u2713",
                            color = Color(0xFF06210F),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Black,
                            modifier = Modifier.align(Alignment.Center)
                        )
                    }
                }
            }
        }
    }
}
