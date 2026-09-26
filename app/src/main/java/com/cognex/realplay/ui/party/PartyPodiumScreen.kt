package com.cognex.realplay.ui.party

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.cognex.realplay.engine.PartyRuntime
import com.cognex.realplay.ui.common.RpButton
import com.cognex.realplay.ui.common.RpCard
import com.cognex.realplay.ui.common.RpOutlinedButton
import com.cognex.realplay.ui.common.RpScaffold
import com.cognex.realplay.ui.theme.RpCyan
import com.cognex.realplay.ui.theme.RpOnDark
import com.cognex.realplay.ui.theme.RpOnDarkMuted
import com.cognex.realplay.ui.theme.RpSpace

/** Final podium reveal (Architecture §25) — the ranked leaderboard, celebratory but honest. */
@Composable
fun PartyPodiumScreen(onPlayAgain: () -> Unit, onHome: () -> Unit) {
    val ranked = PartyRuntime.active?.leaderboard?.ranked() ?: emptyList()

    RpScaffold(title = "Results") {
        RpCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(horizontal = RpSpace.md + RpSpace.xs, vertical = RpSpace.sm)) {
                ranked.forEachIndexed { i, entry ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${medal(i)} ${entry.name}",
                            color = RpOnDark,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = if (i == 0) FontWeight.Black else FontWeight.SemiBold
                        )
                        Text("${entry.score}", color = RpCyan, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                    }
                    if (i != ranked.lastIndex) HorizontalDivider(color = Color(0x1AFFFFFF))
                }
                if (ranked.isEmpty()) {
                    Text("No rounds played yet.", color = RpOnDarkMuted, modifier = Modifier.padding(vertical = 16.dp))
                }
            }
        }

        Spacer(Modifier.height(RpSpace.xl))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            RpOutlinedButton(text = "Play Again", onClick = onPlayAgain, modifier = Modifier.weight(1f))
            RpButton(text = "Home", onClick = onHome, modifier = Modifier.weight(1f))
        }
    }
}

private fun medal(index: Int): String = when (index) {
    0 -> "\uD83E\uDD47"
    1 -> "\uD83E\uDD48"
    2 -> "\uD83E\uDD49"
    else -> "${index + 1}."
}
