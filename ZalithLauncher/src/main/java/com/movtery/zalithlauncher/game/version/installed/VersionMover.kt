package com.movtery.zalithlauncher.game.version.installed

import android.content.Context
import com.movtery.zalithlauncher.R
import com.movtery.zalithlauncher.coroutine.TaskFlowExecutor
import com.movtery.zalithlauncher.coroutine.TitledTask
import com.movtery.zalithlauncher.coroutine.addTask
import com.movtery.zalithlauncher.coroutine.buildPhase
import com.movtery.zalithlauncher.ui.androidText
import com.movtery.zalithlauncher.ui.screens.content.elements.GameFolderOperation
import com.movtery.zalithlauncher.utils.logging.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import org.apache.commons.io.FileUtils
import java.io.File

private const val TAG = "VersionMover"

class VersionMover(
    private val context: Context,
    scope: CoroutineScope,
    private val versions: List<Version>,
    private val sourceGameHome: String,
    private val targetGamePath: String,
    private val changeState: (GameFolderOperation) -> Unit
) {
    private val taskExecutor = TaskFlowExecutor(scope)
    val tasksFlow: StateFlow<List<TitledTask>> = taskExecutor.tasksFlow

    private val movedVersions = mutableListOf<String>()
    private val failedVersions = mutableListOf<Pair<String, String>>()

    fun start(
        onEnd: (moved: List<String>, failed: List<Pair<String, String>>) -> Unit,
        onThrowable: (Throwable) -> Unit
    ) {
        taskExecutor.executePhasesAsync(
            onStart = {
                movedVersions.clear()
                failedVersions.clear()
                val phases = getTaskPhases()
                taskExecutor.addPhases(phases)
            },
            onComplete = {
                changeState(GameFolderOperation.None)
                onEnd(movedVersions.toList(), failedVersions.toList())
            },
            onError = onThrowable
        )
    }

    fun cancel() {
        taskExecutor.cancel()
    }

    fun isRunning(): Boolean = taskExecutor.isRunning()

    private suspend fun getTaskPhases() = withContext(Dispatchers.IO) {
        listOf(
            buildPhase {
                addTask(
                    id = "VersionMover.MoveVersions",
                    title = androidText(R.string.versions_manage_move_versions_progress),
                    icon = R.drawable.ic_autorenew,
                    dispatcher = Dispatchers.IO
                ) { task ->
                    task.updateProgress(-1f)
                    val total = versions.size

                    versions.forEachIndexed { index, version ->
                        ensureActive()
                        val name = version.getVersionName()
                        task.updateMessage(androidText(R.string.versions_manage_move_versions_moving, name))

                        val sourceDir = version.getVersionPath()
                        val targetDir = File(File(targetGamePath, "versions"), name)

                        try {
                            runCatching {
                                moveVersionDirectory(sourceDir, targetDir, version, name)
                                movedVersions.add(name)
                            }.onFailure { e ->
                                Logger.error(TAG, "Failed to move version $name", e)
                                failedVersions.add(name to (e.message ?: e.javaClass.simpleName))
                                if (targetDir.exists()) {
                                    FileUtils.deleteQuietly(targetDir)
                                }
                            }
                        } catch (e: Exception) {
                            ensureActive()
                            throw e
                        }

                        task.updateProgress(
                            percentage = (index + 1).toFloat() / total.toFloat()
                        )
                    }

                    copyAssets()
                    task.updateProgress(-1f)
                }
            }
        )
    }

    private fun moveVersionDirectory(
        sourceDir: File,
        targetDir: File,
        version: Version,
        name: String
    ) {
        if (targetDir.exists()) {
            FileUtils.deleteQuietly(targetDir)
        }
        targetDir.mkdirs()
        FileUtils.copyDirectory(sourceDir, targetDir)
        FileUtils.deleteQuietly(sourceDir)

        version.getVersionConfig().apply {
            setVersionPath(targetDir)
            saveWithThrowable()
        }
    }

    private suspend fun copyAssets() {
        movedVersions.forEach { name ->
            val sourceIndex = File(File(sourceGameHome, "assets/indexes"), "${name}.json")
            val targetIndex = File(File(targetGamePath, "assets/indexes"), "${name}.json")
            if (sourceIndex.exists()) {
                targetIndex.parentFile.mkdirs()
                FileUtils.copyFile(sourceIndex, targetIndex)
            }

            val sourceResources = File(File(sourceGameHome, "resources"), name)
            val targetResources = File(File(targetGamePath, "resources"), name)
            if (sourceResources.exists()) {
                targetResources.parentFile.mkdirs()
                FileUtils.copyDirectory(sourceResources, targetResources)
            }
        }
    }
}
