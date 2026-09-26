package com.cognex.realplay.ui.common

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.cognex.realplay.ui.theme.RpAmber
import com.cognex.realplay.ui.theme.RpCyan
import com.cognex.realplay.ui.theme.RpOnDark
import com.cognex.realplay.ui.theme.RpOnDarkMuted
import com.cognex.realplay.ui.theme.RpNavy
import com.cognex.realplay.ui.theme.RpNavyDeep
import com.cognex.realplay.ui.theme.RpNavyElevated
import com.cognex.realplay.ui.theme.RpRadius
import com.cognex.realplay.ui.theme.RpSpace

/**
 * RealPlay's shared design-system components (Architecture §13 UX). One place for the chrome every
 * screen reuses, so the app reads as one product instead of five different ones grown across
 * S5–S6 + AI + v3.6. Presentation only — none of this touches perception, the engine or a verdict.
 */

/** Consistent full-bleed screen: gradient background + title + optional scrolling + padded content. */
@Composable
fun RpScaffold(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    scrollable: Boolean = true,
    content: @Composable ColumnScope.() -> Unit
) {
    val scroll = if (scrollable) Modifier.verticalScroll(rememberScrollState()) else Modifier
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(RpNavyElevated, RpNavy, RpNavyDeep)))
            .statusBarsPadding()
            .then(scroll)
            .padding(RpSpace.lg)
    ) {
        Text(title, style = MaterialTheme.typography.headlineMedium, color = RpCyan, fontWeight = FontWeight.Bold)
        if (subtitle != null) {
            Spacer(Modifier.height(RpSpace.xs))
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = RpOnDarkMuted)
        }
        Spacer(Modifier.height(RpSpace.lg - RpSpace.xs))
        content()
    }
}

/** A soft radial glow behind hero content (Home) — fills empty dark space with real depth. */
@Composable
fun RpHeroBackground(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(RpNavyElevated, RpNavy, RpNavyDeep)))
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        colors = listOf(RpCyan.copy(alpha = 0.16f), Color.Transparent),
                        center = Offset.Unspecified,
                        radius = 900f
                    )
                )
        )
    }
}

/** A simple aperture/lens mark (no image assets) — reads as "camera", RealPlay's whole premise. */
@Composable
fun RpLogoMark(modifier: Modifier = Modifier, markSize: androidx.compose.ui.unit.Dp = 88.dp) {
    Canvas(modifier = modifier.size(markSize)) {
        val radius = size.minDimension / 2f
        val stroke = radius * 0.14f
        drawCircle(color = RpCyan.copy(alpha = 0.14f), radius = radius)
        drawCircle(color = RpCyan, radius = radius - stroke, style = Stroke(width = stroke))
        drawCircle(color = RpAmber, radius = radius * 0.32f)
    }
}

