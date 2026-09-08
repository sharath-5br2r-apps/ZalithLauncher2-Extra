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

package com.movtery.zalithlauncher.ui.screens.content.download

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.movtery.zalithlauncher.game.download.assets.platform.FINGERPRINT_BATCH_SIZE
import com.movtery.zalithlauncher.game.download.assets.platform.Platform
import com.movtery.zalithlauncher.game.download.assets.platform.PlatformVersion
import com.movtery.zalithlauncher.game.download.assets.platform.getCFFilesByFingerprints
import com.movtery.zalithlauncher.game.download.assets.platform.getModrinthVersBySha1
import com.movtery.zalithlauncher.game.version.installed.Version
import com.movtery.zalithlauncher.game.version.installed.VersionFolders
import com.movtery.zalithlauncher.game.version.mod.InstalledMod
import com.movtery.zalithlauncher.game.version.mod.ModFingerprints
import com.movtery.zalithlauncher.game.version.mod.READER_PARALLELISM
import com.movtery.zalithlauncher.game.version.mod.computeModFingerprints
import com.movtery.zalithlauncher.game.version.mod.installedModCache
import com.movtery.zalithlauncher.game.version.mod.isDisabled
import com.movtery.zalithlauncher.game.version.mod.toInstalledMod
import com.movtery.zalithlauncher.setting.AllSettings
import com.movtery.zalithlauncher.utils.logging.Logger
import com.tencent.mmkv.MMKV
import kotlinx.coroutines.Job
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.File

private const val TAG = "DownloadModViewModel"

/** 下载模组屏幕的本地模组安装信息状态 */
class DownloadModViewModel : ViewModel() {
    /** 当前匹配的目标平台 */
    var currentPlatform: Platform = AllSettings.searchModPlatform.getValue()
        private set

    /** 是否正在进行本地扫描或平台匹配 */
    var matching by mutableStateOf(false)
        private set

    /** 当前平台下本地已安装的模组项目映射，键为平台项目ID */
    var installedByProject by mutableStateOf<Map<String, InstalledMod>>(emptyMap())
        private set

    /** 当前平台下本地已安装的模组版本映射，键为平台版本ID */
    private var installedByVersion by mutableStateOf<Map<String, InstalledMod>>(emptyMap())

    private var scanJob: Job? = null

    /** 最近一次扫描对应的版本名称，用于判断扫描目标是否变化 */
    private var scannedVersionName: String? = null

    /** 最近一次扫描得到的所有本地模组文件指纹 */
    private var scannedFingerprints: List<ModFingerprints> = emptyList()

    /** 本次生命周期内已完成的平台匹配结果，避免切换平台时重复计算 */
    private val matchedResults =
        mutableMapOf<Platform, Pair<Map<String, InstalledMod>, Map<String, InstalledMod>>>()

    /**
     * 查询平台项目在本地是否已安装
     */
    fun checkProject(platform: Platform, projectId: String): InstalledMod? {
        return installedByProject[projectId]
            ?.takeIf { it.platform == platform && !it.notFound }
    }

    /**
     * 查询平台版本在本地是否已安装
     */
    fun checkVersion(version: PlatformVersion): InstalledMod? {
        return installedByVersion[version.platformId()]
            ?.takeIf { it.platform == version.platform() && !it.notFound }
    }

    /**
     * 扫描指定游戏版本的模组目录，并按当前平台匹配本地已安装的模组
     *
     * 扫描目标版本变化时立即清除旧的匹配结果；
     * 同版本重复扫描时保留旧结果直至新结果就绪，避免标注闪烁
     */
    fun scan(version: Version?) {
        scanJob?.cancel()

        val versionName = version?.getVersionName()
        if (versionName != scannedVersionName) {
            installedByProject = emptyMap()
            installedByVersion = emptyMap()
        }
        //重扫后指纹集合可能变化，已完成的平台匹配结果全部失效
        matchedResults.clear()

        scanJob = viewModelScope.launch {
            matching = true
            scannedVersionName = versionName
            scannedFingerprints = version?.let { ver ->
                runCatching {
                    scanFingerprints(VersionFolders.MOD.getDir(ver.getGameDir()))
                }.onFailure { e ->
                    Logger.warning(TAG, "Failed to scan local mod fingerprints", e)
                }.getOrDefault(emptyList())
            } ?: emptyList()

            applyMatches(currentPlatform)
            matching = false
        }
    }

    /**
     * 搜索平台变更时，使用已扫描的指纹按新平台重新匹配
     */
    fun onPlatformChanged(platform: Platform) {
        if (currentPlatform == platform) return
        currentPlatform = platform

        // 扫描尚未结束时，扫描尾部会自动按最新平台进行匹配
        if (scanJob?.isActive == true) return

        scanJob = viewModelScope.launch {
            matching = true
            applyMatches(platform)
            matching = false
        }
    }

