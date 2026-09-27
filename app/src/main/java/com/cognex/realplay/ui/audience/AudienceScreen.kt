package com.cognex.realplay.ui.audience

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.cognex.realplay.engine.AppSettings
import com.cognex.realplay.engine.Audience
import com.cognex.realplay.engine.SessionConfig
import com.cognex.realplay.engine.ToddlerSupervision
import com.cognex.realplay.ui.common.RpButton
import com.cognex.realplay.ui.common.RpOptionCard
import com.cognex.realplay.ui.common.RpOutlinedButton
import com.cognex.realplay.ui.common.RpScaffold
import com.cognex.realplay.ui.theme.RpAmber
import com.cognex.realplay.ui.theme.RpOnDarkMuted
import com.cognex.realplay.ui.theme.RpSpace

/**
 * Difficulty / audience selection (§ audience control) — a dedicated screen BEFORE scanning so the
 * tier is locked in before the camera opens. Each tier sets the starting difficulty and the game
 * family; TODDLER is basic games only (find + colour) and needs a supervision acknowledgement.
 * The choice is written to [SessionConfig.audience], which also updates the engine's age band.
 */
@Composable
fun AudienceScreen(onContinue: () -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    var selected by remember { mutableStateOf(SessionConfig.audience) }
    var supervisionAcked by remember { mutableStateOf(ToddlerSupervision.isAcknowledged(context)) }
    var showSupervisionDialog by remember { mutableStateOf(false) }

    RpScaffold(
        title = "Who's playing?",
        subtitle = "Pick a difficulty — we'll tune every game to it. You can change it next time."
    ) {
        AudienceCard(
            icon = "\uD83E\uDDF8", // teddy bear
            audience = Audience.TODDLER,
            subtitle = "Very gentle · big cues · spoken · basic games only",
            selected = selected == Audience.TODDLER,
            accent = RpAmber
        ) {
            selected = Audience.TODDLER
            if (!supervisionAcked) showSupervisionDialog = true
        }
        Spacer(Modifier.height(RpSpace.md))
        AudienceCard(
            icon = "\uD83D\uDE42", // slight smile
            audience = Audience.KIDS,
            subtitle = "Playful and easy · grab, group, pose",
            selected = selected == Audience.KIDS
        ) { selected = Audience.KIDS }
        Spacer(Modifier.height(RpSpace.md))
        AudienceCard(
            icon = "\uD83C\uDFAF", // target
            audience = Audience.PLAYER,
            subtitle = "Balanced · adds line-up and ramps up",
            selected = selected == Audience.PLAYER
        ) { selected = Audience.PLAYER }
        Spacer(Modifier.height(RpSpace.md))
        AudienceCard(
            icon = "\u26A1", // high voltage
            audience = Audience.PRO,
            subtitle = "Fast and precise · timed · every game",
            selected = selected == Audience.PRO
        ) { selected = Audience.PRO }

        Spacer(Modifier.height(RpSpace.lg))
        Text(
            "Game style",
            style = MaterialTheme.typography.titleMedium,
            color = RpOnDarkMuted,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.height(RpSpace.sm))
        var verified by remember { mutableStateOf(AppSettings.verifierEnabled.value) }
        RpOptionCard(
            icon = "\uD83C\uDFAF", // target
            title = "Verified games",
            subtitle = "The camera checks each game and passes it when you really do it.",
            selected = verified
        ) { verified = true; AppSettings.setVerifierEnabled(true) }
        Spacer(Modifier.height(RpSpace.md))
        RpOptionCard(
            icon = "\u2728", // sparkles
            title = "Freeform (no camera check)",
            subtitle = "The app invents a fun game and reads it aloud — tap Next level when you're ready.",
            selected = !verified,
            accent = RpAmber
        ) { verified = false; AppSettings.setVerifierEnabled(false) }

        Spacer(Modifier.height(RpSpace.xl))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            RpOutlinedButton(text = "Back", onClick = onBack, modifier = Modifier.weight(1f))
            RpButton(
                text = "Start scanning",
                onClick = {
                    SessionConfig.audience = selected
                    onContinue()
                },
                enabled = selected != Audience.TODDLER || supervisionAcked,
                modifier = Modifier.weight(1.4f)
            )
        }
    }

    // Adult-supervision notice (§10 rule 9) — shown once per install, acknowledged before Toddler
    // play begins. Blocks Start for TODDLER until dismissed.
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
            },
            dismissButton = {
                Button(onClick = { showSupervisionDialog = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun AudienceCard(
    icon: String,
    audience: Audience,
    subtitle: String,
    selected: Boolean,
    accent: androidx.compose.ui.graphics.Color = com.cognex.realplay.ui.theme.RpCyan,
    onClick: () -> Unit
) {
    RpOptionCard(
        icon = icon,
        title = audience.label,
        subtitle = subtitle,
        selected = selected,
        accent = accent,
        onClick = onClick
    )
}
