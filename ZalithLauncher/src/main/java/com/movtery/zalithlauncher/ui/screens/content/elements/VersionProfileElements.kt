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

package com.movtery.zalithlauncher.ui.screens.content.elements

import android.graphics.drawable.ColorDrawable
import android.view.WindowManager
import androidx.core.view.WindowCompat
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.movtery.zalithlauncher.R
import com.movtery.zalithlauncher.game.version.installed.Version
import com.movtery.zalithlauncher.game.version.profile.VersionProfile
import com.movtery.zalithlauncher.game.version.profile.VersionProfileManager
import com.movtery.zalithlauncher.ui.components.BackgroundCard
import com.movtery.zalithlauncher.ui.components.CardTitleLayout
import com.movtery.zalithlauncher.ui.components.SimpleEditDialog
import com.movtery.zalithlauncher.ui.theme.cardColor
import com.movtery.zalithlauncher.ui.theme.itemColor

private sealed interface ProfileEditor {
    data object Create : ProfileEditor
    data class Rename(val profile: VersionProfile) : ProfileEditor
}

/**
 * Centered popup for managing profiles belonging to a specific game version/instance.
 * Uses a dark semi-transparent scrim (matching the RecordingPlayerOverlay overlay mode)
 * over the rest of the screen, with a centered card containing a Create Profile button
 * and a scrollable list of profile cards (each with Rename and Delete actions).
 */
@Composable
fun ManageProfilesPopup(
    version: Version,
    onDismiss: () -> Unit
) {
    var refreshKey by remember { mutableIntStateOf(0) }
    val profileChange by VersionProfileManager.profileChanges.collectAsStateWithLifecycle()
    val profileRevision = profileChange
        ?.takeIf { it.versionPath == version.getVersionPath().absolutePath }
        ?.revision ?: 0L
    val profiles = remember(version, refreshKey, profileRevision) {
        VersionProfileManager.listProfiles(version)
    }
    val activeName = remember(version, refreshKey, profileRevision) {
        VersionProfileManager.activeProfileName(version)
    }
    var editor by remember { mutableStateOf<ProfileEditor?>(null) }
    var deleteTarget by remember { mutableStateOf<VersionProfile?>(null) }

    fun refresh() { refreshKey++ }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            // Allow dialog to draw behind status bar / navigation bar so the
            // scrim covers the full physical display — identical to RecordingPlayerOverlay.
            decorFitsSystemWindows = false
        )
    ) {
        // Mirror exactly what RecordingPlayerOverlay does in overlay (card) mode:
        // transparent background, no system dim, and edge-to-edge window layout.
        val dialogView = LocalView.current
        SideEffect {
            val dialogWindow = (dialogView.parent as? DialogWindowProvider)?.window
            dialogWindow?.setBackgroundDrawable(
                ColorDrawable(android.graphics.Color.TRANSPARENT)
            )
            dialogWindow?.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            dialogWindow?.setDimAmount(0f)
            dialogWindow?.let { w ->
                WindowCompat.setDecorFitsSystemWindows(w, false)
                w.setLayout(
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.MATCH_PARENT
                )
            }
        }

        Box(modifier = Modifier.fillMaxSize()) {

            // ── Full-screen scrim (same colour as RecordingPlayerOverlay card mode) ──
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.68f))
                    .clickable(onClick = onDismiss)
            )

            // ── Fixed-size centred card ───────────────────────────────────────
            BackgroundCard(
                shape = MaterialTheme.shapes.extraLarge,
                modifier = Modifier
                    .fillMaxWidth(0.9f)
                    .widthIn(max = 500.dp)
                    .height(420.dp)
                    .align(Alignment.Center)
            ) {
                // ── Title bar ────────────────────────────────────────────────
                CardTitleLayout {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 4.dp, end = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            modifier = Modifier
                                .padding(start = 12.dp)
                                .size(20.dp),
                            painter = painterResource(R.drawable.ic_style_outlined),
                            contentDescription = null
                        )
                        Text(
                            text = stringResource(R.string.version_profile_manage),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 6.dp),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        IconButton(onClick = onDismiss) {
                            Icon(
                                painter = painterResource(R.drawable.ic_close),
                                contentDescription = stringResource(R.string.generic_close)
                            )
                        }
                    }
                }

                // ── Create Profile button ─────────────────────────────────────
                Button(
                    onClick = { editor = ProfileEditor.Create },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    Icon(
                        modifier = Modifier.size(18.dp),
                        painter = painterResource(R.drawable.ic_add_box_outlined),
                        contentDescription = null
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.version_profile_create))
                }

                // ── Scrollable profile card list ──────────────────────────────
                if (profiles.isNotEmpty()) {
                    HorizontalDivider(
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    )

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        profiles.forEach { profile ->
                            ProfileCardItem(
                                profile = profile,
                                isActive = profile.name == activeName,
                                onSelect = {
                                    VersionProfileManager.selectProfile(version, profile.name)
                                    refresh()
                                },
                                onRename = { editor = ProfileEditor.Rename(profile) },
                                onDelete = { deleteTarget = profile }
                            )
                        }
                    }
                }
            }
        }
    }

    // ── Profile name editor (Create / Rename) ─────────────────────────────────
    editor?.let { action ->
        var value by remember(action) {
            mutableStateOf(
                when (action) {
                    ProfileEditor.Create -> ""
                    is ProfileEditor.Rename -> action.profile.name
                }
            )
        }
        SimpleEditDialog(
            title = stringResource(
                if (action is ProfileEditor.Create) R.string.version_profile_create
                else R.string.version_profile_rename
            ),
            value = value,
            onValueChange = { value = it },
            singleLine = true,
            onDismissRequest = { editor = null },
            onConfirm = {
                when (action) {
                    ProfileEditor.Create -> VersionProfileManager.createProfile(version, value)
                    is ProfileEditor.Rename -> VersionProfileManager.renameProfile(
                        version, action.profile.name, value
                    )
                }
                editor = null
                refresh()
            }
        )
    }

    // ── Delete confirmation ───────────────────────────────────────────────────
    deleteTarget?.let { profile ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            icon = {
                Icon(
                    painter = painterResource(R.drawable.ic_delete_filled),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error
                )
            },
            title = { Text(stringResource(R.string.version_profile_delete)) },
            text = {
                Text(
                    text = stringResource(R.string.version_profile_delete_warning, profile.name),
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        VersionProfileManager.deleteProfile(version, profile.name)
                        deleteTarget = null
                        refresh()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    )
                ) { Text(stringResource(R.string.generic_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) {
                    Text(stringResource(R.string.generic_cancel))
                }
            },
            shape = MaterialTheme.shapes.extraLarge
        )
    }
}

