package com.cognex.realplay.ui.common

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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/**
 * Shared placeholder scaffold for screens that are wired for navigation in S0 but filled in
 * during later stages. Keeps every screen consistent and clearly labelled.
 */
@Composable
fun PlaceholderScreen(
    title: String,
    subtitle: String,
    primaryLabel: String? = null,
    onPrimary: (() -> Unit)? = null,
    secondaryLabel: String? = null,
    onSecondary: (() -> Unit)? = null
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
                text = title,
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(40.dp))
            if (primaryLabel != null && onPrimary != null) {
                Button(
                    onClick = onPrimary,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                ) { Text(primaryLabel, style = MaterialTheme.typography.labelLarge) }
                Spacer(Modifier.height(12.dp))
            }
            if (secondaryLabel != null && onSecondary != null) {
                OutlinedButton(
                    onClick = onSecondary,
                    modifier = Modifier.fillMaxWidth()
                ) { Text(secondaryLabel, style = MaterialTheme.typography.labelLarge) }
            }
        }
    }
}
