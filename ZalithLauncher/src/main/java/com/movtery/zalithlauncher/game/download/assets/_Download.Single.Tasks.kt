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
import com.movtery.zalithlauncher.game.download.assets.platform.PlatformVersion
import com.movtery.zalithlauncher.game.download.assets.platform.getVersions
import com.movtery.zalithlauncher.game.download.assets.platform.mcim.mapMCIMMirrorUrls
import com.movtery.zalithlauncher.game.version.installed.Version
import com.movtery.zalithlauncher.path.PathManager
import com.movtery.zalithlauncher.ui.AndroidStringText
import com.movtery.zalithlauncher.ui.androidText
import com.movtery.zalithlauncher.utils.file.ensureParentDirectory
import com.movtery.zalithlauncher.utils.file.formatFileSize
import com.movtery.zalithlauncher.utils.logging.Logger
import com.movtery.zalithlauncher.utils.network.downloadFileFromSources
import com.movtery.zalithlauncher.utils.network.toLocal
import com.movtery.zalithlauncher.utils.network.withSpeedReport
import com.movtery.zalithlauncher.viewmodel.ErrorViewModel
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.plugins.ResponseException
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
    onFileCopied: suspend (zip: File, folder: File) -> Unit = { _, _ -> },
    onFileCancelled: (zip: File, folder: File) -> Unit = { _, _ -> },
    submitError: (ErrorViewModel.ThrowableMessage) -> Unit
) {
    val cacheFile = File(File(PathManager.DIR_CACHE, "assets"), version.platformSha1() ?: version.platformFileName())

    downloadSingleFile(
        version = version,
        file = cacheFile,
        onDownloaded = { task ->
            task.updateProgress(-1f)
            task.updateMessage(androidText(R.string.download_assets_install_progress_installing, version.platformFileName()))
            versions.forEach { ver ->
                val targetFolder = File(ver.getGameDir(), folder)
                val targetFile = File(targetFolder, version.platformFileName())
                if (targetFile.exists() && !targetFile.delete()) throw IOException("Failed to properly delete the existing target file.")
                cacheFile.copyTo(targetFile)
                onFileCopied(targetFile, targetFolder) //文件已复制回调
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
                val targetFile = File(targetFolder, version.platformFileName())
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

private fun downloadSingleFile(
    version: PlatformVersion,
    file: File,
    onDownloaded: suspend (Task) -> Unit,
    onError: (Throwable) -> Unit = {},
    onCancel: () -> Unit = {},
    onFinally: () -> Unit = {}
) {

    InstallerRestoreRegistry.collapseTaskMenu() 

    TaskSystem.submitTask(
        Task.runTask(
            id = version.platformSha1() ?: version.platformFileName(),
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
 * 批量下载依赖资源
 * @param deps 需要下载的依赖
 * @param gameVersions 安装到的游戏版本
 * @param folder 目标文件夹
 * @param targetLoaders 目标加载器列表 (如 "fabric", "neoforge", "forge")，用于过滤依赖版本
 * @param onEachError 每个依赖下载失败时的回调
 */
suspend fun downloadDependenciesBatch(
    context: Context,
    deps: List<PlatformVersion.PlatformDependency>,
    gameVersions: List<Version>,
    folder: String,
    submitError: (ErrorViewModel.ThrowableMessage) -> Unit,
    targetLoaders: Set<String> = emptySet(),
    onEachError: (name: String, error: String) -> Unit = { _, _ -> }
) {
    deps.forEach { dep ->
        try {
            val versions = getVersions(
                projectID = dep.projectId,
                platform = dep.platform
            ) as List<PlatformVersion>

            val targetGameVers = gameVersions.mapNotNull { it.getVersionInfo()?.minecraftVersion }

            //先尝试初始化每个候选版本，未初始化的版本无法读取文件名、下载链接等信息，
            //之前这里直接调用 platformGameVersion()/platformFileName() 而未初始化，会导致
            //lateinit 属性未初始化异常，被下方的 catch 静默吞掉，看起来就像"点击后什么也没下载"
            val initializedVersions = versions.mapNotNull { ver ->
                runCatching {
                    if (ver.initFile(dep.projectId)) ver else null
                }.getOrNull()
            }

            if (initializedVersions.isEmpty()) {
                onEachError(dep.projectId, "No downloadable file found for this dependency")
                return@forEach
            }

            var matchingVersions = initializedVersions

            //先按游戏版本过滤
            if (targetGameVers.isNotEmpty()) {
                matchingVersions = matchingVersions.filter { ver ->
                    ver.platformGameVersion().any { it in targetGameVers }
                }
            }

            //再按加载器过滤: 如果版本指定了加载器，只保留匹配目标加载器的
            if (targetLoaders.isNotEmpty()) {
                val filteredByLoader = matchingVersions.filter { ver ->
                    val loaders = ver.platformLoaders()
                    loaders.isEmpty() || loaders.any { loader ->
                        targetLoaders.any { target ->
                            loader.getDisplayName().replace(" ", "").replace("-", "").lowercase()
                                .contains(target.lowercase()) ||
                            target.contains(loader.getDisplayName().replace(" ", "").replace("-", "").lowercase())
                        }
                    }
                }
                if (filteredByLoader.isNotEmpty()) {
                    matchingVersions = filteredByLoader
                }
            }

            val matchingVersion = matchingVersions.firstOrNull() ?: initializedVersions.firstOrNull() ?: run {
                onEachError(dep.projectId, "No matching version found for ${targetGameVers.joinToString()}")
                return@forEach
            }

            downloadSingleForVersions(
                context = context,
                version = matchingVersion,
                versions = gameVersions,
                folder = folder,
                submitError = submitError
            )
        } catch (e: Exception) {
            Logger.warning(TAG, "Failed to download dependency ${dep.projectId} while batch downloading all dependencies.", e)
            onEachError(dep.projectId, e.message ?: e.javaClass.simpleName)
        }
    }
}