@Composable
fun RpButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    containerColor: Color = RpCyan
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(RpRadius.lg),
        colors = ButtonDefaults.buttonColors(containerColor = containerColor),
        elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp, pressedElevation = 1.dp),
        modifier = modifier.height(56.dp)
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun RpOutlinedButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(RpRadius.lg),
        border = ButtonDefaults.outlinedButtonBorder(enabled).copy(width = 1.5.dp),
        modifier = modifier.height(56.dp)
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun RpTextButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    TextButton(onClick = onClick, modifier = modifier) {
        Text(text, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/**
 * A selectable card (mode/format pickers) or a plain grouping container (Settings sections) —
 * pass [onClick] for the former, omit it for the latter.
 */
@Composable
fun RpCard(
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    enabled: Boolean = true,
    accent: Color = RpCyan,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val border = if (selected) accent else Color(0x33FFFFFF)
    val bg = if (selected) accent.copy(alpha = 0.14f) else RpNavyElevated
    val shape = RoundedCornerShape(RpRadius.xl)
    val base = modifier
        .shadow(if (selected) 8.dp else 3.dp, shape, clip = false)
        .clip(shape)
        .background(bg)
        .border(if (selected) 2.dp else 1.dp, border, shape)
    Column(
        modifier = if (onClick != null) base.clickable(enabled = enabled, onClick = onClick) else base,
        content = content
    )
}

/** A pill-shaped selectable chip (age band, player count, format). */
@Composable
fun RpChip(label: String, selected: Boolean, modifier: Modifier = Modifier, accent: Color = RpCyan, onClick: () -> Unit) {
    val border = if (selected) accent else Color(0x33FFFFFF)
    val bg = if (selected) accent.copy(alpha = 0.16f) else RpNavyElevated
    val shape = RoundedCornerShape(RpRadius.md)
    Column(
        modifier = modifier
            .shadow(if (selected) 6.dp else 2.dp, shape, clip = false)
            .clip(shape)
            .background(bg)
            .border(2.dp, border, RoundedCornerShape(RpRadius.md))
            .clickable(onClick = onClick)
            .padding(vertical = RpSpace.md),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(label, color = if (selected) accent else RpOnDarkMuted, style = MaterialTheme.typography.labelLarge)
    }
}

/**
 * A premium selectable option row: a leading emoji [icon] in a tinted tile, a [title] + [subtitle],
 * and a trailing selection ring. Used for the mode and party-format pickers so they read as a real
 * product choice, not a plain list. [enabled] false dims it and shows [disabledNote] as the subtitle.
 */
@Composable
fun RpOptionCard(
    icon: String,
    title: String,
    subtitle: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    accent: Color = RpCyan,
    disabledNote: String? = null,
    onClick: () -> Unit
) {
    RpCard(
        selected = selected,
        enabled = enabled,
        accent = accent,
        onClick = onClick,
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = RpSpace.md, vertical = RpSpace.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(RpSpace.md)
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(RpRadius.md))
                    .background((if (selected) accent else RpOnDarkMuted).copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center
            ) {
                Text(icon, style = MaterialTheme.typography.headlineSmall)
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleLarge,
                    color = if (enabled) RpOnDark else RpOnDarkMuted,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    if (!enabled && disabledNote != null) disabledNote else subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (enabled) RpOnDarkMuted else RpAmber
                )
            }
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(RoundedCornerShape(50))
                    .background(if (selected) accent else Color.Transparent)
                    .border(2.dp, if (selected) accent else Color(0x33FFFFFF), RoundedCornerShape(50)),
                contentAlignment = Alignment.Center
            ) {
                if (selected) {
                    Text("\u2713", color = RpNavyDeep, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Black)
                }
            }
        }
    }
}

/** One accented number + label — the score/streak/cleared trio on Result, reused wherever a stat needs one look. */
@Composable
fun RpStatCard(label: String, value: String, accent: Color, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(RpRadius.lg)
    Column(
        modifier = modifier
            .shadow(3.dp, shape, clip = false)
            .clip(shape)
            .background(RpNavyElevated)
            .padding(vertical = RpSpace.md, horizontal = RpSpace.sm + RpSpace.xs),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(value, style = MaterialTheme.typography.headlineSmall, color = accent, fontWeight = FontWeight.Black)
        Text(label, style = MaterialTheme.typography.labelMedium, color = RpOnDarkMuted)
    }
}

/** A small accented pill label — e.g. a round/format tag. */
@Composable
fun RpBadge(text: String, modifier: Modifier = Modifier, color: Color = RpCyan) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(RpRadius.sm))
            .background(color.copy(alpha = 0.18f))
            .padding(horizontal = RpSpace.sm + RpSpace.xs, vertical = RpSpace.xs)
    ) {
        Text(text, color = color, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
    }
}

/** A calm, non-alarming banner — coaching hints, break suggestions, any "here's what to do" toast. */
@Composable
fun RpToast(text: String, visible: Boolean, modifier: Modifier = Modifier) {
    AnimatedVisibility(visible = visible && text.isNotBlank(), enter = fadeIn(), exit = fadeOut(), modifier = modifier) {
        Text(
            text = text,
            color = Color(0xFFBFF7EC),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Medium,
            modifier = Modifier
                .background(Color(0xE6241A4D), RoundedCornerShape(RpRadius.md))
                .border(1.dp, Color(0x6625E0C8), RoundedCornerShape(RpRadius.md))
                .padding(horizontal = RpSpace.lg - RpSpace.xs, vertical = RpSpace.md - RpSpace.xs)
        )
    }
}
