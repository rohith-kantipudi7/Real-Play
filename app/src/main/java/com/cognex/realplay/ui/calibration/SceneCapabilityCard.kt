package com.cognex.realplay.ui.calibration

import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * The SceneCapabilityCard (Architecture §15) — "your 2 seconds". Shown once after CALIBRATING as a
 * warm, plain-language summary of what the camera sees, then tap to start.
 *
 * The raw ranked score table (long-press) only appears when the developer overlay is on
 * (Settings → Developer) — normal players never see internal scores.
 */
@Composable
fun SceneCapabilityCard(
    model: SceneCapabilityCardModel,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val devOverlay by com.cognex.realplay.engine.AppSettings.devOverlayEnabled.collectAsState()
    var showRanked by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xCC000000))
            .pointerInput(devOverlay) {
                detectTapGestures(
                    onTap = { onDismiss() },
                    onLongPress = { if (devOverlay) showRanked = !showRanked }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .padding(28.dp)
                .background(Color(0xFF10151C), RoundedCornerShape(20.dp))
                .padding(horizontal = 28.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.Start
        ) {
            Text(
                text = if (model.objectLabels.isNotEmpty()) "Here's what I can see" else "Let's take a look",
                style = MaterialTheme.typography.headlineSmall,
                color = Color.White,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(18.dp))

            if (showRanked) {
                RankedTable(model)
            } else if (model.objectLabels.isNotEmpty()) {
                Text(
                    text = model.objectLabels.joinToString("  ·  ") {
                        it.replaceFirstChar { ch -> ch.uppercase() }
                    },
                    style = MaterialTheme.typography.titleLarge,
                    color = Color(0xFF25E0C8),
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    text = "Let's make a game out of these!",
                    style = MaterialTheme.typography.titleMedium,
                    color = Color(0xFFCBD5E1)
                )
            } else if (model.sparse) {
                Text(
                    text = "This spot is a bit bare — let's play a simple one.",
                    style = MaterialTheme.typography.titleMedium,
                    color = Color(0xFFCBD5E1)
                )
            } else {
                model.rows.forEachIndexed { i, row ->
                    CountRow(value = row.value, label = row.label, delayMs = i * 120)
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    text = "Let's make a game out of these!",
                    style = MaterialTheme.typography.titleMedium,
                    color = Color(0xFFCBD5E1)
                )
            }

            Spacer(Modifier.height(20.dp))
            Text(
                text = "Tap anywhere to start",
                style = MaterialTheme.typography.labelLarge,
                color = Color(0xFF25E0C8),
                fontWeight = FontWeight.Bold
            )
            if (devOverlay) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = if (showRanked) "long-press to hide scores" else "long-press for scores",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFF64748B)
                )
            }
        }
    }
}

/** A single count row with the number animating 0→[value] over ~600 ms, staggered by [delayMs]. */
@Composable
private fun CountRow(value: Int, label: String, delayMs: Int) {
    var target by remember { mutableStateOf(0) }
    LaunchedEffect(value) { target = value }
    val animated by animateIntAsState(
        targetValue = target,
        animationSpec = tween(durationMillis = 600, delayMillis = delayMs, easing = LinearOutSlowInEasing),
        label = "count"
    )
    Row(
        modifier = Modifier.padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = animated.toString(),
            style = MaterialTheme.typography.headlineMedium,
            color = Color(0xFFFFB13A),
            fontWeight = FontWeight.Bold,
            modifier = Modifier.width(44.dp)
        )
        Text(
            text = label,
            style = MaterialTheme.typography.titleMedium,
            color = Color(0xFFE2E8F0)
        )
    }
}

/** The long-press ranked list — monospace, scores to 2dp, every zero with its reason (§15). */
@Composable
private fun RankedTable(model: SceneCapabilityCardModel) {
    val pickedId = model.ranked.firstOrNull { it.score > 0f && it.type == model.playingType }?.generatorId
    Column {
        model.ranked.forEach { c ->
            val name = c.type.name.replace('_', ' ').lowercase()
                .split(' ').joinToString("") { it.replaceFirstChar { ch -> ch.uppercase() } }
            val scoreText = "%.2f".format(c.score)
            val tail = when {
                c.generatorId == pickedId -> "\u2713 picked"
                c.zeroReason != null -> "\u2717 ${c.zeroReason}"
                else -> ""
            }
            Row(modifier = Modifier.padding(vertical = 2.dp)) {
                Text(
                    text = name.padEnd(13),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 13.sp,
                    color = Color(0xFFE2E8F0)
                )
                Text(
                    text = scoreText,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 13.sp,
                    color = if (c.score > 0f) Color(0xFF4ADE80) else Color(0xFF64748B),
                    modifier = Modifier.width(52.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = tail,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 13.sp,
                    color = Color(0xFF94A3B8)
                )
            }
        }
    }
}
