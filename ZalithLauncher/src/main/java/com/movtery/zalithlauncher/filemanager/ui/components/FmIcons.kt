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

package com.movtery.zalithlauncher.filemanager.ui.components

import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.movtery.zalithlauncher.R
import com.movtery.zalithlauncher.filemanager.logic.entry.ArchiveType

object FmIcons {
    @Composable
    private fun forFile(name: String): Painter {
        val drawable = remember(name) {
            val arch = ArchiveType.of(name)
            if (arch != null) return@remember R.drawable.ic_folder_zip_filled

            val ext = name.substringAfterLast('.', "").lowercase()
            when (ext) {
                "txt", "log", "md", "json", "yaml", "yml", "toml", "ini", "cfg" -> R.drawable.ic_text_snippet_filled
                "png", "jpg", "jpeg", "gif", "bmp", "webp", "svg" -> R.drawable.ic_image_filled
                "mp3", "wav", "ogg", "flac", "m4a", "aac" -> R.drawable.ic_audio_file_filled
                "mp4", "mkv", "avi", "mov", "webm" -> R.drawable.ic_movie_filled
//            "java", "kt", "kts", "c", "cpp", "h", "js", "ts", "py", "sh", "xml", "html", "css" -> R.drawable.ic_text_snippet_filled
                else -> R.drawable.ic_draft_filled
            }
        }
        return painterResource(drawable)
    }

    /** 根据条目类型与扩展名显示对应图标 */
    @Composable
    fun IconFor(
        name: String,
        isDirectory: Boolean,
        modifier: Modifier = Modifier,
        size: Dp = 28.dp,
    ) {
        val painter = if (isDirectory) {
            painterResource(R.drawable.ic_folder_filled)
        } else {
            forFile(name)
        }
        Icon(
            modifier = modifier.size(size),
            painter = painter,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.secondary
        )
    }
}