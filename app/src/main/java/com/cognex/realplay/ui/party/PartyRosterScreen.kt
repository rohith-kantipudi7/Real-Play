package com.cognex.realplay.ui.party

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.cognex.realplay.challenge.AgeBand
import com.cognex.realplay.engine.PartyRuntime
import com.cognex.realplay.engine.SessionConfig
import com.cognex.realplay.party.Entrant
import com.cognex.realplay.party.PartyFormat
import com.cognex.realplay.party.PartySession
import com.cognex.realplay.party.Roster
import com.cognex.realplay.ui.common.RpButton
import com.cognex.realplay.ui.common.RpChip
import com.cognex.realplay.ui.common.RpOptionCard
import com.cognex.realplay.ui.common.RpOutlinedButton
import com.cognex.realplay.ui.common.RpScaffold
import com.cognex.realplay.ui.theme.RpAmber
import com.cognex.realplay.ui.theme.RpCyan
import com.cognex.realplay.ui.theme.RpError
import com.cognex.realplay.ui.theme.RpOnDark
import com.cognex.realplay.ui.theme.RpOnDarkMuted
import com.cognex.realplay.ui.theme.RpSpace

/**
 * Party roster setup (Architecture §25): pick a format, name 2–8 players (or 2–4 teams for
 * TEAM_VS_TEAM) and an age band, then start the rotation. Reuses the §10 supervision gate for
 * TODDLER — a birthday party of small children runs the same TODDLER pacing as SOLO.
 */
@Composable
fun PartyRosterScreen(onStart: () -> Unit, onBack: () -> Unit) {
    var format by remember { mutableStateOf(PartyFormat.RELAY) }
    val kind = if (format == PartyFormat.TEAM_VS_TEAM) Roster.EntrantKind.TEAM else Roster.EntrantKind.PLAYER
    var names by remember(kind) {
        mutableStateOf(defaultNames(kind))
    }
    // Party is never TODDLER (§25) — clamp up if a prior SOLO session left TODDLER selected.
    var age by remember {
        mutableStateOf(SessionConfig.ageBand.takeIf { it != AgeBand.TODDLER } ?: AgeBand.EARLY)
    }
    var error by remember { mutableStateOf<String?>(null) }

    val minCount = if (kind == Roster.EntrantKind.TEAM) Roster.MIN_TEAMS else Roster.MIN_PLAYERS
    val maxCount = if (kind == Roster.EntrantKind.TEAM) Roster.MAX_TEAMS else Roster.MAX_PLAYERS
    val noun = if (kind == Roster.EntrantKind.TEAM) "team" else "player"

    RpScaffold(title = "Party mode", subtitle = "Pass the phone around — the AI makes a fresh game for every turn.") {
        SectionLabel("Format")
        Spacer(Modifier.height(8.dp))
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            FormatRow("\uD83C\uDFC3", "Relay", "Each player takes one quick turn", format == PartyFormat.RELAY) { format = PartyFormat.RELAY }
            FormatRow("\u2694\uFE0F", "Head-to-head", "Two players race the same challenge", format == PartyFormat.HEAD_TO_HEAD) { format = PartyFormat.HEAD_TO_HEAD }
            FormatRow("\uD83D\uDC65", "Team vs team", "Members alternate, scores add up", format == PartyFormat.TEAM_VS_TEAM) { format = PartyFormat.TEAM_VS_TEAM }
            FormatRow("\uD83D\uDD25", "Co-op streak", "The whole room shares one streak", format == PartyFormat.CO_OP_STREAK) { format = PartyFormat.CO_OP_STREAK }
        }

        Spacer(Modifier.height(RpSpace.lg))
        SectionLabel(if (kind == Roster.EntrantKind.TEAM) "Teams" else "Players")
        Spacer(Modifier.height(8.dp))
        names.forEachIndexed { i, name ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .width(14.dp).height(14.dp)
                        .clip(CircleShape)
                        .background(PARTY_PALETTE[i % PARTY_PALETTE.size].toComposeColor())
                )
                Spacer(Modifier.width(10.dp))
                OutlinedTextField(
                    value = name,
                    onValueChange = { updated -> names = names.toMutableList().also { it[i] = updated } },
                    modifier = Modifier.weight(1f),
                    singleLine = true
                )
                if (names.size > minCount) {
                    TextButton(onClick = { names = names.toMutableList().also { it.removeAt(i) } }) {
                        Text("Remove", color = RpError)
                    }
                }
            }
        }
        if (names.size < maxCount) {
            TextButton(onClick = { names = names + "${noun.replaceFirstChar { it.uppercase() }} ${names.size + 1}" }) {
                Text("+ Add a $noun", color = RpCyan)
            }
        }

        Spacer(Modifier.height(RpSpace.lg))
        SectionLabel("Age")
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            RpChip("Early", age == AgeBand.EARLY, Modifier.weight(1f), accent = RpAmber) { age = AgeBand.EARLY }
            RpChip("Middle", age == AgeBand.MIDDLE, Modifier.weight(1f), accent = RpAmber) { age = AgeBand.MIDDLE }
            RpChip("Older", age == AgeBand.OLDER, Modifier.weight(1f), accent = RpAmber) { age = AgeBand.OLDER }
        }

        error?.let {
            Spacer(Modifier.height(12.dp))
            Text(it, color = RpError, style = MaterialTheme.typography.bodyMedium)
        }

        Spacer(Modifier.height(RpSpace.xl))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            RpOutlinedButton(text = "Back", onClick = onBack, modifier = Modifier.weight(1f))
            RpButton(
                text = "Start",
                onClick = {
                    val trimmed = names.map { it.trim() }.filter { it.isNotEmpty() }
                    if (trimmed.size < minCount) {
                        error = "Need at least $minCount ${noun}s"
                        return@RpButton
                    }
                    val entrants = trimmed.mapIndexed { i, n ->
                        Entrant(id = "e$i", name = n, colorTag = PARTY_PALETTE[i % PARTY_PALETTE.size])
                    }
                    SessionConfig.ageBand = age
                    PartyRuntime.active = PartySession(Roster(entrants, kind), format, age)
                    onStart()
                },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

private fun defaultNames(kind: Roster.EntrantKind): List<String> =
    if (kind == Roster.EntrantKind.TEAM) listOf("Team 1", "Team 2") else listOf("Player 1", "Player 2")

@Composable
private fun SectionLabel(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, color = RpOnDarkMuted, fontWeight = FontWeight.SemiBold)
}

@Composable
private fun FormatRow(icon: String, title: String, subtitle: String, selected: Boolean, onClick: () -> Unit) {
    RpOptionCard(
        icon = icon,
        title = title,
        subtitle = subtitle,
        selected = selected,
        accent = RpAmber,
        onClick = onClick
    )
}
