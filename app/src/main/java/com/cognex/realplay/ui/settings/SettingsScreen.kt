package com.cognex.realplay.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.cognex.realplay.device.DeviceCapabilities
import com.cognex.realplay.device.PerformanceProfile
import com.cognex.realplay.engine.AppSettings

/** Settings — in S0 this surfaces the device probe and selected performance profile. */
@Composable
fun SettingsScreen(
    deviceCapabilities: DeviceCapabilities,
    performanceProfile: PerformanceProfile,
    onBack: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(24.dp)
    ) {
        Text(
            text = "Settings",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.height(24.dp))
        Text(
            text = "Device",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.secondary
        )
        Spacer(Modifier.height(8.dp))

        SettingRow("Performance profile", performanceProfile.name)
        SettingRow("Model", "${deviceCapabilities.manufacturer} ${deviceCapabilities.model}")
        SettingRow("Android", "${deviceCapabilities.androidRelease} (API ${deviceCapabilities.androidSdk})")
        SettingRow("RAM", "${deviceCapabilities.totalRamMb} MB")
        SettingRow("CPU cores", deviceCapabilities.availableProcessors.toString())
        SettingRow("Battery", "${deviceCapabilities.batteryPercent}%")
        SettingRow("Thermal", deviceCapabilities.thermalStatus.name)

        Spacer(Modifier.height(32.dp))
        Text(
            text = "Sound",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.secondary
        )
        Spacer(Modifier.height(8.dp))

        val muted by AppSettings.muted.collectAsState()
        ToggleRow(
            label = "Mute sound effects",
            checked = muted,
            onCheckedChange = AppSettings::setMuted
        )

        Spacer(Modifier.height(32.dp))
        Text(
            text = "AI composer (on-device)",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.secondary
        )
        Spacer(Modifier.height(8.dp))

        val aiComposer by AppSettings.aiComposerEnabled.collectAsState()
        ToggleRow(
            label = "Use Gemma to compose games",
            checked = aiComposer,
            onCheckedChange = AppSettings::setAiComposerEnabled
        )
        Text(
            text = "Runs fully offline on this phone. Needs the side-loaded Gemma model in " +
                "/sdcard/realplay/models. When off — or the model is absent — the game uses the " +
                "built-in composer. The AI only arranges verifiable skills and wording; it never " +
                "decides pass or fail.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp)
        )

        Spacer(Modifier.height(32.dp))
        Text(
            text = "Developer",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.secondary
        )
        Spacer(Modifier.height(8.dp))

        val forceTrackOnly by AppSettings.forceTrackOnly.collectAsState()
        ToggleRow(
            label = "Force track-only mode",
            checked = forceTrackOnly,
            onCheckedChange = AppSettings::setForceTrackOnly
        )

        Spacer(Modifier.height(32.dp))
        OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
            Text("Back", style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
private fun SettingRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.SemiBold
        )
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
}