@Composable
private fun ProfileCardItem(
    profile: VersionProfile,
    isActive: Boolean,
    onSelect: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit
) {
    // Use the same base color as the version-list item cards (dark gray in dark theme).
    // Active state is distinguished by a primary-colored border only — no bright fill.
    val base = itemColor()
    val borderColor by animateColorAsState(
        targetValue = if (isActive) MaterialTheme.colorScheme.primary
                      else Color.Transparent,
        animationSpec = tween(200),
        label = "profileCardBorder"
    )

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = base,
        border = BorderStroke(1.5.dp, borderColor),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
        onClick = onSelect
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(9.dp)
                    .then(
                        if (isActive)
                            Modifier.background(
                                color = MaterialTheme.colorScheme.primary,
                                shape = RoundedCornerShape(50)
                            )
                        else
                            Modifier.border(
                                width = 1.5.dp,
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f),
                                shape = RoundedCornerShape(50)
                            )
                    )
            )
            Text(
                text = profile.name,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Normal,
                color = if (isActive) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurface
            )
            AnimatedVisibility(
                visible = isActive,
                enter = fadeIn(animationSpec = tween(150)),
                exit = fadeOut(animationSpec = tween(150))
            ) {
                Icon(
                    modifier = Modifier.size(16.dp),
                    painter = painterResource(R.drawable.ic_check),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            }
            IconButton(
                modifier = Modifier.size(36.dp),
                onClick = onRename
            ) {
                Icon(
                    modifier = Modifier.size(18.dp),
                    painter = painterResource(R.drawable.ic_edit_filled),
                    contentDescription = stringResource(R.string.generic_rename),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(
                modifier = Modifier.size(36.dp),
                onClick = onDelete
            ) {
                Icon(
                    modifier = Modifier.size(18.dp),
                    painter = painterResource(R.drawable.ic_delete_filled),
                    contentDescription = stringResource(R.string.generic_delete),
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

/**
 * Shared profile dropdown used by instance cards and the dashboard.
 * The manager is intentionally synchronous: profile changes rename files
 * immediately, then this composable refreshes its small menu state.
 */
@Composable
fun VersionProfileMenu(
    version: Version,
    modifier: Modifier = Modifier,
    onExpandedChanged: (Boolean) -> Unit = {}
) {
    var expanded by remember(version) { mutableStateOf(false) }
    var managementOpen by remember(version) { mutableStateOf(false) }
    var refreshKey by remember(version) { mutableIntStateOf(0) }
    var editor by remember { mutableStateOf<ProfileEditor?>(null) }
    var deleteTarget by remember { mutableStateOf<VersionProfile?>(null) }
    val profileChange by VersionProfileManager.profileChanges.collectAsStateWithLifecycle()
    val profileRevision = profileChange
        ?.takeIf { it.versionPath == version.getVersionPath().absolutePath }
        ?.revision
        ?: 0L
    val profiles = remember(version, refreshKey, profileRevision) {
        VersionProfileManager.listProfiles(version)
    }
    val activeName = remember(version, refreshKey, profileRevision) {
        VersionProfileManager.activeProfileName(version)
    }

    fun refresh() {
        refreshKey++
    }

    BoxWithProfileMenu(
        version = version,
        modifier = modifier,
        expanded = expanded,
        onExpandedChanged = {
            expanded = it
            onExpandedChanged(it)
        },
        profiles = profiles,
        activeName = activeName,
        onSelect = { name ->
            VersionProfileManager.selectProfile(version, name)
            refresh()
            expanded = false
            onExpandedChanged(false)
        },
        onManage = {
            expanded = false
            onExpandedChanged(false)
            managementOpen = true
        },
        onCreate = {
            expanded = false
            managementOpen = false
            editor = ProfileEditor.Create
        },
    )

    ProfileManagementDialog(
        open = managementOpen,
        profiles = profiles,
        activeName = activeName,
        onCreate = {
            managementOpen = false
            editor = ProfileEditor.Create
        },
        onDuplicate = { profile ->
            VersionProfileManager.duplicateProfile(version, profile.name)
            refresh()
        },
        onRename = {
            managementOpen = false
            editor = ProfileEditor.Rename(it)
        },
        onDelete = {
            managementOpen = false
            deleteTarget = it
        },
        onDismiss = { managementOpen = false }
    )

    editor?.let { action ->
        var value by remember(action) {
            mutableStateOf(
                when (action) {
                    ProfileEditor.Create -> ""
                    is ProfileEditor.Rename -> action.profile.name
                }
            )
        }
        SimpleEditDialog(
            title = stringResource(
                if (action is ProfileEditor.Create) R.string.version_profile_create
                else R.string.version_profile_rename
            ),
            value = value,
            onValueChange = { value = it },
            singleLine = true,
            onDismissRequest = { editor = null },
            onConfirm = {
                when (action) {
                    ProfileEditor.Create -> VersionProfileManager.createProfile(version, value)
                    is ProfileEditor.Rename -> VersionProfileManager.renameProfile(
                        version,
                        action.profile.name,
                        value
                    )
                }
                editor = null
                refresh()
            }
        )
    }

    deleteTarget?.let { profile ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            icon = {
                Icon(
                    painter = painterResource(R.drawable.ic_delete_filled),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error
                )
            },
            title = { Text(stringResource(R.string.version_profile_delete)) },
            text = {
                Text(
                    text = stringResource(R.string.version_profile_delete_warning, profile.name),
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        VersionProfileManager.deleteProfile(version, profile.name)
                        deleteTarget = null
                        refresh()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    )
                ) { Text(stringResource(R.string.generic_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) {
                    Text(stringResource(R.string.generic_cancel))
                }
            },
            shape = MaterialTheme.shapes.extraLarge
        )
    }
}

@Composable
private fun BoxWithProfileMenu(
    version: Version,
    modifier: Modifier,
    expanded: Boolean,
    onExpandedChanged: (Boolean) -> Unit,
    profiles: List<VersionProfile>,
    activeName: String,
    onSelect: (String) -> Unit,
    onManage: () -> Unit,
    onCreate: () -> Unit
) {
    Box(modifier) {
        IconButton(
            onClick = { onExpandedChanged(!expanded) },
            enabled = version.isValid()
        ) {
            Icon(
                modifier = Modifier.size(20.dp),
                painter = painterResource(R.drawable.ic_style_outlined),
                contentDescription = stringResource(R.string.version_profile)
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { onExpandedChanged(false) },
            modifier = Modifier.widthIn(min = 200.dp, max = 300.dp),
            shape = MaterialTheme.shapes.large
        ) {
            profiles.forEach { profile ->
                val isActive = profile.name == activeName
                DropdownMenuItem(
                    text = {
                        Text(
                            text = profile.name,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Normal,
                            color = if (isActive) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurface
                        )
                    },
                    leadingIcon = {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .background(
                                    color = if (isActive) MaterialTheme.colorScheme.primary
                                            else MaterialTheme.colorScheme.outlineVariant,
                                    shape = RoundedCornerShape(50)
                                )
                        )
                    },
                    trailingIcon = if (isActive) ({
                        Icon(
                            modifier = Modifier.size(16.dp),
                            painter = painterResource(R.drawable.ic_check),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }) else null,
                    onClick = { onSelect(profile.name) }
                )
            }
            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.version_profile_create)) },
                leadingIcon = {
                    Icon(
                        modifier = Modifier.size(20.dp),
                        painter = painterResource(R.drawable.ic_add_box_outlined),
                        contentDescription = null
                    )
                },
                onClick = onCreate
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.version_profile_manage)) },
                leadingIcon = {
                    Icon(
                        modifier = Modifier.size(20.dp),
                        painter = painterResource(R.drawable.ic_settings_filled),
                        contentDescription = null
                    )
                },
                onClick = onManage
            )
        }
    }
}

