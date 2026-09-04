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
 * along with this program. If not, see <https://www.gnu.org/licenses/gpl-3.0.txt>.
 */

package com.movtery.zalithlauncher.ui.screens.content.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.movtery.zalithlauncher.R
import com.movtery.zalithlauncher.setting.AllSettings
import com.movtery.zalithlauncher.ui.base.BaseScreen
import com.movtery.zalithlauncher.ui.components.AnimatedColumn
import com.movtery.zalithlauncher.ui.components.SimpleAlertDialog
import com.movtery.zalithlauncher.ui.screens.NestedNavKey
import com.movtery.zalithlauncher.ui.screens.NormalNavKey
import com.movtery.zalithlauncher.ui.screens.TitledNavKey
import com.movtery.zalithlauncher.ui.screens.content.elements.QuickAccessShortcut
import com.movtery.zalithlauncher.ui.screens.content.elements.QuickAccessShortcutGrid
import com.movtery.zalithlauncher.ui.screens.content.settings.layouts.CardPosition
import com.movtery.zalithlauncher.ui.screens.content.settings.layouts.SettingsCard
import com.movtery.zalithlauncher.ui.screens.content.settings.layouts.SettingsCardColumn

private const val MIN_SHORTCUTS = 3
private const val MAX_SHORTCUTS = 8

