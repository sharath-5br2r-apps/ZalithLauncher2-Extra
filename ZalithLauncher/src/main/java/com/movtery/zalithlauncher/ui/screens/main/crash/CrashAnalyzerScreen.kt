/*
 * Zalith Launcher 2 — Zeryth Fork
 * Crash Analyzer: Main Screen
 */

package com.movtery.zalithlauncher.ui.screens.main.crash

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.movtery.zalithlauncher.R
import com.movtery.zalithlauncher.game.crash.model.CrashDiagnosis
import com.movtery.zalithlauncher.ui.components.verticalScrollWithBar
import com.movtery.zalithlauncher.viewmodel.CrashAnalyzerViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CrashAnalyzerScreen(
    viewModel: CrashAnalyzerViewModel,
    onShowLogsClick: () -> Unit,
    onShareLogsClick: () -> Unit,
    onCopySummaryClick: () -> Unit,
    onCopyTechnicalClick: () -> Unit,
    onShareReportClick: () -> Unit,
    onRestartClick: () -> Unit,
    onExitClick: () -> Unit
) {
    val snackbarHostState = remember { SnackbarHostState() }

    // Show repair result as snackbar
    LaunchedEffect(viewModel.repairResult) {
        viewModel.repairResult?.let { result ->
            val msg = when (result) {
                is com.movtery.zalithlauncher.game.crash.CrashRepairExecutor.RepairResult.Success -> result.message
                is com.movtery.zalithlauncher.game.crash.CrashRepairExecutor.RepairResult.Failure -> "⚠ ${result.reason}"
            }
            snackbarHostState.showSnackbar(msg)
            viewModel.dismissRepairResult()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.crash_analyzer_title)) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                ),
                actions = {
                    IconButton(onClick = onShowLogsClick) {
                        Icon(
                            painter = painterResource(R.drawable.ic_description_outlined),
                            contentDescription = stringResource(R.string.crash_show_logs)
                        )
                    }
                    IconButton(onClick = onShareLogsClick) {
                        Icon(
                            painter = painterResource(R.drawable.ic_share_filled),
                            contentDescription = stringResource(R.string.crash_share_logs)
                        )
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->

        AnimatedContent(
            targetState = viewModel.analysisState,
            transitionSpec = {
                fadeIn(tween(300)) togetherWith fadeOut(tween(200))
            },
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
        ) { state ->
            when (state) {
                is CrashAnalyzerViewModel.AnalysisState.Idle,
                is CrashAnalyzerViewModel.AnalysisState.Analyzing -> {
                    AnalyzingPlaceholder()
                }

                is CrashAnalyzerViewModel.AnalysisState.Error -> {
                    ErrorPlaceholder(
                        message = state.message,
                        onShowLogsClick = onShowLogsClick,
                        onExitClick = onExitClick
                    )
                }

                is CrashAnalyzerViewModel.AnalysisState.Complete -> {
                    val diagnosis = viewModel.diagnosis
                    if (diagnosis != null) {
                        DiagnosisContent(
                            viewModel = viewModel,
                            diagnosis = diagnosis,
                            onCopySummaryClick = onCopySummaryClick,
                            onCopyTechnicalClick = onCopyTechnicalClick,
                            onShareReportClick = onShareReportClick,
                            onRestartClick = onRestartClick,
                            onExitClick = onExitClick
                        )
                    } else {
                        AnalyzingPlaceholder()
                    }
                }
            }
        }
    }
}

@Composable
private fun AnalyzingPlaceholder() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator()
            Spacer(Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.crash_analyzer_analyzing),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
private fun ErrorPlaceholder(
    message: String,
    onShowLogsClick: () -> Unit,
    onExitClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_warning_filled),
            contentDescription = null,
            modifier = Modifier.size(48.dp),
            tint = MaterialTheme.colorScheme.error
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.crash_analyzer_error),
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(24.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onShowLogsClick) {
                Text(stringResource(R.string.crash_show_logs))
            }
            FilledTonalButton(onClick = onExitClick) {
                Text(stringResource(R.string.crash_exit))
            }
        }
    }
}