@Composable
private fun ProfileManagementDialog(
    open: Boolean,
    profiles: List<VersionProfile>,
    activeName: String,
    onCreate: () -> Unit,
    onDuplicate: (VersionProfile) -> Unit,
    onRename: (VersionProfile) -> Unit,
    onDelete: (VersionProfile) -> Unit,
    onDismiss: () -> Unit
) {
    if (!open) return
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                painter = painterResource(R.drawable.ic_style_outlined),
                contentDescription = null
            )
        },
        title = { Text(stringResource(R.string.version_profile_manage)) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                profiles.forEachIndexed { index, profile ->
                    val isActive = profile.name == activeName
                    if (index > 0) {
                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 4.dp),
                            thickness = 0.5.dp,
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                        )
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp, horizontal = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            modifier = Modifier.size(18.dp),
                            painter = painterResource(R.drawable.ic_style_outlined),
                            contentDescription = null,
                            tint = if (isActive) MaterialTheme.colorScheme.primary
                                   else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        )
                        Text(
                            text = profile.name,
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Normal,
                            color = if (isActive) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurface
                        )
                        IconButton(
                            modifier = Modifier.size(36.dp),
                            onClick = { onDuplicate(profile) }
                        ) {
                            Icon(
                                modifier = Modifier.size(18.dp),
                                painter = painterResource(R.drawable.ic_file_copy_filled),
                                contentDescription = stringResource(R.string.generic_copy),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(
                            modifier = Modifier.size(36.dp),
                            onClick = { onRename(profile) }
                        ) {
                            Icon(
                                modifier = Modifier.size(18.dp),
                                painter = painterResource(R.drawable.ic_edit_filled),
                                contentDescription = stringResource(R.string.generic_rename),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(
                            modifier = Modifier.size(36.dp),
                            onClick = { onDelete(profile) }
                        ) {
                            Icon(
                                modifier = Modifier.size(18.dp),
                                painter = painterResource(R.drawable.ic_delete_filled),
                                contentDescription = stringResource(R.string.generic_delete),
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onCreate) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        modifier = Modifier.size(16.dp),
                        painter = painterResource(R.drawable.ic_add_box_outlined),
                        contentDescription = null
                    )
                    Text(stringResource(R.string.version_profile_create))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.generic_close))
            }
        },
        shape = MaterialTheme.shapes.extraLarge
    )
}

