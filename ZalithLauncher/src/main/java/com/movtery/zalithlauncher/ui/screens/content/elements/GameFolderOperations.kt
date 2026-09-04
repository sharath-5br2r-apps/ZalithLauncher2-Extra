package com.movtery.zalithlauncher.ui.screens.content.elements

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.movtery.zalithlauncher.R
import com.movtery.zalithlauncher.game.path.GamePathManager
import com.movtery.zalithlauncher.game.version.installed.Version
import com.movtery.zalithlauncher.game.version.installed.VersionMover
import com.movtery.zalithlauncher.ui.components.MarqueeText

sealed interface GameFolderOperation {
    data object None : GameFolderOperation
    data object MoveVersionsSelect : GameFolderOperation
    data class MoveVersionsTarget(val versions: List<Version>) : GameFolderOperation
    data class MoveVersionsSummary(val versions: List<Version>, val targetPath: String) : GameFolderOperation
    data class MoveVersionsProgress(val mover: VersionMover) : GameFolderOperation
    data class MoveVersionsResult(val moved: List<String>, val failed: List<Pair<String, String>>) : GameFolderOperation
}

@Composable
fun GameFolderOperationDialog(
    operation: GameFolderOperation,
    changeState: (GameFolderOperation) -> Unit,
    versions: List<Version>,
    onStartMove: (List<Version>, String) -> Unit,
) {
    when (operation) {
        is GameFolderOperation.None -> {}
        is GameFolderOperation.MoveVersionsSelect -> MoveVersionsSelectStep(
            versions = versions,
            onNext = { selected -> changeState(GameFolderOperation.MoveVersionsTarget(selected)) },
            onCancel = { changeState(GameFolderOperation.None) }
        )
        is GameFolderOperation.MoveVersionsTarget -> MoveVersionsTargetStep(
            onNext = { target -> changeState(GameFolderOperation.MoveVersionsSummary(operation.versions, target)) },
            onCancel = { changeState(GameFolderOperation.None) }
        )
        is GameFolderOperation.MoveVersionsSummary -> MoveVersionsSummaryStep(
            versions = operation.versions,
            targetPath = operation.targetPath,
            onFinish = {
                changeState(GameFolderOperation.None)
                onStartMove(operation.versions, operation.targetPath)
            },
            onCancel = { changeState(GameFolderOperation.None) }
        )
        is GameFolderOperation.MoveVersionsProgress -> {
            val mover = operation.mover
            val tasks by mover.tasksFlow.collectAsStateWithLifecycle()
            if (tasks.isNotEmpty()) {
                TitleTaskFlowDialog(
                    title = stringResource(R.string.versions_manage_move_versions_progress),
                    tasks = tasks,
                    onCancel = {
                        mover.cancel()
                        changeState(GameFolderOperation.None)
                    }
                )
            }
        }
        is GameFolderOperation.MoveVersionsResult -> {
            MoveVersionsResultDialog(
                moved = operation.moved,
                failed = operation.failed,
                onDismiss = { changeState(GameFolderOperation.None) }
            )
        }
    }
}

