package com.cognex.realplay.ui.modeselect

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.cognex.realplay.challenge.AgeBand
import com.cognex.realplay.engine.PlayMode
import com.cognex.realplay.engine.SessionConfig
import com.cognex.realplay.engine.ToddlerSupervision
import com.cognex.realplay.perception.AssetModelResolver
import com.cognex.realplay.ui.common.RpButton
import com.cognex.realplay.ui.common.RpCard
import com.cognex.realplay.ui.common.RpChip
import com.cognex.realplay.ui.common.RpOutlinedButton
import com.cognex.realplay.ui.common.RpScaffold
import com.cognex.realplay.ui.theme.RpAmber
import com.cognex.realplay.ui.theme.RpCyan
import com.cognex.realplay.ui.theme.RpOnDark
import com.cognex.realplay.ui.theme.RpOnDarkMuted
import com.cognex.realplay.ui.theme.RpSpace

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
    var supervisionAcked by remember { mutableStateOf(ToddlerSupervision.isAcknowledged(context)) }
    var showSupervisionDialog by remember { mutableStateOf(false) }

    RpScaffold(title = "Choose your game") {
        SectionLabel("Mode")
        Spacer(Modifier.height(8.dp))
        RpCard(
            selected = mode == PlayMode.OBJECTS,
            onClick = { mode = PlayMode.OBJECTS },
            modifier = Modifier.fillMaxWidth().height(84.dp)
        ) { ModeCardBody("Objects", "Move and arrange real things", enabled = true) }
        Spacer(Modifier.height(10.dp))
        RpCard(
            selected = mode == PlayMode.BODY,
            enabled = poseAvailable,
            onClick = { mode = PlayMode.BODY },
            modifier = Modifier.fillMaxWidth().height(84.dp)
        ) { ModeCardBody("Body", if (poseAvailable) "Poses and movement" else "needs a person", enabled = poseAvailable) }
        Spacer(Modifier.height(10.dp))
        RpCard(
            selected = mode == PlayMode.MIXED,
            onClick = { mode = PlayMode.MIXED },
            modifier = Modifier.fillMaxWidth().height(84.dp)
        ) { ModeCardBody("Mixed", "Objects and movement together", enabled = true) }

        Spacer(Modifier.height(RpSpace.lg))
        SectionLabel("Age")
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            RpChip("Toddler", age == AgeBand.TODDLER, Modifier.weight(1f), accent = RpAmber) {
                age = AgeBand.TODDLER
                if (!supervisionAcked) showSupervisionDialog = true
            }
            RpChip("Early", age == AgeBand.EARLY, Modifier.weight(1f), accent = RpAmber) { age = AgeBand.EARLY }
            RpChip("Middle", age == AgeBand.MIDDLE, Modifier.weight(1f), accent = RpAmber) { age = AgeBand.MIDDLE }
            RpChip("Older", age == AgeBand.OLDER, Modifier.weight(1f), accent = RpAmber) { age = AgeBand.OLDER }
        }

        Spacer(Modifier.height(RpSpace.lg))
        SectionLabel("Players")
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            RpChip("1 player", players == 1, Modifier.weight(1f)) { players = 1 }
            RpChip("2 players", players == 2, Modifier.weight(1f)) { players = 2 }
        }

        Spacer(Modifier.height(RpSpace.xl))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            RpOutlinedButton(text = "Back", onClick = onBack, modifier = Modifier.weight(1f))
            RpButton(
                text = "Continue",
                onClick = {
                    SessionConfig.mode = mode
                    SessionConfig.ageBand = age
                    SessionConfig.playerCount = players
                    onContinue()
                },
                enabled = age != AgeBand.TODDLER || supervisionAcked,
                modifier = Modifier.weight(1f)
            )
        }
    }

    // Adult-supervision notice (Architecture §10 rule 9) — shown once per install, acknowledged
    // before Toddler play begins. Blocks Continue for TODDLER until dismissed.
    if (showSupervisionDialog) {
        AlertDialog(
            onDismissRequest = { showSupervisionDialog = false },
            title = { Text("Toddler mode") },
            text = { Text("Please stay nearby and supervise play. RealPlay uses the camera to watch the room, not the child.") },
            confirmButton = {
                Button(onClick = {
                    ToddlerSupervision.acknowledge(context)
                    supervisionAcked = true
                    showSupervisionDialog = false
                }) { Text("I understand") }
            }
        )
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, color = RpOnDarkMuted, fontWeight = FontWeight.SemiBold)
}

@Composable
private fun ModeCardBody(title: String, subtitle: String, enabled: Boolean) {
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
            color = if (enabled) RpOnDarkMuted else com.cognex.realplay.ui.theme.RpError
        )
    }
}
