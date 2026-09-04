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

package com.movtery.zalithlauncher.ui.screens.content.download.assets.elements

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.movtery.zalithlauncher.R
import com.movtery.zalithlauncher.game.download.assets.platform.PlatformClasses
import com.movtery.zalithlauncher.ui.components.MarqueeText
import com.movtery.zalithlauncher.ui.components.OwnOutlinedTextField
import com.movtery.zalithlauncher.ui.screens.content.elements.isFilenameInvalid
import com.movtery.zalithlauncher.ui.theme.cardColor
import com.movtery.zalithlauncher.ui.theme.onCardColor
import java.io.File

/**
 * 目前支持重复文件检测的资源类型：模组、资源包、光影包
 * 存档等其他类型不参与本检测，保持原有安装行为不变
 */
fun PlatformClasses.supportsDuplicateFileCheck(): Boolean =
    this == PlatformClasses.MOD ||
        this == PlatformClasses.RESOURCE_PACK ||
        this == PlatformClasses.SHADERS

/**
 * 在给定的目标目录列表中查找是否已存在同名文件
 * @return 第一个与[fileName]冲突的已存在文件，不存在冲突则返回null
 */
fun findConflictingFile(fileName: String, targetFolders: List<File>): File? {
    if (fileName.isEmpty()) return null
    return targetFolders.firstNotNullOfOrNull { folder ->
        File(folder, fileName).takeIf { it.exists() }
    }
}

/**
 * 重复文件冲突对话框
 * 当即将安装的文件与当前所选实例内已存在的文件同名时展示
 * 复用现有的文件名校验逻辑（[isFilenameInvalid]）与Material组件
 *
 * @param originalFileName 即将安装的原始文件名，用于预填充输入框
 * @param targetFolders 当前所选实例（仅限当前选择的Minecraft实例）内的目标目录列表
 * @param onCancel 取消安装
 * @param onOverwrite 使用原始文件名覆盖已存在的文件
 * @param onConfirm 使用新的、已解决冲突的文件名继续安装
 */
@Composable
fun DuplicateFileConflictDialog(
    originalFileName: String,
    targetFolders: List<File>,
    onCancel: () -> Unit,
    onOverwrite: () -> Unit,
    onConfirm: (newFileName: String) -> Unit
) {
    var fileName by rememberSaveable { mutableStateOf(originalFileName) }

    //复用现有的文件名合法性校验工具
    val filenameInvalidMessage = key(fileName) {
        isFilenameInvalid(fileName)
    }
    val isEmpty = fileName.isEmpty()
    val hasConflict = remember(fileName, targetFolders) {
        !isEmpty && findConflictingFile(fileName, targetFolders) != null
    }
    val isUnchanged = fileName == originalFileName
    val isError = isEmpty || filenameInvalidMessage != null || hasConflict

    //仅当文件名已被修改，且新文件名不再存在任何校验/冲突问题时，才允许确认
    val confirmEnabled = !isUnchanged && !isError

    Dialog(onDismissRequest = onCancel) {
        BoxWithConstraints(
            modifier = Modifier.fillMaxHeight(),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                modifier = Modifier
                    .padding(all = 6.dp)
                    .widthIn(min = 320.dp)
                    .heightIn(max = maxHeight - 12.dp)
                    .wrapContentHeight(),
                shape = MaterialTheme.shapes.extraLarge,
                color = cardColor(false),
                contentColor = onCardColor(),
                shadowElevation = 6.dp
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    Text(
                        text = stringResource(R.string.download_assets_duplicate_file_title),
                        style = MaterialTheme.typography.titleMedium
                    )

                    OwnOutlinedTextField(
                        modifier = Modifier.fillMaxWidth(),
                        value = fileName,
                        onValueChange = { fileName = it },
                        isError = isError,
                        singleLine = true,
                        maxLines = 1,
                        supportingText = {
                            when {
                                isEmpty -> Text(stringResource(R.string.generic_cannot_empty))
                                filenameInvalidMessage != null -> Text(filenameInvalidMessage)
                                hasConflict -> Text(stringResource(R.string.download_assets_duplicate_file_warning))
                            }
                        }
                    )

                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp),
                            shape = MaterialTheme.shapes.large,
                            enabled = confirmEnabled,
                            onClick = {
                                if (confirmEnabled) onConfirm(fileName)
                            }
                        ) {
                            MarqueeText(text = stringResource(R.string.generic_confirm))
                        }
                        OutlinedButton(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp),
                            shape = MaterialTheme.shapes.large,
                            onClick = onOverwrite
                        ) {
                            MarqueeText(text = stringResource(R.string.download_assets_duplicate_file_overwrite))
                        }
                        FilledTonalButton(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp),
                            shape = MaterialTheme.shapes.large,
                            onClick = onCancel
                        ) {
                            MarqueeText(text = stringResource(R.string.generic_cancel))
                        }
                    }
                }
            }
        }
    }
}
