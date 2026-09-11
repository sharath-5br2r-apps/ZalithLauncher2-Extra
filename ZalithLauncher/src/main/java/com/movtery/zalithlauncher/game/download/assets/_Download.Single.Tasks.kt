/*
 * Zalith Launcher 2
 * Copyright (C) 2025 MovTery <movtery228@qq.com> and contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/gpl-3.0.txt>.
 */

package com.movtery.zalithlauncher.game.download.assets

import android.content.Context
import com.movtery.zalithlauncher.R
import com.movtery.zalithlauncher.coroutine.InstallerRestoreRegistry
import com.movtery.zalithlauncher.coroutine.Task
import com.movtery.zalithlauncher.coroutine.TaskSystem
import com.movtery.zalithlauncher.game.download.assets.platform.PlatformProject
import com.movtery.zalithlauncher.game.download.assets.platform.PlatformVersion
import com.movtery.zalithlauncher.game.download.assets.platform.getVersions
import com.movtery.zalithlauncher.game.download.assets.platform.mcim.mapMCIMMirrorUrls
import com.movtery.zalithlauncher.game.version.installed.Version
import com.movtery.zalithlauncher.game.version.installed.VersionFolders
import com.movtery.zalithlauncher.game.version.profile.VersionProfileManager
import com.movtery.zalithlauncher.path.PathManager
import com.movtery.zalithlauncher.ui.AndroidStringText
import com.movtery.zalithlauncher.ui.androidText
import com.movtery.zalithlauncher.ui.toAndroidString
import com.movtery.zalithlauncher.utils.file.ensureParentDirectory
import com.movtery.zalithlauncher.utils.file.formatFileSize
import com.movtery.zalithlauncher.utils.logging.Logger
import com.movtery.zalithlauncher.utils.network.downloadFileFromSources
import com.movtery.zalithlauncher.utils.network.toLocal
import com.movtery.zalithlauncher.utils.network.withSpeedReport
import com.movtery.zalithlauncher.viewmodel.ErrorViewModel
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.plugins.ResponseException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okio.IOException
import org.apache.commons.io.FileUtils
import java.io.File
import java.net.ConnectException
import java.net.UnknownHostException
import java.nio.channels.UnresolvedAddressException

private const val TAG = "DownloadSingle"

/**
 * 为一些版本下载单独的资源文件
 * @param version 要下载单独资源版本信息
 * @param versions 为哪些游戏版本下载
 * @param folder 版本游戏目录下的相对路径
 * @param onFileCopied 文件已成功复制到版本游戏目录后 单独回调
 * @param onFileCancelled 文件安装已取消 单独回调
 */
fun downloadSingleForVersions(
    context: Context,
    version: PlatformVersion,
    versions: List<Version>,
    folder: String,
    /** 自定义安装后的文件名，用于解决重复文件命名冲突；为空则使用原始文件名（并覆盖同名文件） */
    customFileName: String? = null,
    onFileCopied: suspend (zip: File, folder: File) -> Unit = { _, _ -> },
    onFileCancelled: (zip: File, folder: File) -> Unit = { _, _ -> },
    submitError: (ErrorViewModel.ThrowableMessage) -> Unit
) {
    val fileKey = version.platformSha1() ?: version.platformFileName()
    val cacheFile = File(File(PathManager.DIR_CACHE, "assets"), fileKey)
    val targetFileName = customFileName ?: version.platformFileName()

    downloadSingleFile(
        version = version,
        taskId = downloadTaskId(fileKey, versions),
        file = cacheFile,
        onDownloaded = { task ->
            task.updateProgress(-1f)
            task.updateMessage(androidText(R.string.download_assets_install_progress_installing, version.platformFileName()))
            versions.forEach { ver ->
                val targetFolder = File(ver.getGameDir(), folder)
                val targetFile = File(targetFolder, targetFileName)
                if (targetFile.exists() && !targetFile.delete()) throw IOException("Failed to properly delete the existing target file.")
                cacheFile.copyTo(targetFile)
                onFileCopied(targetFile, targetFolder) //文件已复制回调
                // Register the newly installed file as enabled in the active Version
                // Profile. Using registerInstalledContent (rather than
                // captureCurrentState) ensures the update is atomic and surgical:
                // it marks only this file as enabled, re-enables it on disk if a
                // concurrent apply() already renamed it to .disabled, and emits
                // notifyProfileChanged so management screens refresh immediately.
                VersionProfileManager.registerInstalledContent(ver, targetFile)
            }
        },
        onError = { e ->
            Logger.warning(TAG, "An error occurred while downloading the resource files.", e)

            submitError(
                ErrorViewModel.ThrowableMessage(
                    title = androidText(R.string.download_assets_install_failed),
                    message = mapExceptionToMessage(e)
                )
            )
        },
        onCancel = {
            FileUtils.deleteQuietly(cacheFile)
            versions.forEach { ver ->
                val targetFolder = File(ver.getGameDir(), folder)
                val targetFile = File(targetFolder, targetFileName)
                if (targetFile.exists()) FileUtils.deleteQuietly(targetFile)
                onFileCancelled(targetFile, targetFolder) //文件已取消回调
            }
        },
        onFinally = {
            Logger.info(TAG, "Attempting to clear cached resource files.")
            FileUtils.deleteQuietly(cacheFile)
        }
    )
}

