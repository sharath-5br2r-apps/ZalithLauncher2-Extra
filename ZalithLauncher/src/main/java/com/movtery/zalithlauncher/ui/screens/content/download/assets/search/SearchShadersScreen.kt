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

package com.movtery.zalithlauncher.ui.screens.content.download.assets.search

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.movtery.zalithlauncher.game.download.assets.platform.Platform
import com.movtery.zalithlauncher.game.download.assets.platform.PlatformClasses
import com.movtery.zalithlauncher.game.download.assets.platform.PlatformSearchFilter
import com.movtery.zalithlauncher.game.download.assets.platform.curseforge.models.CurseForgeShadersCategory
import com.movtery.zalithlauncher.game.download.assets.platform.modrinth.models.ModrinthFeatures
import com.movtery.zalithlauncher.game.download.assets.platform.modrinth.models.ModrinthShadersCategory
import com.movtery.zalithlauncher.setting.AllSettings
import com.movtery.zalithlauncher.ui.screens.NormalNavKey
import com.movtery.zalithlauncher.ui.screens.TitledNavKey

@Composable
fun SearchShadersScreen(
    mainScreenKey: TitledNavKey?,
    downloadScreenKey: TitledNavKey?,
    downloadShadersScreenKey: TitledNavKey,
    downloadShadersScreenCurrentKey: TitledNavKey?,
    swapToDownload: (Platform, projectId: String, iconUrl: String?) -> Unit = { _, _, _ -> }
) {
    val initialPlatform = remember {
        AllSettings.searchShadersPlatform.getValue()
    }
    // 从持久化存储中恢复上次的过滤器配置（游戏版本为会话状态，不从持久化恢复）
    val initialFilter = remember {
        val platform = AllSettings.searchShadersPlatform.getValue()
        val sortField = AllSettings.searchShadersSortField.getValue()
        val categoryStrings = AllSettings.searchShadersCategories.getValue()
        val categories = categoryStrings.mapNotNull { str ->
            when (platform) {
                Platform.MODRINTH -> ModrinthShadersCategory.entries.find { it.facetValue() == str }
                    ?: ModrinthFeatures.entries.find { it.facetValue() == str }
                Platform.CURSEFORGE -> CurseForgeShadersCategory.entries.find { it.describe() == str }
            }
        }
        PlatformSearchFilter(
            sortField = sortField,
            gameVersion = null, // Game Version is session-only; not loaded from preferences
            categories = categories
        )
    }

    SearchAssetsScreen(
        mainScreenKey = mainScreenKey,
        parentScreenKey = downloadShadersScreenKey,
        parentCurrentKey = downloadScreenKey,
        screenKey = NormalNavKey.SearchShaders,
        currentKey = downloadShadersScreenCurrentKey,
        platformClasses = PlatformClasses.SHADERS,
        initialPlatform = initialPlatform,
        onPlatformChange = {
            AllSettings.searchShadersPlatform.save(it)
        },
        initialFilter = initialFilter,
        autoSelectEnabled = AllSettings.autoSelectDownloadContent.getValue() && AllSettings.autoSelectShaderPacks.getValue(),
        onFilterChange = { platform, filter ->
            AllSettings.searchShadersSortField.save(filter.sortField)
            // Game Version is session-only; not saved to preferences
            AllSettings.searchShadersCategories.save(
                when (platform) {
                    Platform.MODRINTH -> filter.categories.mapNotNull { cat ->
                        (cat as? ModrinthShadersCategory)?.facetValue()
                            ?: (cat as? ModrinthFeatures)?.facetValue()
                    }
                    Platform.CURSEFORGE -> filter.categories.mapNotNull { cat ->
                        (cat as? CurseForgeShadersCategory)?.describe()
                    }
                }
            )
        },
        getCategories = { platform ->
            when (platform) {
                Platform.CURSEFORGE -> CurseForgeShadersCategory.entries
                Platform.MODRINTH -> ModrinthShadersCategory.entries
            }
        },
        mapCategories = { platform, string ->
            when (platform) {
                Platform.MODRINTH -> {
                    ModrinthShadersCategory.entries.find { it.facetValue() == string }
                        ?: ModrinthFeatures.entries.find { it.facetValue() == string }
                }
                Platform.CURSEFORGE -> {
                    CurseForgeShadersCategory.entries.find { it.describe() == string }
                }
            }
        },
        filterPersistenceKey = AllSettings.searchShadersFilter.key,
        swapToDownload = swapToDownload
    )
}