@Composable
private fun MoveVersionsSelectStep(
    versions: List<Version>,
    onNext: (List<Version>) -> Unit,
    onCancel: () -> Unit
) {
    val movableVersions = remember(versions) {
        versions.filter { it.isIsolation() }
    }
    var selectedVersions by remember { mutableStateOf(setOf<Version>()) }
    var showError by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(stringResource(R.string.versions_manage_move_versions_select_versions)) },
        text = {
            Column {
                if (movableVersions.isEmpty()) {
                    Text(stringResource(R.string.versions_manage_move_versions_no_versions))
                } else {
                    LazyColumn(modifier = Modifier.height(300.dp)) {
                        items(movableVersions, key = { it.getVersionName() }) { version ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = version in selectedVersions,
                                    onClick = {
                                        selectedVersions = if (version in selectedVersions) {
                                            emptySet()
                                        } else {
                                            setOf(version)
                                        }
                                    }
                                )
                                Text(
                                    modifier = Modifier.padding(start = 8.dp),
                                    text = version.getVersionName(),
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }
                    }
                    if (showError && selectedVersions.isEmpty()) {
                        Text(
                            text = stringResource(R.string.generic_cannot_empty),
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                enabled = movableVersions.isNotEmpty(),
                onClick = {
                    if (selectedVersions.isEmpty()) {
                        showError = true
                    } else {
                        onNext(selectedVersions.toList())
                    }
                }
            ) {
                MarqueeText(text = stringResource(R.string.versions_manage_move_versions_next))
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onCancel) {
                MarqueeText(text = stringResource(R.string.generic_cancel))
            }
        }
    )
}

@Composable
private fun MoveVersionsTargetStep(
    onNext: (String) -> Unit,
    onCancel: () -> Unit
) {
    val gamePaths by GamePathManager.gamePathData.collectAsStateWithLifecycle()
    val currentPath by GamePathManager.currentPath.collectAsStateWithLifecycle()
    var selectedPath by remember { mutableStateOf("") }
    var showError by remember { mutableStateOf(false) }

    val availablePaths = remember(gamePaths, currentPath) {
        gamePaths.filter { it.path != currentPath }
    }

    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(stringResource(R.string.versions_manage_move_versions_select_target)) },
        text = {
            Column {
                if (availablePaths.isEmpty()) {
                    Text(stringResource(R.string.versions_manage_move_versions_no_versions))
                } else {
                    LazyColumn(modifier = Modifier.height(300.dp)) {
                        items(availablePaths, key = { it.id }) { path ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = selectedPath == path.path,
                                    onClick = { selectedPath = path.path }
                                )
                                Text(
                                    modifier = Modifier.padding(start = 8.dp),
                                    text = path.title.ifEmpty { stringResource(R.string.generic_default) },
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }
                    }
                    if (showError && selectedPath.isEmpty()) {
                        Text(
                            text = stringResource(R.string.generic_cannot_empty),
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                enabled = availablePaths.isNotEmpty(),
                onClick = {
                    if (selectedPath.isEmpty()) {
                        showError = true
                    } else {
                        onNext(selectedPath)
                    }
                }
            ) {
                MarqueeText(text = stringResource(R.string.versions_manage_move_versions_next))
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onCancel) {
                MarqueeText(text = stringResource(R.string.generic_cancel))
            }
        }
    )
}

@Composable
private fun MoveVersionsSummaryStep(
    versions: List<Version>,
    targetPath: String,
    onFinish: () -> Unit,
    onCancel: () -> Unit
) {
    val gamePaths by GamePathManager.gamePathData.collectAsStateWithLifecycle()
    val currentPath by GamePathManager.currentPath.collectAsStateWithLifecycle()

    val targetTitle = remember(gamePaths, targetPath) {
        gamePaths.find { it.path == targetPath }?.title?.ifEmpty { "Default" } ?: targetPath
    }
    val sourceTitle = remember(gamePaths, currentPath) {
        gamePaths.find { it.path == currentPath }?.title?.ifEmpty { "Default" } ?: currentPath
    }

    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(stringResource(R.string.versions_manage_move_versions_summary)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
                versions.forEach { version ->
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
                    ) {
                        Text(
                            text = version.getVersionName(),
                            style = MaterialTheme.typography.titleMedium
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = sourceTitle,
                                style = MaterialTheme.typography.bodySmall
                            )
                            Text(
                                modifier = Modifier.padding(horizontal = 8.dp),
                                text = "↓",
                                style = MaterialTheme.typography.bodySmall
                            )
                            Text(
                                text = targetTitle,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                    if (version != versions.last()) {
                        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onFinish) {
                MarqueeText(text = stringResource(R.string.versions_manage_move_versions_finish))
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onCancel) {
                MarqueeText(text = stringResource(R.string.generic_cancel))
            }
        }
    )
}

@Composable
private fun MoveVersionsResultDialog(
    moved: List<String>,
    failed: List<Pair<String, String>>,
    onDismiss: () -> Unit
) {
    var dismissed by remember { mutableStateOf(false) }

    if (dismissed) return

    AlertDialog(
        onDismissRequest = {
            dismissed = true
            onDismiss()
        },
        title = {
            Text(
                text = if (failed.isEmpty()) {
                    stringResource(R.string.versions_manage_move_versions_done)
                } else {
                    stringResource(R.string.versions_manage_move_versions_failed)
                }
            )
        },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                if (moved.isNotEmpty()) {
                    Text(
                        text = "Moved: ${moved.size} version(s)",
                        style = MaterialTheme.typography.labelLarge
                    )
                    moved.forEach { name ->
                        Text(
                            modifier = Modifier.padding(start = 8.dp),
                            text = "✓ $name",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
                if (failed.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "Failed: ${failed.size} version(s)",
                        style = MaterialTheme.typography.labelLarge
                    )
                    failed.forEach { (name, reason) ->
                        Text(
                            modifier = Modifier.padding(start = 8.dp),
                            text = "✗ $name: $reason",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                dismissed = true
                onDismiss()
            }) {
                MarqueeText(text = stringResource(R.string.generic_done))
            }
        }
    )
}
