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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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

/**
 * "Whose turn" handoff (Architecture §25) — big name + colour swatch, the live leaderboard, and a
 * Ready button that starts that participant's round in the existing [com.cognex.realplay.ui.game.GameScreen].
 * If no PARTY session is active (e.g. process death mid-session), bails back rather than crashing.
 */
@Composable
fun PartyHandoffScreen(onReady: () -> Unit, onBack: () -> Unit) {
    val party = PartyRuntime.active
    if (party == null) {
        LaunchedEffect(Unit) { onBack() }
        return
    }

    val current = party.turns.current
    val opponent = party.turns.opponent
    val roundNumber = party.turns.roundNumber(party.turnsPlayed)

    RpScaffold(title = "Round $roundNumber · ${party.format.name.replace('_', ' ')}") {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .width(28.dp).height(28.dp)
                    .clip(CircleShape)
                    .background(current.colorTag.toComposeColor())
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "${current.name}'s turn!",
                style = MaterialTheme.typography.displaySmall,
                color = RpOnDark,
                fontWeight = FontWeight.Black
            )
            opponent?.let {
                Spacer(Modifier.height(4.dp))
                Text("vs ${it.name}", style = MaterialTheme.typography.titleMedium, color = RpCyan)
            }
        }

        Spacer(Modifier.height(RpSpace.xl))
        Text("Leaderboard", style = MaterialTheme.typography.titleMedium, color = RpOnDarkMuted, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        RpCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(horizontal = RpSpace.md, vertical = RpSpace.sm)) {
                party.leaderboard.ranked().forEachIndexed { i, entry ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("${i + 1}. ${entry.name}", color = RpOnDark, style = MaterialTheme.typography.bodyLarge)
                        Text("${entry.score}", color = RpCyan, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyLarge)
                    }
                    if (i != party.leaderboard.ranked().lastIndex) HorizontalDivider(color = Color(0x1AFFFFFF))
                }
            }
        }

        Spacer(Modifier.height(RpSpace.xl))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            RpOutlinedButton(text = "Back", onClick = onBack, modifier = Modifier.weight(1f))
            RpButton(text = "I'm ready!", onClick = onReady, modifier = Modifier.weight(1f))
        }
    }
}