/**
 * 下载任务的Id
 * 同一文件安装到不同的游戏版本时属于不同的任务，避免被误判为重复任务而丢弃目标版本
 */
private fun downloadTaskId(fileKey: String, versions: List<Version>): String {
    if (versions.isEmpty()) return fileKey
    return "$fileKey|${versions.map { it.getVersionName() }.sorted().joinToString(",")}"
}

private fun downloadSingleFile(
    version: PlatformVersion,
    taskId: String,
    file: File,
    onDownloaded: suspend (Task) -> Unit,
    onError: (Throwable) -> Unit = {},
    onCancel: () -> Unit = {},
    onFinally: () -> Unit = {}
) {
    //Mods/Resource Packs/Shaders have no foreground install popup with a minimize button —
    //the download goes straight to the background Task Menu, so it must collapse the menu
    //immediately here, using the same shared implementation as the popup dialogs' onMinimize.
    InstallerRestoreRegistry.collapseTaskMenu()

    TaskSystem.submitTask(
        Task.runTask(
            id = taskId,
            task = { task ->
                val totalFileSize = version.platformFileSize()
                var downloadedSize = 0L

                //更新下载任务进度
                fun updateProgress() {
                    task.updateProgress(
                        (downloadedSize.toDouble() / totalFileSize.toDouble()).toFloat()
                    )
                    task.updateMessage(
                        androidText(
                            R.string.download_assets_install_progress_downloading,
                            version.platformFileName(),
                            formatFileSize(downloadedSize),
                            formatFileSize(totalFileSize),
                        )
                    )
                }
                updateProgress()

                withSpeedReport(
                    onSpeedReport = { bytes ->
                        task.updateSpeed(bytes)
                    },
                    onClear = {
                        task.clearSpeed()
                    }
                ) { report ->
                    downloadFileFromSources(
                        urls = version
                            .platformDownloadUrl()
                            .mapMCIMMirrorUrls(),
                        sha1 = version.platformSha1(),
                        outputFile = file.ensureParentDirectory(),
                        sizeCallback = { size ->
                            downloadedSize += size
                            updateProgress()
                            report(size)
                        }
                    )
                }

                onDownloaded(task)
            },
            onError = onError,
            onCancel = onCancel,
            onFinally = onFinally
        )
    )
}

fun mapExceptionToMessage(e: Throwable): AndroidStringText {
    return when (e) {
        is HttpRequestTimeoutException -> androidText(R.string.error_timeout)
        is UnknownHostException, is UnresolvedAddressException -> androidText(R.string.error_network_unreachable)
        is ConnectException -> androidText(R.string.error_connection_failed)
        is ResponseException -> e.toLocal()
        else -> {
            androidText(e.localizedMessage ?: e::class.simpleName ?: "Unknown error")
        }
    }
}

/**
 * Batch-downloads a list of dependency projects into the given game versions.
 *
 * For each dependency:
 * 1. Fetches all available platform versions.
 * 2. Initialises every candidate's file metadata first (some platforms require an
 *    extra network call before [PlatformVersion.platformGameVersion] / [PlatformVersion.platformLoaders]
 *    / [PlatformVersion.platformFileName] can be safely read — reading them before init
 *    throws, which used to be silently swallowed and looked like "nothing downloads").
 * 3. Filters the initialised candidates by the installed Minecraft version, then by
 *    mod loader (falling back to the unfiltered set whenever a filter eliminates everyone,
 *    since some platform listings omit loader/game-version tags on a given file).
 * 4. Picks the most recently published match and submits a background download task via
 *    [downloadSingleForVersions].
 *
 * Pre-download failures (network error fetching versions, no version found,
 * init failure) are reported via [onEachError]. Errors that occur during the
 * actual file download are forwarded to [submitError] as usual.
 *
 * @param deps     dependency entries — (dependency metadata, project metadata)
 * @param gameVersions installed game versions to install each dep into
 * @param folder   relative folder under each version's game dir (e.g. "mods")
 * @param onEachError called with (name, errorMessage) for pre-download failures
 */
