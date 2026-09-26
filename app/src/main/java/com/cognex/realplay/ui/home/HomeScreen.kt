package com.cognex.realplay.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.cognex.realplay.ui.theme.RealPlayTheme
import com.cognex.realplay.ui.theme.RpAmber
import com.cognex.realplay.ui.theme.RpCyan

/** Landing screen: wordmark, tagline, PLAY, and a Settings entry. */
@Composable
fun HomeScreen(
    onPlay: () -> Unit,
    onSettings: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = "REALPLAY",
                style = MaterialTheme.typography.displayLarge,
                color = RpCyan
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Your world is the game.",
                style = MaterialTheme.typography.titleLarge,
                color = RpAmber,
                fontWeight = FontWeight.Normal,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(48.dp))
            Button(
                onClick = onPlay,
                colors = ButtonDefaults.buttonColors(containerColor = RpCyan),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp)
            ) {
                Text(
                    text = "PLAY",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onPrimary
                )
            }
            Spacer(Modifier.height(12.dp))
            TextButton(onClick = onSettings, modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Settings",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun HomeScreenPreview() {
    RealPlayTheme {
        HomeScreen(onPlay = {}, onSettings = {})
    }
}
