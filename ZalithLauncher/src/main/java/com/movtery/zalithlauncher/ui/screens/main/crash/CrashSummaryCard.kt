/*
 * Zalith Launcher 2 — Zeryth Fork
 * Crash Analyzer: Summary Card
 */

package com.movtery.zalithlauncher.ui.screens.main.crash

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.movtery.zalithlauncher.R
import com.movtery.zalithlauncher.game.crash.model.CrashCategory
import com.movtery.zalithlauncher.game.crash.model.CrashDiagnosis
import com.movtery.zalithlauncher.game.crash.model.CrashSession
import com.movtery.zalithlauncher.game.crash.model.CrashSeverity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun CrashSummaryCard(
    diagnosis: CrashDiagnosis,
    session: CrashSession?,
    isExpanded: Boolean,
    onToggle: () -> Unit
) {
    ExpandableAnalyzerCard(
        title = stringResource(R.string.crash_summary_title),
        isExpanded = isExpanded,
        onToggle = onToggle,
        headerContent = {
            // Category chip shown even when collapsed
            CategoryChip(diagnosis.category, diagnosis.severity)
        }
    ) {
        // Confidence
        ConfidenceRow(diagnosis.confidence, diagnosis.confidenceBand)

        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

        // Root cause
        InfoRow(
            label = stringResource(R.string.crash_summary_root_cause),
            value = diagnosis.rootCause
        )

        if (diagnosis.offendingComponent != null) {
            InfoRow(
                label = stringResource(R.string.crash_summary_offending),
                value = diagnosis.offendingComponent
            )
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

        // Metadata from session
        session?.let { s ->
            s.mcVersion?.let { InfoRow(stringResource(R.string.crash_summary_mc_version), it) }
            s.loader?.let { InfoRow(stringResource(R.string.crash_summary_loader), it) }
            s.renderer?.let { InfoRow(stringResource(R.string.crash_summary_renderer), it) }
            s.javaVersion?.let { InfoRow(stringResource(R.string.crash_summary_java), it) }
        }

        val date = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
            .format(Date(session?.timestamp ?: System.currentTimeMillis()))
        InfoRow(stringResource(R.string.crash_summary_timestamp), date)
    }
}

@Composable
private fun CategoryChip(category: CrashCategory, severity: CrashSeverity) {
    val containerColor = when (severity) {
        CrashSeverity.CRITICAL -> MaterialTheme.colorScheme.errorContainer
        CrashSeverity.HIGH     -> MaterialTheme.colorScheme.tertiaryContainer
        CrashSeverity.MEDIUM   -> MaterialTheme.colorScheme.secondaryContainer
        CrashSeverity.LOW      -> MaterialTheme.colorScheme.surfaceVariant
        CrashSeverity.UNKNOWN  -> MaterialTheme.colorScheme.surfaceVariant
    }
    val contentColor = when (severity) {
        CrashSeverity.CRITICAL -> MaterialTheme.colorScheme.onErrorContainer
        CrashSeverity.HIGH     -> MaterialTheme.colorScheme.onTertiaryContainer
        CrashSeverity.MEDIUM   -> MaterialTheme.colorScheme.onSecondaryContainer
        else                   -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Surface(
        color = containerColor,
        contentColor = contentColor,
        shape = MaterialTheme.shapes.small
    ) {
        Text(
            text = stringResource(category.titleRes),
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

@Composable
private fun ConfidenceRow(confidence: Int, band: CrashDiagnosis.ConfidenceBand) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = stringResource(R.string.crash_summary_confidence),
            style = MaterialTheme.typography.bodyMedium
        )
        Text(
            text = "$confidence% (${
                when (band) {
                    CrashDiagnosis.ConfidenceBand.EXTREMELY_HIGH -> stringResource(R.string.crash_confidence_extremely_high)
                    CrashDiagnosis.ConfidenceBand.HIGH           -> stringResource(R.string.crash_confidence_high)
                    CrashDiagnosis.ConfidenceBand.MEDIUM         -> stringResource(R.string.crash_confidence_medium)
                    CrashDiagnosis.ConfidenceBand.LOW            -> stringResource(R.string.crash_confidence_low)
                    CrashDiagnosis.ConfidenceBand.VERY_LOW       -> stringResource(R.string.crash_confidence_very_low)
                }
            })",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Column(modifier = Modifier
        .fillMaxWidth()
        .padding(vertical = 2.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}