suspend fun downloadDependenciesBatch(
    context: Context,
    deps: List<Pair<PlatformVersion.PlatformDependency, PlatformProject>>,
    gameVersions: List<Version>,
    folder: String,
    submitError: (ErrorViewModel.ThrowableMessage) -> Unit,
    onEachError: (name: String, error: String) -> Unit,
    onEachSkipped: (name: String) -> Unit = {}
) = withContext(Dispatchers.IO) {
    val plan = if (folder == VersionFolders.MOD.folderName) {
        planDependencyDownloads(deps, gameVersions)
    } else {
        val requirements = planDependencyRequirements(deps)
        DependencyInstallPlan(
            work = requirements.dependencies.map { (dependency, project) ->
                DependencyInstallWork(dependency, project, gameVersions.distinctBy { it.getVersionName() })
            },
            skippedDependencyNames = emptyList(),
            planningErrors = requirements.planningErrors
        )
    }

    plan.skippedDependencyNames.forEach(onEachSkipped)
    plan.planningErrors.forEach { (name, error) -> onEachError(name, error) }

    fun normalizeLoaderName(name: String) = name.filter(Char::isLetterOrDigit).lowercase()

    for (planned in plan.work) {
        val dep = planned.dependency
        val project = planned.project
        val name = project.platformTitle()
        runCatching {
            // Fetch all versions for this dependency project
            val allVersions = getVersions(
                projectID = dep.projectId,
                platform = dep.platform
            )

            // Initialise every candidate first; unusable/uninitialisable ones are dropped here
            // instead of crashing later when their metadata is read.
            val initializedVersions = allVersions.mapNotNull { ver ->
                runCatching { if (ver.initFile(dep.projectId)) ver else null }.getOrNull()
            }

            if (initializedVersions.isEmpty()) {
                onEachError(name, "No downloadable file found for dependency: $name")
                return@runCatching
            }

            val selectedByTarget = planned.targetVersions.mapNotNull { target ->
                try {
                    val targetInfo = target.getVersionInfo()
                        ?: throw IOException("Selected game instance has no Minecraft metadata")
                    val targetMinecraft = targetInfo.minecraftVersion
                    val targetLoader = targetInfo.loaderInfo?.loader?.displayName

                    // A pinned dependency is strict: never install a different file
                    // merely because the pinned file is unavailable.
                    val candidates = dep.versionId?.let { versionId ->
                        initializedVersions.filter { it.platformId().equals(versionId, ignoreCase = true) }
                            .also {
                                if (it.isEmpty()) {
                                    throw IOException("Pinned dependency version is unavailable: $versionId")
                                }
                            }
                    } ?: initializedVersions

                    val gameCompatible = candidates.filter { candidate ->
                        val supportedVersions = candidate.platformGameVersion()
                        supportedVersions.isEmpty() || targetMinecraft in supportedVersions
                    }
                    if (gameCompatible.isEmpty()) {
                        throw IOException("No compatible Minecraft version found")
                    }

                    val loaderCompatible = gameCompatible.filter { candidate ->
                        val supportedLoaders = candidate.platformLoaders()
                        targetLoader == null ||
                            supportedLoaders.isEmpty() ||
                            supportedLoaders.any { loader ->
                                val candidateName = normalizeLoaderName(loader.getDisplayName())
                                val targetName = normalizeLoaderName(targetLoader)
                                candidateName == targetName ||
                                    candidateName.contains(targetName) ||
                                    targetName.contains(candidateName)
                            }
                    }
                    if (loaderCompatible.isEmpty()) {
                        throw IOException("No compatible loader version found")
                    }

                    target to (loaderCompatible.maxByOrNull { it.platformDatePublished() }
                        ?: throw IOException("No available version found"))
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    val targetName = "${name} [${target.getVersionName()}]"
                    onEachError(targetName, mapExceptionToMessage(e).toAndroidString(context))
                    null
                }
            }

            if (selectedByTarget.isEmpty()) return@runCatching

            // Group instances that selected the same file so one download task
            // still serves all compatible targets without copying across
            // incompatible instances.
            selectedByTarget
                .groupBy { (_, selected) ->
                    selected.platformSha1() ?: selected.platformId()
                }
                .values
                .forEach { selections ->
                    val selected = selections.first().second
                    downloadSingleForVersions(
                        context = context,
                        version = selected,
                        versions = selections.map { it.first },
                        folder = folder,
                        submitError = submitError
                    )
                }
        }.onFailure { e ->
            if (e !is CancellationException) {
                Logger.warning(TAG, "Failed to prepare batch download for dependency: $name", e)
                val msg = mapExceptionToMessage(e).toAndroidString(context)
                onEachError(name, msg)
            }
        }
    }
}
