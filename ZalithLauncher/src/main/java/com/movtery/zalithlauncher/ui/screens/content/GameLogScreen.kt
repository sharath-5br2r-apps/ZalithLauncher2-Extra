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

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.movtery.zalithlauncher.R
import com.movtery.zalithlauncher.game.version.installed.VersionsManager
import com.movtery.zalithlauncher.path.PathManager
import com.movtery.zalithlauncher.setting.enums.isLauncherInDarkTheme
import com.movtery.zalithlauncher.ui.base.BaseScreen
import com.movtery.zalithlauncher.ui.code_editor.EditorState
import com.movtery.zalithlauncher.ui.code_editor.SoraEditor
import com.movtery.zalithlauncher.ui.code_editor.TextMateRegistry
import com.movtery.zalithlauncher.ui.code_editor.scheme.SchemeIDEADark
import com.movtery.zalithlauncher.ui.code_editor.scheme.SchemeIDEALight
import com.movtery.zalithlauncher.ui.components.BackgroundCard
import com.movtery.zalithlauncher.ui.screens.NormalNavKey
import com.movtery.zalithlauncher.game.launch.LogName
import com.movtery.zalithlauncher.utils.logging.Logger
import com.movtery.zalithlauncher.viewmodel.ScreenBackStackViewModel
import io.github.rosemoe.sora.lang.Language
import io.github.rosemoe.sora.text.Content
import io.github.rosemoe.sora.widget.schemes.EditorColorScheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.RandomAccessFile

private const val MAX_LOG_VIEW_SIZE: Long = 8L * 1024 * 1024

private fun readLogFile(file: File): String {
    val size = file.length()
    if (size <= MAX_LOG_VIEW_SIZE) return file.readText()
    RandomAccessFile(file, "r").use { raf ->
        raf.seek(size - MAX_LOG_VIEW_SIZE)
        val bytes = ByteArray(MAX_LOG_VIEW_SIZE.toInt())
        raf.readFully(bytes)
        val text = String(bytes, Charsets.UTF_8)
            .trimStart('\uFFFD')

        val firstNewline = text.indexOf('\n')
        return if (firstNewline >= 0 && firstNewline < text.length - 1) {
            text.substring(firstNewline + 1)
        } else {
            text
        }
    }
}

private fun resolveLatestGameLog(): File? {
    val currentVer = VersionsManager.currentVersion.value
    val verLog = currentVer?.getLatestLog()
    if (verLog != null && verLog.exists()) {
        return verLog
    }
    val fallbackLog = File(PathManager.DIR_LAUNCHER_LOGS, LogName.GAME.fileName)
    if (fallbackLog.exists()) {
        return fallbackLog
    }
    return null
}

@Composable
fun GameLogScreen(
    backStackViewModel: ScreenBackStackViewModel
) {
    val isDark = isLauncherInDarkTheme()
    val context = LocalContext.current

    var editorState by remember { mutableStateOf<EditorState>(EditorState.Loading) }
    var hasLogFile by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        val logFile = resolveLatestGameLog()
        if (logFile == null) {
            hasLogFile = false
            return@LaunchedEffect
        }
        hasLogFile = true
        editorState = EditorState.Loading
        val content = withContext(Dispatchers.IO) {
            runCatching {
                readLogFile(logFile)
            }.getOrElse { e ->
                Logger.warning("ViewGameLog", "Unable to read game log file!", e)
                e.message
            }
        }
        editorState = EditorState.Success(Content(content))
    }

    BaseScreen(
        screenKey = NormalNavKey.GameLog,
        currentKey = backStackViewModel.mainScreen.currentKey
    ) { isVisible ->
        AnimatedVisibility(
            visible = isVisible,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            if (!hasLogFile) {
                BackgroundCard(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(12.dp),
                    shape = MaterialTheme.shapes.extraLarge
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = stringResource(R.string.stats_no_log),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                val fallbackScheme = remember(isDark) {
                    if (isDark) SchemeIDEADark() else SchemeIDEALight()
                }
                var language by remember { mutableStateOf<Language?>(null) }
                var scheme by remember { mutableStateOf<EditorColorScheme?>(null) }
                LaunchedEffect(isDark) {
                    language = TextMateRegistry.languageFor("text.log", context)
                    scheme = TextMateRegistry.colorScheme(isDark, context)
                }

                SoraEditor(
                    state = editorState,
                    scheme = scheme ?: fallbackScheme,
                    language = language,
                    isReadOnly = true,
                    onSaveClick = {}
                )
            }
        }
    }
}
