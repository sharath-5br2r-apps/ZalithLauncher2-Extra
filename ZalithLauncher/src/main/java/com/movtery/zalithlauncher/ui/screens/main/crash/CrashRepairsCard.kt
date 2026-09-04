/*
 * Zalith Launcher 2 — Zeryth Fork
 * Crash Analyzer: Recommended Repairs Card
 */

package com.movtery.zalithlauncher.ui.screens.main.crash

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.movtery.zalithlauncher.R
import com.movtery.zalithlauncher.game.crash.model.CrashDiagnosis
import com.movtery.zalithlauncher.game.crash.model.RepairAction


@Composable
fun CrashRepairsCard(
    diagnosis: CrashDiagnosis,
    onRepairClick: (RepairAction) -> Unit,
    isExpanded: Boolean,
    onToggle: () -> Unit
) {
    ExpandableAnalyzerCard(
        title = stringResource(R.string.crash_repairs_title),
        isExpanded = isExpanded,
        onToggle = onToggle
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            diagnosis.recommendedRepairs.forEachIndexed { index, action ->
                RepairActionCard(action = action, rank = index + 1, onRepairClick = onRepairClick)
                if (index < diagnosis.recommendedRepairs.lastIndex) {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                }
            }
        }
    }
}

@Composable
private fun RepairActionCard(
    action: RepairAction,
    rank: Int,
    onRepairClick: (RepairAction) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "#$rank ${action.label}",
                style = MaterialTheme.typography.titleSmall
            )
            Text(
                text = "${action.estimatedSuccessRate}% success",
                style = MaterialTheme.typography.labelSmall,
                color = when {
                    action.estimatedSuccessRate >= 70 -> MaterialTheme.colorScheme.primary
                    action.estimatedSuccessRate >= 50 -> MaterialTheme.colorScheme.onSurface
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
        }

        Text(
            text = action.description,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface
        )

        Text(
            text = action.rationale,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (action.isReversible)
                    stringResource(R.string.crash_repair_reversible)
                else
                    stringResource(R.string.crash_repair_irreversible),
                style = MaterialTheme.typography.labelSmall,
                color = if (action.isReversible)
                    MaterialTheme.colorScheme.primary
                else
                    MaterialTheme.colorScheme.error
            )

            FilledTonalButton(
                onClick = { onRepairClick(action) },
                modifier = Modifier.height(32.dp)
            ) {
                Text(
                    text = stringResource(R.string.crash_repair_apply),
                    style = MaterialTheme.typography.labelMedium
                )
            }
        }
    }
}

@Composable
fun RepairConfirmationDialog(
    action: RepairAction,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(stringResource(R.string.crash_repair_confirm_title))
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("${stringResource(R.string.crash_repair_confirm_action)}: ${action.label}")
                Text(action.description, style = MaterialTheme.typography.bodySmall)
                Text(
                    text = if (action.isReversible)
                        stringResource(R.string.crash_repair_confirm_reversible)
                    else
                        stringResource(R.string.crash_repair_confirm_irreversible),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (action.isReversible)
                        MaterialTheme.colorScheme.primary
                    else
                        MaterialTheme.colorScheme.error
                )
            }
        },
        confirmButton = {
            FilledTonalButton(onClick = onConfirm) {
                Text(stringResource(R.string.crash_repair_confirm_yes))
            }
        },
        dismissButton = {
            androidx.compose.material3.OutlinedButton(onClick = onDismiss) {
                Text(stringResource(R.string.generic_cancel))
            }
        }
    )
}
