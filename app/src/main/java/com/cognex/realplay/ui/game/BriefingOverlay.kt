package com.cognex.realplay.ui.game

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/**
 * The pre-challenge briefing (Architecture §13 S6): the instruction animates in over a dimmed
 * scrim, then a 3-2-1 countdown ticks down to "GO!", after which [onGo] hands control to live play.
 * Re-runs whenever [challengeKey] changes (a new challenge selected).
 *
 * [onTick] fires once per countdown number and [onGo] once at the end, so the caller can play the
 * countdown/start cues.
 */
@Composable
fun BriefingOverlay(
    challengeKey: Int,
    instruction: String,
    modifier: Modifier = Modifier,
    onTick: () -> Unit = {},
    onGo: () -> Unit = {}
) {
    var visible by remember { mutableStateOf(false) }
    var count by remember { mutableIntStateOf(3) }

    LaunchedEffect(challengeKey) {
        if (challengeKey <= 0) return@LaunchedEffect
        visible = true
        // Let the instruction read for a beat before the numbers start.
        delay(700)
        for (n in 3 downTo 1) {
            count = n
            onTick()
            delay(650)
        }
        count = 0 // GO!
        onGo()
        delay(450)
        visible = false
    }

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = modifier
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xB3000000)),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(32.dp)
            ) {
                Text(
                    text = instruction,
                    color = Color.White,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .background(Color(0x66000000), RoundedCornerShape(16.dp))
                        .padding(horizontal = 20.dp, vertical = 14.dp)
                )
                val label = if (count == 0) "GO!" else count.toString()
                val pop by animateFloatAsState(
                    targetValue = if (count == 0) 1.4f else 1f,
                    label = "countPop"
                )
                Text(
                    text = label,
                    color = if (count == 0) Color(0xFF57E39B) else Color(0xFF25E0C8),
                    style = MaterialTheme.typography.displayLarge,
                    fontWeight = FontWeight.Black,
                    modifier = Modifier
                        .padding(top = 28.dp)
                        .scale(pop)
                )
            }
        }
    }
}
