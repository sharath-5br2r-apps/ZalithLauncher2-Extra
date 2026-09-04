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

package com.movtery.zalithlauncher.game.versioninfo

import com.google.gson.reflect.TypeToken
import com.movtery.zalithlauncher.game.addons.mirror.mapBMCLMirrorUrls
import com.movtery.zalithlauncher.game.versioninfo.models.GameManifest
import com.movtery.zalithlauncher.game.versioninfo.models.VersionManifest
import com.movtery.zalithlauncher.game.versioninfo.models.mapVersion
import com.movtery.zalithlauncher.path.PathManager
import com.movtery.zalithlauncher.path.URL_MINECRAFT_VERSION_REPOS
import com.movtery.zalithlauncher.utils.GSON
import com.movtery.zalithlauncher.utils.file.readString
import com.movtery.zalithlauncher.utils.logging.Logger
import com.movtery.zalithlauncher.utils.network.fetchStringFromUrls
import com.movtery.zalithlauncher.utils.network.withRetry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import org.apache.commons.io.FileUtils
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

private const val TAG = "MinecraftVersions"

object MinecraftVersions {
    private var manifest: VersionManifest? = null

    private val _allVersions = MutableStateFlow<List<MinecraftVersion>>(emptyList())
    val allVersions = _allVersions.asStateFlow()

    /** In-memory session cache: versionId → estimated download size in bytes */
    private val sizeCache = ConcurrentHashMap<String, Long>()

    /**
     * Returns the total download size (client JAR + required libraries + all game assets)
     * for a version. The calculation mirrors [com.movtery.zalithlauncher.game.version.download.BaseMinecraftDownloader]
     * so the displayed value represents the true download payload for a fresh install.
     *
     * Library size logic follows the downloader exactly:
     * - org.lwjgl is excluded (bundled by the launcher).
     * - Modern libraries (with a `downloads.artifact` block): use `artifact.size`.
     * - Legacy libraries (no `downloads` block): use the top-level `size` field.
     * - Libraries whose `downloads` block is present but has no `artifact` are skipped
     *   (the downloader skips them too).
     *
     * Results are cached in memory for the session so repeated calls are instant.
     * Returns null if the size cannot be determined.
     */
    suspend fun getVersionDownloadSize(versionId: String, versionUrl: String): Long? {
        sizeCache[versionId]?.let { return it }
        return withContext(Dispatchers.IO) {
            runCatching {
                val json = fetchStringFromUrls(versionUrl.mapBMCLMirrorUrls())
                val gameManifest = GSON.fromJson(json, GameManifest::class.java)

                val clientSize = gameManifest.downloads?.client?.size ?: 0L
                val assetTotalSize = gameManifest.rawAssetIndex?.totalSize ?: 0L

                // Sum library sizes using the same logic as BaseMinecraftDownloader.loadLibraryDownloads()
                val librariesSize = gameManifest.libraries
                    ?.filter { library -> !library.name.startsWith("org.lwjgl") }
                    ?.sumOf { library ->
                        when {
                            // Modern format: downloads block with an artifact entry
                            library.downloads?.artifact != null ->
                                library.downloads.artifact.size.takeIf { it > 0L } ?: 0L
                            // Legacy format: no downloads block at all — fall back to top-level size
                            library.downloads == null ->
                                library.size.takeIf { it > 0L } ?: 0L
                            // downloads present but no artifact — downloader skips these
                            else -> 0L
                        }
                    } ?: 0L

                val total = clientSize + assetTotalSize + librariesSize
                if (total > 0L) sizeCache[versionId] = total
                total.takeIf { it > 0L }
            }.getOrElse { e ->
                Logger.warning(TAG, "Failed to fetch download size for $versionId", e)
                null
            }
        }
    }

    /**
     * 刷新Minecraft版本的版本号列表
     * @param force 强制下载更新版本列表
     */
    @Throws(IllegalStateException::class)
    suspend fun refreshVersions(force: Boolean = false) {
        if (!force && _allVersions.value.isNotEmpty()) return

        val vm = if (force) {
            getVersionManifest(force = true)
        } else {
            manifest ?: getVersionManifest(force = false)
        }
        val versions = vm.versions.mapVersion()
        _allVersions.update { versions }
    }

    /**
     * 获取Minecraft版本信息列表
     * @param force 强制下载更新版本列表
     */
    @Throws(IllegalStateException::class)
    suspend fun getVersionManifest(force: Boolean = false): VersionManifest {
        manifest?.takeIf { !force }?.let { return it }

        return withContext(Dispatchers.IO) {
            val localManifestFile = PathManager.FILE_MINECRAFT_VERSIONS
            val isOutdated = !localManifestFile.exists() || !localManifestFile.isFile ||
                    //一天更新一次版本信息列表
                    localManifestFile.lastModified() + TimeUnit.DAYS.toMillis(1) < System.currentTimeMillis()

            val newManifest = if (force || isOutdated) {
                downloadVersionManifest()
            } else {
                try {
                    GSON.fromJson(localManifestFile.readText(), VersionManifest::class.java)
                } catch (e: Exception) {
                    Logger.warning(TAG, "Failed to parse version manifest, will redownload", e)
                    //读取失败则删除当前的版本信息文件
                    FileUtils.deleteQuietly(localManifestFile)
                    downloadVersionManifest()
                }
            }

            val newManifest0 = newManifest ?: throw IllegalStateException("Version manifest is null after all attempts")
            mergeUnlistVersions(newManifest0) ?: newManifest0
        }.also { newManifest ->
            manifest = newManifest
        }
    }

    /**
     * 从官方版本仓库获取版本信息
     */
    private suspend fun downloadVersionManifest(): VersionManifest {
        return withContext(Dispatchers.IO) {
            withRetry("MinecraftVersions", maxRetries = 2) {
                val rawJson = fetchStringFromUrls(URL_MINECRAFT_VERSION_REPOS.mapBMCLMirrorUrls())
                val versionManifest = GSON.fromJson(rawJson, VersionManifest::class.java)
                PathManager.FILE_MINECRAFT_VERSIONS.writeText(rawJson)
                versionManifest
            }
        }
    }

    /**
     * 尝试从本地合并官方隐藏的版本
     */
    private suspend fun mergeUnlistVersions(
        currentManifest: VersionManifest
    ): VersionManifest? {
        return withContext(Dispatchers.IO) {
            MinecraftVersions::class.java.getResourceAsStream("/assets/game/unlist_versions.json")?.use { input ->
                input.readString()
            }?.let { unlistVersionJson ->
                GSON.fromJson<List<VersionManifest.Version>>(
                    unlistVersionJson,
                    object : TypeToken<List<VersionManifest.Version>>() {}.type
                )
            }?.let { unlistVersions ->
                val versions = currentManifest.versions.toMutableList()
                versions.addAll(unlistVersions)
                versions.sortWith { version, other ->
                    other.releaseTime.compareTo(version.releaseTime)
                }

                currentManifest.copy(
                    versions = versions.toList()
                )
            }
        }
    }
}