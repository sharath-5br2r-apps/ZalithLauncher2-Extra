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

package com.movtery.zalithlauncher.ui.screens.content

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.movtery.zalithlauncher.R
import com.movtery.zalithlauncher.setting.enums.isLauncherInDarkTheme
import com.movtery.zalithlauncher.ui.base.BaseScreen
import com.movtery.zalithlauncher.ui.code_editor.EditorState
import com.movtery.zalithlauncher.ui.code_editor.SoraEditor
import com.movtery.zalithlauncher.ui.code_editor.lang.MarkdownLanguage
import com.movtery.zalithlauncher.ui.code_editor.scheme.SchemeIDEADark
import com.movtery.zalithlauncher.ui.code_editor.scheme.SchemeIDEALight
import com.movtery.zalithlauncher.ui.screens.NormalNavKey
import com.movtery.zalithlauncher.utils.logging.Logger
import com.movtery.zalithlauncher.ui.androidText
import com.movtery.zalithlauncher.viewmodel.ErrorViewModel
import com.movtery.zalithlauncher.viewmodel.ScreenBackStackViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private const val TAG = "FileEditorScreen"

/** 编辑器单个文件大小上限：2 MB，超出后不允许在内置编辑器中打开 */
private const val MAX_EDITABLE_SIZE = 2L * 1024 * 1024

@Composable
fun FileEditorScreen(
    key: NormalNavKey.FileEditor,
    backStackViewModel: ScreenBackStackViewModel,
    submitError: (ErrorViewModel.ThrowableMessage) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val isDark = isLauncherInDarkTheme()

    var editorState by remember { mutableStateOf<EditorState>(EditorState.Loading) }

    LaunchedEffect(key) {
        editorState = EditorState.Loading
        val file = File(key.filePath)
        val result = withContext(Dispatchers.IO) {
            runCatching {
                if (!file.exists()) throw IllegalStateException("File does not exist")
                if (file.length() > MAX_EDITABLE_SIZE) {
                    return@runCatching null
                }
                file.readText()
            }
        }
        result.onFailure { e ->
            Logger.warning(TAG, "Failed to open file for editing: ${key.filePath}", e)
            submitError(
                ErrorViewModel.ThrowableMessage(
                    title = androidText(R.string.generic_warning),
                    message = androidText(R.string.file_editor_load_failed, e.message ?: e.javaClass.simpleName)
                )
            )
            return@LaunchedEffect
        }
        val content = result.getOrNull()
        if (content == null) {
            submitError(
                ErrorViewModel.ThrowableMessage(
                    title = androidText(R.string.generic_warning),
                    message = androidText(R.string.file_editor_too_large)
                )
            )
            return@LaunchedEffect
        }
        editorState = EditorState.Success(io.github.rosemoe.sora.text.Content(content))
    }

    BaseScreen(
        screenKey = key,
        currentKey = backStackViewModel.mainScreen.currentKey,
        useClassEquality = true
    ) { isVisible ->
        AnimatedVisibility(
            visible = isVisible,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            val scheme = remember(isDark) {
                if (isDark) SchemeIDEADark() else SchemeIDEALight()
            }
            val language = remember(key.filePath) {
                if (key.filePath.endsWith(".md", ignoreCase = true)) MarkdownLanguage(homePageExtra = false) else null
            }

            SoraEditor(
                state = editorState,
                scheme = scheme,
                language = language,
                isReadOnly = false,
                onSaveClick = {
                    val state = editorState
                    if (state is EditorState.Success) {
                        scope.launch(Dispatchers.IO) {
                            runCatching {
                                File(key.filePath).writeText(state.content.toString())
                            }.onSuccess {
                                withContext(Dispatchers.Main) {
                                    Toast.makeText(
                                        context,
                                        context.getString(R.string.file_editor_saved),
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            }.onFailure { e ->
                                Logger.warning(TAG, "Failed to save file: ${key.filePath}", e)
                                withContext(Dispatchers.Main) {
                                    submitError(
                                        ErrorViewModel.ThrowableMessage(
                                            title = androidText(R.string.generic_warning),
                                            message = androidText(R.string.file_editor_save_failed, e.message ?: e.javaClass.simpleName)
                                        )
                                    )
                                }
                            }
                        }
                    }
                }
            )
        }
    }
}
