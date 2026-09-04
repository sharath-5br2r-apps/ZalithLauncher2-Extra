/*
 * Zalith Launcher 2 — Zeryth Fork
 * Crash Analyzer: Evidence Card
 */

package com.movtery.zalithlauncher.ui.screens.main.crash

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.movtery.zalithlauncher.R
import com.movtery.zalithlauncher.game.crash.model.CrashDiagnosis
import com.movtery.zalithlauncher.game.crash.model.CrashEvidenceItem

@Composable
fun CrashEvidenceCard(
    diagnosis: CrashDiagnosis,
    showTechnical: Boolean,
    isExpanded: Boolean,
    onToggle: () -> Unit
) {
    ExpandableAnalyzerCard(
        title = stringResource(R.string.crash_evidence_title),
        isExpanded = isExpanded,
        onToggle = onToggle
    ) {
        val explanationText = if (showTechnical) {
            diagnosis.technicalDetail.ifBlank { diagnosis.rootCauseDetail }
        } else {
            diagnosis.rootCauseDetail.ifBlank { diagnosis.rootCause }
        }

        if (explanationText.isNotBlank()) {
            Text(
                text = explanationText,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(12.dp))
        }

        if (diagnosis.evidence.isEmpty()) {
            Text(
                text = stringResource(R.string.crash_evidence_none),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                diagnosis.evidence.forEach { item ->
                    EvidenceItem(item = item, showSource = showTechnical)
                }
            }
        }

        // Technical advanced view: show offending component
        if (showTechnical && diagnosis.offendingComponent != null) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.crash_evidence_offending, diagnosis.offendingComponent),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
        }
    }
}

@Composable
private fun EvidenceItem(item: CrashEvidenceItem, showSource: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_check),
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.width(8.dp))
        Column {
            Text(
                text = item.text,
                style = MaterialTheme.typography.bodySmall
            )
            if (showSource) {
                Text(
                    text = "Source: ${item.source.name.lowercase().replace('_', ' ')} | weight: ${"%.2f".format(item.weight)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