/**
 * Expanded replacement for the dashboard account block. It deliberately
 * shows the same profile list instead of the player head/name.
 */
@Composable
fun VersionProfilePanel(
    version: Version,
    modifier: Modifier = Modifier,
    onSelected: () -> Unit = {}
) {
    val profileChange by VersionProfileManager.profileChanges.collectAsStateWithLifecycle()
    val profileRevision = profileChange
        ?.takeIf { it.versionPath == version.getVersionPath().absolutePath }
        ?.revision
        ?: 0L
    val profiles = remember(version, profileRevision) {
        VersionProfileManager.listProfiles(version)
    }
    val active = remember(version, profileRevision) {
        VersionProfileManager.activeProfileName(version)
    }
    val primary = MaterialTheme.colorScheme.primary

    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.extraLarge,
        color = cardColor(false),
        shadowElevation = 4.dp,
        tonalElevation = 2.dp
    ) {
        Column(
            modifier = Modifier
                .padding(vertical = 10.dp, horizontal = 4.dp)
                .animateContentSize(animationSpec = tween(200)),
            verticalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            // ── Header ───────────────────────────────────────────────────────
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    modifier = Modifier.size(16.dp),
                    painter = painterResource(R.drawable.ic_style_outlined),
                    contentDescription = null,
                    tint = primary
                )
                Text(
                    text = stringResource(R.string.version_profile),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = primary,
                    modifier = Modifier.weight(1f)
                )
                // Profile count badge
                if (profiles.size > 1) {
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = itemColor(),
                        border = BorderStroke(1.dp, primary)
                    ) {
                        Text(
                            text = "${profiles.size}",
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = primary
                        )
                    }
                }
            }

            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 12.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
            )

            Spacer(Modifier.height(4.dp))

            // ── Profile list ─────────────────────────────────────────────────
            LazyVerticalGrid(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 276.dp),
                columns = GridCells.Fixed(2),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                contentPadding = PaddingValues(
                    horizontal = 8.dp,
                    vertical = 4.dp
                )
            ) {
                items(
                    items = profiles,
                    key = { it.name }
                ) { profile ->
                    val isActive = profile.name == active
                    val chipBorderColor by animateColorAsState(
                        targetValue = if (isActive) primary else Color.Transparent,
                        animationSpec = tween(200),
                        label = "profileChipBorder"
                    )
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.large,
                        color = itemColor(),
                        border = BorderStroke(1.5.dp, chipBorderColor),
                        onClick = {
                            VersionProfileManager.selectProfile(version, profile.name)
                            onSelected()
                        }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .then(
                                        if (isActive)
                                            Modifier.background(
                                                color = primary,
                                                shape = RoundedCornerShape(50)
                                            )
                                        else
                                            Modifier.border(
                                                width = 1.5.dp,
                                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f),
                                                shape = RoundedCornerShape(50)
                                            )
                                    )
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = profile.name,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Normal,
                                color = if (isActive) primary
                                        else MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f)
                            )
                            AnimatedVisibility(
                                visible = isActive,
                                enter = fadeIn(animationSpec = tween(150)),
                                exit = fadeOut(animationSpec = tween(150))
                            ) {
                                Icon(
                                    modifier = Modifier.size(16.dp),
                                    painter = painterResource(R.drawable.ic_check),
                                    contentDescription = null,
                                    tint = primary
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
