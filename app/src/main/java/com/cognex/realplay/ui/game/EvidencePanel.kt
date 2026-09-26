package com.cognex.realplay.ui.game

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.cognex.realplay.verify.Evidence
import com.cognex.realplay.verify.MeasurementDomain
import kotlin.math.roundToInt

/**
 * The measurement panel (Architecture §13 S6, §12, §20 invariant 6) — renders measured vs required
 * IN THE EVIDENCE'S OWN [MeasurementDomain]. While the domain is NORMALIZED the wording is honest
 * and relative ("Bottle to Book: 0.18 — needed under 0.30 ✓"); only when a fact is METRIC does it
 * show centimetres. It NEVER fabricates a unit.
 *
 * For a multi-step mission it shows one row per evidence item.
 */
@Composable
fun EvidencePanel(
    evidence: List<Evidence>,
    modifier: Modifier = Modifier
) {
    if (evidence.isEmpty()) return
    Column(
        modifier = modifier
            .background(Color(0xAA0B1220), RoundedCornerShape(10.dp))
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        evidence.forEach { EvidenceRow(it) }
    }
}

@Composable
private fun EvidenceRow(ev: Evidence) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = evidenceWording(ev),
            color = Color(0xFFE2E8F0),
            style = MaterialTheme.typography.bodyMedium
        )
        Text(
            text = if (ev.satisfied) "\u2713" else "\u2026",
            color = if (ev.satisfied) Color(0xFF4ADE80) else Color(0xFFFBBF24),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold
        )
    }
}

/**
 * Honest domain-aware wording (§12). NORMALIZED/PIXEL values stay relative; only METRIC gets "cm".
 * The comparator is spelled out in plain words so a parent reads it in a glance.
 */
private fun evidenceWording(ev: Evidence): String {
    val label = ev.label.replaceFirstChar { it.uppercase() }
    return when (ev.domain) {
        MeasurementDomain.METRIC -> {
            val m = "${(ev.measured).roundToInt()} cm"
            val need = "${(ev.required).roundToInt()} cm"
            "$label: $m ${needPhrase(ev.comparator)} $need"
        }
        MeasurementDomain.NORMALIZED, MeasurementDomain.PIXEL -> {
            val m = format2(ev.measured)
            val need = format2(ev.required)
            "$label: $m ${needPhrase(ev.comparator)} $need"
        }
    }
}

private fun needPhrase(comparator: String): String = when (comparator) {
    "<", "<=" -> "— needed under"
    ">", ">=" -> "— needed over"
    "==" -> "— needs"
    "~=" -> "— target"
    else -> "vs"
}

private fun format2(v: Float): String = ((v * 100f).roundToInt() / 100f).toString()