@Composable
fun QuickAccessCustomizationScreen(
    key: NestedNavKey.Settings,
    settingsScreenKey: TitledNavKey?,
    mainScreenKey: TitledNavKey?
) {
    BaseScreen(
        Triple(key, mainScreenKey, false),
        Triple(NormalNavKey.Settings.QuickAccessCustomization, settingsScreenKey, false)
    ) { isVisible ->

        // Working copy, initialized from persisted setting
        var activeShortcuts by remember {
            val saved = AllSettings.quickAccessShortcuts.getValue()
                .mapNotNull { QuickAccessShortcut.fromId(it) }
                .distinct()
                .take(MAX_SHORTCUTS)
            mutableStateOf(
                if (saved.size >= MIN_SHORTCUTS) saved
                else QuickAccessShortcut.DEFAULT_IDS.mapNotNull { QuickAccessShortcut.fromId(it) }
            )
        }

        // Persist immediately on every change
        LaunchedEffect(activeShortcuts) {
            AllSettings.quickAccessShortcuts.save(activeShortcuts.map { it.id })
        }

        var showRestoreDialog by remember { mutableStateOf(false) }

        if (showRestoreDialog) {
            SimpleAlertDialog(
                title = stringResource(R.string.generic_reset),
                text = stringResource(R.string.settings_launcher_quick_access_restore_confirm),
                onConfirm = {
                    activeShortcuts = QuickAccessShortcut.DEFAULT_IDS
                        .mapNotNull { QuickAccessShortcut.fromId(it) }
                    showRestoreDialog = false
                },
                onDismiss = { showRestoreDialog = false }
            )
        }

        AnimatedColumn(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(all = 12.dp),
            isVisible = isVisible
        ) { scope ->

            // ── Live Preview ─────────────────────────────────────────────────
            // Reuses the shared QuickAccessShortcutGrid composable so the preview
            // is always visually identical to the real Quick Access panel shown on
            // the launcher's main screen. Updates immediately on every change.
            AnimatedItem(scope) { yOffset ->
                val previewRows = activeShortcuts.chunked(4)
                val previewHeight = if (previewRows.size > 1) 235.dp else 160.dp
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .offset { IntOffset(x = 0, y = yOffset.roundToPx()) },
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = stringResource(R.string.quick_access_preview_title),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        modifier = Modifier.padding(horizontal = 4.dp)
                    )
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(previewHeight),
                        shape = MaterialTheme.shapes.extraLarge,
                        color = MaterialTheme.colorScheme.surface,
                        tonalElevation = 4.dp,
                        shadowElevation = 14.dp
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 20.dp, vertical = 14.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                text = "QUICK ACCESS",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    letterSpacing = 1.5.sp
                                ),
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                                modifier = Modifier.padding(horizontal = 4.dp)
                            )
                            QuickAccessShortcutGrid(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f),
                                shortcuts = activeShortcuts
                            )
                        }
                    }
                }
            }

            // ── Restore Defaults ────────────────────────────────────────────
            AnimatedItem(scope) { yOffset ->
                SettingsCardColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .offset { IntOffset(x = 0, y = yOffset.roundToPx()) }
                ) {
                    SettingsCard(
                        position = CardPosition.Single,
                        title = stringResource(R.string.generic_reset),
                        summary = stringResource(R.string.settings_launcher_quick_access_restore_summary),
                        onClick = { showRestoreDialog = true }
                    )
                }
            }

            // ── Active Shortcuts ─────────────────────────────────────────────
            AnimatedItem(scope) { yOffset ->
                SettingsCardColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .offset { IntOffset(x = 0, y = yOffset.roundToPx()) }
                ) {
                    // Section header
                    SettingsCard(
                        modifier = Modifier.fillMaxWidth(),
                        position = CardPosition.Top
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.quick_access_section_active),
                                style = MaterialTheme.typography.titleSmall
                            )
                            Text(
                                text = stringResource(
                                    R.string.quick_access_active_count,
                                    activeShortcuts.size,
                                    MIN_SHORTCUTS,
                                    MAX_SHORTCUTS
                                ),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                        }
                    }

                    // One row per active shortcut
                    activeShortcuts.forEachIndexed { index, shortcut ->
                        val isFirst = index == 0
                        val isLast = index == activeShortcuts.lastIndex
                        val canRemove = activeShortcuts.size > MIN_SHORTCUTS
                        val position = if (isLast) CardPosition.Bottom else CardPosition.Middle

                        SettingsCard(
                            modifier = Modifier.fillMaxWidth(),
                            position = position
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 8.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                // Move up / move down
                                Column {
                                    IconButton(
                                        modifier = Modifier.size(28.dp),
                                        onClick = {
                                            if (!isFirst) {
                                                val list = activeShortcuts.toMutableList()
                                                list.removeAt(index)
                                                list.add(index - 1, shortcut)
                                                activeShortcuts = list
                                            }
                                        },
                                        enabled = !isFirst
                                    ) {
                                        Icon(
                                            painter = painterResource(R.drawable.ic_arrow_drop_up_rounded),
                                            contentDescription = null,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                    IconButton(
                                        modifier = Modifier.size(28.dp),
                                        onClick = {
                                            if (!isLast) {
                                                val list = activeShortcuts.toMutableList()
                                                list.removeAt(index)
                                                list.add(index + 1, shortcut)
                                                activeShortcuts = list
                                            }
                                        },
                                        enabled = !isLast
                                    ) {
                                        Icon(
                                            painter = painterResource(R.drawable.ic_arrow_drop_down_rounded),
                                            contentDescription = null,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }

                                // Shortcut icon
                                Icon(
                                    painter = painterResource(shortcut.iconRes),
                                    contentDescription = null,
                                    modifier = Modifier.size(22.dp),
                                    tint = MaterialTheme.colorScheme.onSurface
                                )

                                // Shortcut label
                                Text(
                                    text = stringResource(shortcut.labelRes),
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.weight(1f)
                                )

                                // Remove button
                                IconButton(
                                    modifier = Modifier.size(28.dp),
                                    onClick = {
                                        activeShortcuts = activeShortcuts.toMutableList()
                                            .also { it.removeAt(index) }
                                    },
                                    enabled = canRemove
                                ) {
                                    Icon(
                                        painter = painterResource(R.drawable.ic_close),
                                        contentDescription = stringResource(R.string.generic_delete),
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // ── Available Shortcuts ──────────────────────────────────────────
            val available = QuickAccessShortcut.entries.filter { it !in activeShortcuts }
            if (available.isNotEmpty()) {
                AnimatedItem(scope) { yOffset ->
                    SettingsCardColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .offset { IntOffset(x = 0, y = yOffset.roundToPx()) }
                    ) {
                        // Section header
                        SettingsCard(
                            modifier = Modifier.fillMaxWidth(),
                            position = CardPosition.Top
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 12.dp)
                            ) {
                                Text(
                                    text = stringResource(R.string.quick_access_section_available),
                                    style = MaterialTheme.typography.titleSmall
                                )
                            }
                        }

                        // One row per available shortcut
                        available.forEachIndexed { index, shortcut ->
                            val isLast = index == available.lastIndex
                            val canAdd = activeShortcuts.size < MAX_SHORTCUTS
                            val position = if (isLast) CardPosition.Bottom else CardPosition.Middle

                            SettingsCard(
                                modifier = Modifier.fillMaxWidth(),
                                position = position
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 8.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    // Align with active list (up/down column width = 56dp)
                                    Spacer(modifier = Modifier.width(56.dp))

                                    Icon(
                                        painter = painterResource(shortcut.iconRes),
                                        contentDescription = null,
                                        modifier = Modifier.size(22.dp),
                                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                    )

                                    Text(
                                        text = stringResource(shortcut.labelRes),
                                        style = MaterialTheme.typography.bodyMedium,
                                        modifier = Modifier.weight(1f),
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                    )

                                    // Add button
                                    IconButton(
                                        modifier = Modifier.size(28.dp),
                                        onClick = {
                                            if (canAdd) {
                                                activeShortcuts = activeShortcuts + shortcut
                                            }
                                        },
                                        enabled = canAdd
                                    ) {
                                        Icon(
                                            painter = painterResource(R.drawable.ic_add),
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