    private suspend fun applyMatches(platform: Platform) {
        matchedResults[platform]?.let { (byProject, byVersion) ->
            installedByProject = byProject
            installedByVersion = byVersion
            return
        }

        val fingerprints = scannedFingerprints
        if (fingerprints.isEmpty()) {
            installedByProject = emptyMap()
            installedByVersion = emptyMap()
            return
        }

        val cache = installedModCache()
        val byProject = mutableMapOf<String, InstalledMod>()
        val byVersion = mutableMapOf<String, InstalledMod>()
        var allSucceeded = true

        fun collect(installed: InstalledMod) {
            if (installed.notFound) return
            byProject[installed.projectId] = installed
            byVersion[installed.versionId] = installed
        }

        // 优先读取持久缓存，只对未命中的指纹发起批量查询
        val uncached = mutableListOf<ModFingerprints>()
        for (print in fingerprints) {
            val cached = cache.decodeParcelable(print.cacheKey(platform), InstalledMod::class.java)
            if (cached != null) collect(cached) else uncached.add(print)
        }

        // 分块批量查询；块内成功时才允许写入持久缓存（含未命中的负缓存），
        // 失败的块不写任何缓存，留待下次进入时重试
        for (chunk in uncached.chunked(FINGERPRINT_BATCH_SIZE)) {
            runCatching {
                when (platform) {
                    Platform.MODRINTH -> getModrinthVersBySha1(chunk.map { it.sha1 })
                    Platform.CURSEFORGE ->
                        getCFFilesByFingerprints(chunk.map { it.murmur2 }).mapKeys { it.key.toString() }
                }
            }.onSuccess { fetched ->
                for (print in chunk) {
                    val installed = fetched[print.fingerprintValue(platform)]?.toInstalledMod()
                        ?: notFoundMod(platform)
                    cache.encode(print.cacheKey(platform), installed, MMKV.ExpireInDay)
                    collect(installed)
                }
            }.onFailure { e ->
                allSucceeded = false
                Logger.warning(TAG, "Failed to match installed mods on platform: $platform", e)
            }
        }

        val result = byProject to byVersion
        // 存在失败块时不做会话内缓存，下次切换平台时重试（成功块已有持久缓存兜底）
        if (allSucceeded) matchedResults[platform] = result
        installedByProject = result.first
        installedByVersion = result.second
    }

    /**
     * 并发计算模组目录内所有文件的指纹
     */
    private suspend fun scanFingerprints(modsDir: File): List<ModFingerprints> =
        withContext(Dispatchers.IO) {
            val files = modsDir.listFiles()
                ?.filter { it.isFile && it.isModFileCandidate() }
                ?: return@withContext emptyList()

            val semaphore = Semaphore(READER_PARALLELISM)
            coroutineScope {
                files.map { file ->
                    async {
                        semaphore.withPermit {
                            runCatching { computeModFingerprints(file) }
                                .onFailure { e ->
                                    Logger.warning(TAG, "Failed to compute mod fingerprints: ${file.name}", e)
                                }
                                .getOrNull()
                        }
                    }
                }.awaitAll().filterNotNull()
            }
        }

    override fun onCleared() {
        scanJob?.cancel()
    }
}

/**
 * 可能为模组的文件扩展名（.disabled 后缀的文件已去除后缀再判断）
 */
private val MOD_FILE_EXTENSIONS = setOf("jar", "zip", "litemod")

/**
 * 文件是否可能为模组文件，非模组文件不参与指纹计算
 */
private fun File.isModFileCandidate(): Boolean {
    val extension = if (isDisabled()) {
        File(nameWithoutExtension).extension
    } else {
        extension
    }
    return extension.lowercase() in MOD_FILE_EXTENSIONS
}

/**
 * 指纹在持久缓存中的键，不同平台的指纹相互独立
 */
private fun ModFingerprints.cacheKey(platform: Platform): String =
    "${platform.name}/${fingerprintValue(platform)}"

/**
 * 指纹在对应平台上的匹配键
 */
private fun ModFingerprints.fingerprintValue(platform: Platform): String = when (platform) {
    Platform.MODRINTH -> sha1
    Platform.CURSEFORGE -> murmur2.toString()
}

/**
 * 平台未命中指纹时的负缓存标记
 */
private fun notFoundMod(platform: Platform): InstalledMod = InstalledMod(
    platform = platform,
    projectId = "",
    versionId = "",
    versionName = "",
    notFound = true
)
