package com.cognex.realplay.ui.modeselect

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.cognex.realplay.challenge.AgeBand
import com.cognex.realplay.engine.PlayMode
import com.cognex.realplay.engine.SessionConfig
import com.cognex.realplay.perception.AssetModelResolver
import com.cognex.realplay.ui.theme.RpCyan
import com.cognex.realplay.ui.theme.RpNavyElevated
import com.cognex.realplay.ui.theme.RpOnDark
import com.cognex.realplay.ui.theme.RpOnDarkMuted

/**
 * Mode / age / players selection (Architecture §13 S6, §10). Big visual cards a parent can use in
 * three seconds. Body mode is disabled and labelled "needs a person" while pose is off (until S8);
 * the choices are written to [SessionConfig] and read by the game view model when play starts.
 */
@Composable
fun ModeSelectScreen(onContinue: () -> Unit, onBack: () -> Unit) {
    // Pose ships in S8: Body/Mixed play is available whenever the pose landmarker asset is in the
    // APK. If the runtime pose backend later fails on a device, the object-only path still works
    // (players stay empty, G4/G5 pre-filter out) — §20 invariant 12.
    val context = LocalContext.current
    val poseAvailable = remember {
        AssetModelResolver(context).inspectRequired()
            .any { it.path.contains("pose_landmarker") && it.present }
    }

    var mode by remember { mutableStateOf(PlayMode.OBJECTS) }
    var age by remember { mutableStateOf(AgeBand.MIDDLE) }
    var players by remember { mutableIntStateOf(1) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(24.dp)
    ) {
        Text("Choose your game", style = MaterialTheme.typography.headlineMedium, color = RpCyan)
        Spacer(Modifier.height(20.dp))

        SectionLabel("Mode")
        Spacer(Modifier.height(8.dp))
        BigCard(
            title = "Objects",
            subtitle = "Move and arrange real things",
            selected = mode == PlayMode.OBJECTS,
            enabled = true,
            onClick = { mode = PlayMode.OBJECTS }
        )
        Spacer(Modifier.height(10.dp))
        BigCard(
            title = "Body",
            subtitle = if (poseAvailable) "Poses and movement" else "needs a person",
            selected = mode == PlayMode.BODY,
            enabled = poseAvailable,
            onClick = { mode = PlayMode.BODY }
        )
        Spacer(Modifier.height(10.dp))
        BigCard(
            title = "Mixed",
            subtitle = "Objects and movement together",
            selected = mode == PlayMode.MIXED,
            enabled = true,
            onClick = { mode = PlayMode.MIXED }
        )

        Spacer(Modifier.height(24.dp))
        SectionLabel("Age")
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            AgeChip("Toddler", age == AgeBand.TODDLER, Modifier.weight(1f)) { age = AgeBand.TODDLER }
            AgeChip("Early", age == AgeBand.EARLY, Modifier.weight(1f)) { age = AgeBand.EARLY }
            AgeChip("Middle", age == AgeBand.MIDDLE, Modifier.weight(1f)) { age = AgeBand.MIDDLE }
            AgeChip("Older", age == AgeBand.OLDER, Modifier.weight(1f)) { age = AgeBand.OLDER }
        }

        Spacer(Modifier.height(24.dp))
        SectionLabel("Players")
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            AgeChip("1 player", players == 1, Modifier.weight(1f)) { players = 1 }
            AgeChip("2 players", players == 2, Modifier.weight(1f)) { players = 2 }
        }

        Spacer(Modifier.height(32.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(onClick = onBack, modifier = Modifier.weight(1f)) { Text("Back") }
            Button(
                onClick = {
                    SessionConfig.mode = mode
                    SessionConfig.ageBand = age
                    SessionConfig.playerCount = players
                    onContinue()
                },
                modifier = Modifier.weight(1f)
            ) { Text("Continue") }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, color = RpOnDarkMuted, fontWeight = FontWeight.SemiBold)
}

@Composable
private fun BigCard(
    title: String,
    subtitle: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit
) {
    val border = if (selected) RpCyan else Color(0x22FFFFFF)
    val bg = if (selected) Color(0xFF10344A) else RpNavyElevated
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(84.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(bg)
            .border(2.dp, border, RoundedCornerShape(16.dp))
            .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 20.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Column {
            Text(
                title,
                style = MaterialTheme.typography.titleLarge,
                color = if (enabled) RpOnDark else RpOnDarkMuted,
                fontWeight = FontWeight.Bold
            )
            Text(
                subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = if (enabled) RpOnDarkMuted else Color(0xFFF87171)
            )
        }
    }
}

@Composable
private fun AgeChip(label: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val border = if (selected) RpCyan else Color(0x22FFFFFF)
    val bg = if (selected) Color(0xFF10344A) else RpNavyElevated
    Box(
        modifier = modifier
            .height(52.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(bg)
            .border(2.dp, border, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) RpCyan else RpOnDark,
            fontWeight = FontWeight.SemiBold
        )
    }
}
