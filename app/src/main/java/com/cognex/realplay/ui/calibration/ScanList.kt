package com.cognex.realplay.ui.calibration

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.cognex.realplay.ui.theme.RpCyan
import com.cognex.realplay.ui.theme.RpNavyElevated
import kotlin.math.roundToInt

/** A detected object mid-flight from its camera position up to the list (§ scan-list UX). */
data class FlyingSpec(val id: Long, val label: String, val startX: Float, val startY: Float)

/** A friendly emoji for a COCO label so the list reads at a glance, no assets needed. */
fun objectEmoji(label: String): String = when (label.lowercase().trim()) {
    "person" -> "\uD83E\uDDD1"
    "cup" -> "\u2615"
    "bottle" -> "\uD83C\uDF7C"
    "wine glass" -> "\uD83C\uDF77"
    "bowl" -> "\uD83E\uDD63"
    "book" -> "\uD83D\uDCD6"
    "laptop" -> "\uD83D\uDCBB"
    "cell phone" -> "\uD83D\uDCF1"
    "mouse" -> "\uD83D\uDDB1\uFE0F"
    "keyboard" -> "\u2328\uFE0F"
    "remote" -> "\uD83D\uDCFA"
    "backpack", "handbag", "suitcase" -> "\uD83C\uDF92"
    "banana" -> "\uD83C\uDF4C"
    "apple" -> "\uD83C\uDF4E"
    "orange" -> "\uD83C\uDF4A"
    "sports ball" -> "\u26BD"
    "scissors" -> "\u2702\uFE0F"
    "clock" -> "\uD83D\uDD70\uFE0F"
    "vase" -> "\uD83C\uDFF5\uFE0F"
    "teddy bear" -> "\uD83E\uDDF8"
    "chair" -> "\uD83E\uDE91"
    "tv" -> "\uD83D\uDCFA"
    "spoon" -> "\uD83E\uDD44"
    "fork" -> "\uD83C\uDF74"
    "knife" -> "\uD83D\uDD2A"
    else -> "\uD83D\uDD37"
}

/** Title-cases a COCO label for display ("cell phone" → "Cell Phone"). */
fun prettyLabel(label: String): String =
    label.split(' ').joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } }

/** A settled chip in the top list — pops in with a scale/fade the first time it appears. */
@Composable
fun ScanChip(label: String, modifier: Modifier = Modifier) {
    val appear = remember { Animatable(0f) }
    LaunchedEffect(label) { appear.animateTo(1f, tween(320, easing = FastOutSlowInEasing)) }
    Row(
        modifier = modifier
            .graphicsLayer {
                alpha = appear.value
                scaleX = 0.6f + 0.4f * appear.value
                scaleY = 0.6f + 0.4f * appear.value
            }
            .background(RpNavyElevated.copy(alpha = 0.92f), RoundedCornerShape(50))
            .border(1.5.dp, RpCyan.copy(alpha = 0.5f), RoundedCornerShape(50))
            .padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(objectEmoji(label), style = MaterialTheme.typography.titleMedium)
        Text(
            prettyLabel(label),
            color = Color.White,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold
        )
    }
}

/**
 * A detected object animating from its camera location up to the top list, then handing off to a
 * settled [ScanChip] via [onArrived]. Fades + shrinks slightly as it travels — the "popping out"
 * effect the product owner asked for, kept deliberately simple.
 */
@Composable
fun FlyingChip(
    spec: FlyingSpec,
    targetX: Float,
    targetY: Float,
    onArrived: () -> Unit,
    modifier: Modifier = Modifier
) {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(spec.id) {
        progress.animateTo(1f, tween(620, easing = FastOutSlowInEasing))
        onArrived()
    }
    val p = progress.value
    val x = spec.startX + (targetX - spec.startX) * p
    val y = spec.startY + (targetY - spec.startY) * p
    Row(
        modifier = modifier
            .wrapContentSize()
            .offset { IntOffset((x - 60f).roundToInt(), (y - 24f).roundToInt()) }
            .graphicsLayer {
                alpha = 1f - 0.15f * p
                val s = 1.15f - 0.35f * p
                scaleX = s
                scaleY = s
            }
            .background(RpCyan.copy(alpha = 0.95f), RoundedCornerShape(50))
            .padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(objectEmoji(spec.label), style = MaterialTheme.typography.titleMedium)
        Text(
            prettyLabel(spec.label),
            color = com.cognex.realplay.ui.theme.RpNavyDeep,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold
        )
    }
}
