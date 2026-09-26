package com.cognex.realplay.ui.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.cognex.realplay.ui.common.RpButton
import com.cognex.realplay.ui.common.RpHeroBackground
import com.cognex.realplay.ui.common.RpLogoMark
import com.cognex.realplay.ui.common.RpTextButton
import com.cognex.realplay.ui.theme.RealPlayTheme
import com.cognex.realplay.ui.theme.RpAmber
import com.cognex.realplay.ui.theme.RpCyan
import com.cognex.realplay.ui.theme.RpOnDarkMuted

/** Landing screen: an aperture mark, wordmark, tagline, PLAY, PARTY, and a Settings entry. */
@Composable
fun HomeScreen(
    onPlay: () -> Unit,
    onParty: () -> Unit,
    onVrPreview: () -> Unit,
    onSettings: () -> Unit
) {
    Box(modifier = Modifier.fillMaxSize()) {
        RpHeroBackground()
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp, vertical = 32.dp)
                .padding(bottom = 24.dp)
        ) {
            Spacer(Modifier.weight(0.7f))
            RpLogoMark()
            Spacer(Modifier.height(20.dp))
            Text(
                text = "REALPLAY",
                style = MaterialTheme.typography.displayLarge,
                color = RpCyan,
                fontWeight = FontWeight.Black
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Your world is the game.",
                style = MaterialTheme.typography.titleLarge,
                color = RpAmber,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.weight(0.6f))
            RpButton(
                text = "PLAY",
                onClick = onPlay,
                containerColor = RpCyan,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp)
            )
            Spacer(Modifier.height(12.dp))
            RpButton(
                text = "PARTY",
                onClick = onParty,
                containerColor = RpAmber,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
            )
            Spacer(Modifier.height(12.dp))
            RpTextButton(text = "Settings", onClick = onSettings, modifier = Modifier.fillMaxWidth())
            RpTextButton(text = "VR Preview (concept)", onClick = onVrPreview, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.weight(0.15f))
            Text(
                text = "Offline-first · on-device · Team Cognex",
                style = MaterialTheme.typography.labelMedium,
                color = RpOnDarkMuted
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun HomeScreenPreview() {
    RealPlayTheme {
        HomeScreen(onPlay = {}, onParty = {}, onVrPreview = {}, onSettings = {})
    }
}
