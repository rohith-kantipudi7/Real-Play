package com.cognex.realplay.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
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
import com.cognex.realplay.ui.common.RpCard
import com.cognex.realplay.ui.common.RpOutlinedButton
import com.cognex.realplay.ui.common.RpScaffold
import com.cognex.realplay.ui.theme.RpSpace

/** Settings — device probe, sound, the on-device AI composer toggle, and developer flags. */
@Composable
fun SettingsScreen(
    deviceCapabilities: DeviceCapabilities,
    performanceProfile: PerformanceProfile,
    onBack: () -> Unit
) {
    RpScaffold(title = "Settings", subtitle = "Device, sound, and the AI that builds your games.") {
        SectionLabel("Device")
        Spacer(Modifier.height(8.dp))
        RpCard(modifier = Modifier.fillMaxWidth()) {
            SectionBody {
                SettingRow("Performance profile", performanceProfile.name)
                SettingRow("Model", "${deviceCapabilities.manufacturer} ${deviceCapabilities.model}")
                SettingRow("Android", "${deviceCapabilities.androidRelease} (API ${deviceCapabilities.androidSdk})")
                SettingRow("RAM", "${deviceCapabilities.totalRamMb} MB")
                SettingRow("CPU cores", deviceCapabilities.availableProcessors.toString())
                SettingRow("Battery", "${deviceCapabilities.batteryPercent}%")
                SettingRow("Thermal", deviceCapabilities.thermalStatus.name, divider = false)
            }
        }

        Spacer(Modifier.height(RpSpace.xl))
        SectionLabel("Sound")
        Spacer(Modifier.height(8.dp))
        RpCard(modifier = Modifier.fillMaxWidth()) {
            SectionBody {
                val muted by AppSettings.muted.collectAsState()
                ToggleRow(
                    label = "Mute sound effects",
                    checked = muted,
                    onCheckedChange = AppSettings::setMuted,
                    divider = false
                )
            }
        }

        Spacer(Modifier.height(RpSpace.xl))
        SectionLabel("AI composer")
        Spacer(Modifier.height(8.dp))
        RpCard(modifier = Modifier.fillMaxWidth()) {
            SectionBody {
                val aiComposer by AppSettings.aiComposerEnabled.collectAsState()
                ToggleRow(
                    label = "Use AI to compose games",
                    checked = aiComposer,
                    onCheckedChange = AppSettings::setAiComposerEnabled,
                    divider = false
                )
                Text(
                    text = "Every game is built for your table by a cloud model first, then the " +
                        "on-device model, then a built-in composer — you'll see \u201cCreating your " +
                        "game\u2026\u201d for a moment while it does. The AI only arranges verifiable " +
                        "skills and wording; it never decides pass or fail, and the game stays fully " +
                        "playable offline with this switched off.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }

        Spacer(Modifier.height(RpSpace.xl))
        SectionLabel("Developer")
        Spacer(Modifier.height(8.dp))
        RpCard(modifier = Modifier.fillMaxWidth()) {
            SectionBody {
                val forceTrackOnly by AppSettings.forceTrackOnly.collectAsState()
                ToggleRow(
                    label = "Force track-only mode",
                    checked = forceTrackOnly,
                    onCheckedChange = AppSettings::setForceTrackOnly
                )
                val devOverlay by AppSettings.devOverlayEnabled.collectAsState()
                ToggleRow(
                    label = "Show developer overlay",
                    checked = devOverlay,
                    onCheckedChange = AppSettings::setDevOverlayEnabled,
                    divider = false
                )
                Text(
                    text = "Raw scene/difficulty numbers over the camera preview. Off by default — " +
                        "only useful for debugging on this device.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }

        Spacer(Modifier.height(RpSpace.xl))
        RpOutlinedButton(text = "Back", onClick = onBack, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(text, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.secondary)
}

@Composable
private fun SectionBody(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier.padding(horizontal = RpSpace.md, vertical = RpSpace.sm),
        content = content
    )
}

@Composable
private fun SettingRow(label: String, value: String, divider: Boolean = true) {
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
    if (divider) HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit, divider: Boolean = true) {
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
    if (divider) HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
}
