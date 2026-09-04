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

package com.movtery.zalithlauncher.game.version.shader_pack

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.movtery.zalithlauncher.game.download.assets.platform.Platform
import com.movtery.zalithlauncher.game.download.assets.platform.curseforge.models.CurseForgeFile
import com.movtery.zalithlauncher.game.download.assets.platform.getProjectByVersion
import com.movtery.zalithlauncher.game.download.assets.platform.getVersionByLocalFile
import com.movtery.zalithlauncher.game.download.assets.platform.modrinth.models.ModrinthVersion
import com.movtery.zalithlauncher.ui.screens.content.versions.elements.ShaderPackInfo
import com.movtery.zalithlauncher.utils.file.calculateFileSha1
import com.movtery.zalithlauncher.utils.logging.Logger
import com.tencent.mmkv.MMKV
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

private const val TAG = "RemoteShaderPack"

class RemoteShaderPack(
    val info: ShaderPackInfo
) {
    var isLoading by mutableStateOf(false)
        private set

    var projectInfo: ShaderProjectInfo? by mutableStateOf(null)
        private set

    var isLoaded: Boolean = false
        private set

    suspend fun load(loadFromCache: Boolean) {
        if (loadFromCache && isLoaded) return

        if (!loadFromCache) {
            projectInfo = null
        }

        isLoaded = false
        isLoading = true

        try {
            withContext(Dispatchers.IO) {
                val file = info.file
                val projectCache = shaderProjectCache()

                runCatching {
                    val sha1 = calculateFileSha1(file)

                    val cachedProject = if (loadFromCache) {
                        projectCache.decodeParcelable(sha1, ShaderProjectInfo::class.java)
                    } else null

                    if (loadFromCache && cachedProject != null) {
                        projectInfo = cachedProject
                    } else {
                        ensureActive()
                        val version = getVersionByLocalFile(file, sha1)
                        val projectRef = version?.let { toProjectRef(it) }

                        if (projectRef != null) {
                            val (projectId, platform) = projectRef
                            val project = getProjectByVersion(
                                projectId = projectId,
                                platform = platform,
                                printLog = false
                            )
                            val newProjectInfo = ShaderProjectInfo(
                                id = project.platformId(),
                                platform = project.platform(),
                                iconUrl = project.platformIconUrl(),
                                title = project.platformTitle(),
                                slug = project.platformSlug()
                            )

                            projectInfo = newProjectInfo
                            projectCache.encode(sha1, newProjectInfo, MMKV.ExpireInDay)
                        }
                    }

                    isLoaded = true
                }.onFailure { e ->
                    if (e is CancellationException) return@onFailure
                    Logger.warning(TAG, "Failed to load project info for shader pack: ${file.name}", e)
                }
            }
        } finally {
            isLoading = false
        }
    }

    private fun toProjectRef(version: com.movtery.zalithlauncher.game.download.assets.platform.PlatformVersion): Pair<String, Platform>? {
        return when (version) {
            is ModrinthVersion -> version.projectId to Platform.MODRINTH
            is CurseForgeFile -> version.modId.toString() to Platform.CURSEFORGE
            else -> null
        }
    }
}
