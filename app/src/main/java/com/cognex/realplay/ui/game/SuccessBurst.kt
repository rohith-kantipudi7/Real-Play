package com.cognex.realplay.ui.game

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.sin

/**
 * The success celebration (Architecture §13 S6): a radial particle burst plus a big
 * "PERFECT!" / "NICE!" and the points gained. Fires the instant the mission passes, so the
 * feedback lands well under the 200 ms gate.
 *
 * The particle field is precomputed ONCE (angles/speeds/colours remembered), and the draw loop
 * only does trig into inline [Offset]s — zero heap allocation per frame (§13 S6 gate). A single
 * [Animatable] drives the whole burst; it replays whenever [triggerKey] changes.
 */
@Composable
fun SuccessBurst(
    visible: Boolean,
    perfect: Boolean,
    gainedPoints: Int,
    triggerKey: Int,
    modifier: Modifier = Modifier
) {
    val progress = remember { Animatable(0f) }
    var active by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(triggerKey) {
        if (triggerKey <= 0) return@LaunchedEffect
        active = 1f
        progress.snapTo(0f)
        progress.animateTo(1f, animationSpec = androidx.compose.animation.core.tween(durationMillis = 900))
        active = 0f
    }

    if (!visible || active == 0f) return

    val particles = remember {
        val n = 28
        Array(n) { i ->
            val angle = (i.toFloat() / n) * (2f * Math.PI.toFloat()) + (i % 3) * 0.21f
            val speed = 0.55f + (i % 5) * 0.09f
            val color = PARTICLE_COLORS[i % PARTICLE_COLORS.size]
            Particle(cos(angle), sin(angle), speed, color)
        }
    }

    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        val p = progress.value
        Canvas(modifier = Modifier.fillMaxSize()) {
            val cx = size.width / 2f
            val cy = size.height * 0.42f
            val maxReach = size.minDimension * 0.42f
            val radius = size.minDimension * 0.012f * (1f - p * 0.4f)
            val alpha = (1f - p).coerceIn(0f, 1f)
            for (particle in particles) {
                val dist = maxReach * particle.speed * easeOut(p)
                val x = cx + particle.dx * dist
                val y = cy + particle.dy * dist
                drawCircle(
                    color = particle.color.copy(alpha = alpha),
                    radius = radius,
                    center = Offset(x, y)
                )
            }
        }
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = if (perfect) "PERFECT!" else "NICE!",
                color = if (perfect) Color(0xFFFFD24B) else Color(0xFF57E39B),
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Black
            )
            if (gainedPoints > 0) {
                Text(
                    text = "+$gainedPoints",
                    color = Color.White,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

private data class Particle(val dx: Float, val dy: Float, val speed: Float, val color: Color)

private val PARTICLE_COLORS = arrayOf(
    Color(0xFFFFD24B),
    Color(0xFF57E39B),
    Color(0xFF25E0C8),
    Color(0xFFFF5CA8),
    Color(0xFF9B6BFF)
)

private fun easeOut(t: Float): Float {
    val inv = 1f - t
    return 1f - inv * inv
}
