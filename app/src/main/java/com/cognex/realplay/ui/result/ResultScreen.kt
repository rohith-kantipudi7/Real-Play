package com.cognex.realplay.ui.result

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.cognex.realplay.engine.ChallengeResult
import com.cognex.realplay.engine.SessionResults
import com.cognex.realplay.ui.common.RpButton
import com.cognex.realplay.ui.common.RpOutlinedButton
import com.cognex.realplay.ui.common.RpScaffold
import com.cognex.realplay.ui.common.RpStatCard
import com.cognex.realplay.ui.theme.RpAmber
import com.cognex.realplay.ui.theme.RpCyan
import com.cognex.realplay.ui.theme.RpNavyElevated
import com.cognex.realplay.ui.theme.RpOnDark
import com.cognex.realplay.ui.theme.RpOnDarkMuted
import com.cognex.realplay.ui.theme.RpSpace
import com.cognex.realplay.verify.Evidence
import com.cognex.realplay.verify.MeasurementDomain
import kotlin.math.roundToInt

/**
 * The end-of-session summary (Architecture §13 S6): total score, best streak, and a per-challenge
 * list showing each measurement in its own [MeasurementDomain] and its step count, then Play Again
 * / Home. Reads the just-played session from [SessionResults].
 */
@Composable
fun ResultScreen(onPlayAgain: () -> Unit, onHome: () -> Unit) {
    val results = remember { SessionResults.results }
    val total = remember { SessionResults.totalScore }
    val best = remember { SessionResults.bestStreak }
    val passed = remember { results.count { it.passed } }

    RpScaffold(title = "Great playing!", subtitle = "Here's how this session went.") {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            RpStatCard("Score", "$total", RpCyan, Modifier.weight(1f))
            RpStatCard("Best streak", "$best", RpAmber, Modifier.weight(1f))
            RpStatCard("Cleared", "$passed/${results.size}", Color(0xFF57E39B), Modifier.weight(1f))
        }

        Spacer(Modifier.height(RpSpace.lg))
        Text("Challenges", style = MaterialTheme.typography.titleMedium, color = RpOnDarkMuted, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))

        if (results.isEmpty()) {
            Text(
                "No challenges completed this round.",
                style = MaterialTheme.typography.bodyMedium,
                color = RpOnDarkMuted
            )
        } else {
            results.forEach { ResultRow(it) }
        }

        Spacer(Modifier.height(RpSpace.lg + RpSpace.xs))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            RpOutlinedButton(text = "Home", onClick = onHome, modifier = Modifier.weight(1f))
            RpButton(text = "Play Again", onClick = onPlayAgain, modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun ResultRow(r: ChallengeResult) {
    val accent = if (r.passed) Color(0xFF57E39B) else Color(0xFFFF5C7A)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .background(RpNavyElevated, RoundedCornerShape(14.dp))
            .border(1.dp, Color(0x1AFFFFFF), RoundedCornerShape(14.dp))
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "${r.index}. ${prettyType(r.type)}  (${r.winnerId})",
                style = MaterialTheme.typography.titleMedium,
                color = RpOnDark,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = if (r.timedOut) "TIMED OUT" else if (r.passed) "+${r.score}" else "—",
                style = MaterialTheme.typography.titleMedium,
                color = accent,
                fontWeight = FontWeight.Bold
            )
        }
        Text(
            text = "${r.completedSteps}/${r.stepCount} step${if (r.stepCount == 1) "" else "s"}",
            style = MaterialTheme.typography.labelMedium,
            color = RpOnDarkMuted
        )
        r.evidence.forEach { ev ->
            Text(
                text = evidenceWording(ev),
                style = MaterialTheme.typography.bodySmall,
                color = RpOnDarkMuted
            )
        }
    }
}

private fun prettyType(type: String): String =
    type.split('_').joinToString(" ") { it.lowercase().replaceFirstChar { c -> c.uppercase() } }

/** Same honest domain wording as the live EvidencePanel (§12) — never fabricates a unit. */
private fun evidenceWording(ev: Evidence): String {
    val label = ev.label.replaceFirstChar { it.uppercase() }
    val need = when (ev.comparator) {
        "<", "<=" -> "needed under"
        ">", ">=" -> "needed over"
        "==" -> "needs"
        "~=" -> "target"
        else -> "vs"
    }
    return when (ev.domain) {
        MeasurementDomain.METRIC ->
            "$label: ${ev.measured.roundToInt()} cm — $need ${ev.required.roundToInt()} cm"
        MeasurementDomain.NORMALIZED, MeasurementDomain.PIXEL ->
            "$label: ${fmt2(ev.measured)} — $need ${fmt2(ev.required)}"
    }
}

private fun fmt2(v: Float): String = ((v * 100f).roundToInt() / 100f).toString()