@Composable
private fun DiagnosisContent(
    viewModel: CrashAnalyzerViewModel,
    diagnosis: CrashDiagnosis,
    onCopySummaryClick: () -> Unit,
    onCopyTechnicalClick: () -> Unit,
    onShareReportClick: () -> Unit,
    onRestartClick: () -> Unit,
    onExitClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScrollWithBar(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Plain language / Technical toggle
        ViewModeToggle(
            showTechnical = viewModel.showTechnicalView,
            onToggle = { viewModel.showTechnicalView = !viewModel.showTechnicalView }
        )

        // Summary card
        CrashSummaryCard(
            diagnosis = diagnosis,
            session = viewModel.session,
            isExpanded = "summary" in viewModel.expandedCards,
            onToggle = { viewModel.toggleCard("summary") }
        )

        // Evidence card
        CrashEvidenceCard(
            diagnosis = diagnosis,
            showTechnical = viewModel.showTechnicalView,
            isExpanded = "evidence" in viewModel.expandedCards,
            onToggle = { viewModel.toggleCard("evidence") }
        )

        // Startup timeline
        AnimatedVisibility(visible = diagnosis.startupStage != CrashDiagnosis.StartupStage.UNKNOWN) {
            CrashTimelineCard(
                crashStage = diagnosis.startupStage,
                isExpanded = "timeline" in viewModel.expandedCards,
                onToggle = { viewModel.toggleCard("timeline") }
            )
        }

        // Recommended repairs
        AnimatedVisibility(visible = diagnosis.recommendedRepairs.isNotEmpty()) {
            CrashRepairsCard(
                diagnosis = diagnosis,
                onRepairClick = { action ->
                    viewModel.pendingRepair = action
                },
                isExpanded = "repairs" in viewModel.expandedCards,
                onToggle = { viewModel.toggleCard("repairs") }
            )
        }

        // Action buttons
        ReportActions(
            onCopySummaryClick = onCopySummaryClick,
            onCopyTechnicalClick = onCopyTechnicalClick,
            onShareReportClick = onShareReportClick
        )

        ActionButtons(
            onRestartClick = onRestartClick,
            onExitClick = onExitClick
        )
    }

    // Repair confirmation dialog
    val context = androidx.compose.ui.platform.LocalContext.current
    viewModel.pendingRepair?.let { action ->
        RepairConfirmationDialog(
            action = action,
            onConfirm = {
                viewModel.executeRepair(context, action)
            },
            onDismiss = { viewModel.pendingRepair = null }
        )
    }
}

@Composable
private fun ReportActions(
    onCopySummaryClick: () -> Unit,
    onCopyTechnicalClick: () -> Unit,
    onShareReportClick: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = stringResource(R.string.crash_report_actions),
            style = MaterialTheme.typography.titleSmall
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(onClick = onCopySummaryClick, modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.crash_copy_summary))
            }
            OutlinedButton(onClick = onCopyTechnicalClick, modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.crash_copy_technical))
            }
        }
        FilledTonalButton(
            onClick = onShareReportClick,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(stringResource(R.string.crash_share_report))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ViewModeToggle(showTechnical: Boolean, onToggle: () -> Unit) {
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        SegmentedButton(
            selected = !showTechnical,
            onClick = { if (showTechnical) onToggle() },
            shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
            label = { Text(stringResource(R.string.crash_view_plain)) }
        )
        SegmentedButton(
            selected = showTechnical,
            onClick = { if (!showTechnical) onToggle() },
            shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
            label = { Text(stringResource(R.string.crash_view_technical)) }
        )
    }
}

@Composable
private fun ActionButtons(
    onRestartClick: () -> Unit,
    onExitClick: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)
    ) {
        OutlinedButton(onClick = onExitClick) {
            Text(stringResource(R.string.crash_exit))
        }
        Spacer(Modifier.width(4.dp))
        FilledTonalButton(onClick = onRestartClick) {
            Text(stringResource(R.string.crash_restart))
        }
    }
}
