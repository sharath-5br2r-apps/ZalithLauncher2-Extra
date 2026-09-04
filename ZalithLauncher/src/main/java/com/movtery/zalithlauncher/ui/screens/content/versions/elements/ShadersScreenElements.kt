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

package com.movtery.zalithlauncher.ui.screens.content.versions.elements

import com.movtery.zalithlauncher.game.version.shader_pack.RemoteShaderPack
import java.io.File

sealed interface ShaderOperation {
    data object None : ShaderOperation
    data object Progress : ShaderOperation
    data class Delete(val info: ShaderPackInfo) : ShaderOperation
}

data class ShaderPackInfo(
    val file: File,
    val fileSize: Long,
    val isEnabled: Boolean = !file.name.endsWith(".disabled", ignoreCase = true),
    val displayName: String = if (file.name.endsWith(".disabled", ignoreCase = true))
        file.name.dropLast(9) else file.name
)

fun List<ShaderPackInfo>.filterShaders(
    nameFilter: String,
    stateFilter: PackStateFilter = PackStateFilter.All
) = this.filter {
    val matchesName = nameFilter.isEmpty() || it.displayName.contains(nameFilter, true)
    val matchesState = when (stateFilter) {
        PackStateFilter.All -> true
        PackStateFilter.Enabled -> it.isEnabled
        PackStateFilter.Disabled -> !it.isEnabled
    }
    matchesName && matchesState
}

fun List<RemoteShaderPack>.filterRemoteShaders(
    nameFilter: String,
    stateFilter: PackStateFilter = PackStateFilter.All
) = this.filter {
    val info = it.info
    val matchesName = nameFilter.isEmpty() || info.displayName.contains(nameFilter, true)
    val matchesState = when (stateFilter) {
        PackStateFilter.All -> true
        PackStateFilter.Enabled -> info.isEnabled
        PackStateFilter.Disabled -> !info.isEnabled
    }
    matchesName && matchesState
}
