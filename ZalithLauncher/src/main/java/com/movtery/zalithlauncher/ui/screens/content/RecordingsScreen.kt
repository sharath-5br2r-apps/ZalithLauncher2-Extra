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

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.MediaStore
import android.text.format.Formatter
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.movtery.zalithlauncher.R
import com.movtery.zalithlauncher.ui.base.BaseScreen
import com.movtery.zalithlauncher.ui.components.RecordingPlayerOverlay
import com.movtery.zalithlauncher.ui.screens.NormalNavKey
import com.movtery.zalithlauncher.viewmodel.ScreenBackStackViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class RecordingEntry(
    val uri: Uri,
    val id: Long,
    val displayName: String,
    val durationMs: Long,
    val sizeBytes: Long,
    val dateAddedSec: Long,
    val width: Int,
    val height: Int
)

@Composable
fun RecordingsScreen(backStackViewModel: ScreenBackStackViewModel) {
    BaseScreen(
        screenKey = NormalNavKey.Recordings,
        currentKey = backStackViewModel.mainScreen.currentKey
    ) {
        val context = LocalContext.current
        val scope = rememberCoroutineScope()

        var recordings by remember { mutableStateOf<List<RecordingEntry>>(emptyList()) }
        var loading by remember { mutableStateOf(true) }
        val thumbnails = remember { mutableStateMapOf<Long, Bitmap>() }
        var renameTarget  by remember { mutableStateOf<RecordingEntry?>(null) }
        var deleteTarget  by remember { mutableStateOf<RecordingEntry?>(null) }
        var playingEntry  by remember { mutableStateOf<RecordingEntry?>(null) }

        fun reload() {
            scope.launch {
                loading = true
                recordings = withContext(Dispatchers.IO) { queryRecordings(context) }
                loading = false
            }
        }

        LaunchedEffect(Unit) { reload() }

        Column(modifier = Modifier.fillMaxSize()) {
            if (loading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = stringResource(R.string.generic_loading),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else if (recordings.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            painter = painterResource(R.drawable.ic_videocam_outlined),
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                        )
                        Spacer(Modifier.height(12.dp))
                        Text(
                            text = stringResource(R.string.recordings_empty),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(recordings, key = { it.id }) { entry ->
                        LaunchedEffect(entry.id) {
                            if (!thumbnails.containsKey(entry.id)) {
                                val bmp = withContext(Dispatchers.IO) {
                                    loadThumbnail(context, entry.uri)
                                }
                                if (bmp != null) thumbnails[entry.id] = bmp
                            }
                        }
                        RecordingCard(
                            entry = entry,
                            thumbnail = thumbnails[entry.id],
                            onPlay = { playingEntry = entry },
                            onShare = { shareRecording(context, entry.uri) },
                            onRename = { renameTarget = entry },
                            onDelete = { deleteTarget = entry },
                            onReveal = {
                                val dir = android.os.Environment
                                    .getExternalStoragePublicDirectory(
                                        android.os.Environment.DIRECTORY_MOVIES
                                    )
                                val uri2 = Uri.parse("$dir/Zeryth Recordings")
                                val intent = Intent(Intent.ACTION_VIEW).apply {
                                    setDataAndType(uri2, "*/*")
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                }
                                runCatching { context.startActivity(intent) }
                            }
                        )
                    }
                }
            }
        }

        renameTarget?.let { entry ->
            var name by remember(entry.id) {
                mutableStateOf(entry.displayName.removeSuffix(".mp4"))
            }
            AlertDialog(
                onDismissRequest = { renameTarget = null },
                title = { Text(stringResource(R.string.recordings_rename)) },
                text = {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text(stringResource(R.string.recordings_rename_hint)) },
                        singleLine = true
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        val newName = "${name.trim()}.mp4"
                        scope.launch(Dispatchers.IO) {
                            val values = ContentValues().apply {
                                put(MediaStore.Video.Media.DISPLAY_NAME, newName)
                            }
                            context.contentResolver.update(entry.uri, values, null, null)
                            withContext(Dispatchers.Main) { renameTarget = null; reload() }
                        }
                    }) { Text(stringResource(R.string.generic_save)) }
                },
                dismissButton = {
                    TextButton(onClick = { renameTarget = null }) {
                        Text(stringResource(R.string.generic_cancel))
                    }
                }
            )
        }

        deleteTarget?.let { entry ->
            AlertDialog(
                onDismissRequest = { deleteTarget = null },
                title = { Text(stringResource(R.string.recordings_delete_title)) },
                text = {
                    Text(stringResource(R.string.recordings_delete_message, entry.displayName))
                },
                confirmButton = {
                    TextButton(onClick = {
                        scope.launch(Dispatchers.IO) {
                            runCatching { context.contentResolver.delete(entry.uri, null, null) }
                            withContext(Dispatchers.Main) { deleteTarget = null; reload() }
                        }
                    }) {
                        Text(
                            stringResource(R.string.generic_delete),
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                },
                dismissButton = {
                    TextButton(onClick = { deleteTarget = null }) {
                        Text(stringResource(R.string.generic_cancel))
                    }
                }
            )
        }

        playingEntry?.let { entry ->
            RecordingPlayerOverlay(
                uri   = entry.uri,
                title = entry.displayName.removeSuffix(".mp4"),
                onDismiss = { playingEntry = null }
            )
        }
    }
}

@Composable
private fun RecordingCard(
    entry: RecordingEntry,
    thumbnail: Bitmap?,
    onPlay: () -> Unit,
    onShare: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    onReveal: () -> Unit,
) {
    val context = LocalContext.current
    var menuExpanded by remember { mutableStateOf(false) }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onLongClick = { menuExpanded = true }, onClick = onPlay),
        shape = MaterialTheme.shapes.medium,
        tonalElevation = 2.dp
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .width(120.dp)
                    .aspectRatio(16f / 9f)
                    .clip(MaterialTheme.shapes.small)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                if (thumbnail != null) {
                    Image(
                        bitmap = thumbnail.asImageBitmap(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Icon(
                        painter = painterResource(R.drawable.ic_videocam_outlined),
                        contentDescription = null,
                        modifier = Modifier.size(32.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                    )
                }
            }

            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = entry.displayName.removeSuffix(".mp4"),
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(4.dp))
                val parts = buildList {
                    val dur = formatDuration(entry.durationMs)
                    if (dur.isNotEmpty()) add(dur)
                    if (entry.sizeBytes > 0)
                        add(Formatter.formatShortFileSize(context, entry.sizeBytes))
                    if (entry.width > 0 && entry.height > 0)
                        add("${entry.width}\u00d7${entry.height}")
                    add(SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                        .format(Date(entry.dateAddedSec * 1000L)))
                }
                Text(
                    text = parts.joinToString(" \u00b7 "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Box {
                IconButton(onClick = { menuExpanded = true }) {
                    Icon(painterResource(R.drawable.ic_more_vert), contentDescription = null)
                }
                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false }
                ) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.recordings_play)) },
                        leadingIcon = { Icon(painterResource(R.drawable.ic_play_arrow_filled), null) },
                        onClick = { menuExpanded = false; onPlay() }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.recordings_share)) },
                        leadingIcon = { Icon(painterResource(R.drawable.ic_share_filled), null) },
                        onClick = { menuExpanded = false; onShare() }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.recordings_rename)) },
                        leadingIcon = { Icon(painterResource(R.drawable.ic_edit_outlined), null) },
                        onClick = { menuExpanded = false; onRename() }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.recordings_reveal)) },
                        leadingIcon = { Icon(painterResource(R.drawable.ic_folder_outlined), null) },
                        onClick = { menuExpanded = false; onReveal() }
                    )
                    DropdownMenuItem(
                        text = {
                            Text(
                                stringResource(R.string.generic_delete),
                                color = MaterialTheme.colorScheme.error
                            )
                        },
                        leadingIcon = {
                            Icon(
                                painterResource(R.drawable.ic_delete_outlined),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error
                            )
                        },
                        onClick = { menuExpanded = false; onDelete() }
                    )
                }
            }
        }
    }
}

private fun queryRecordings(context: Context): List<RecordingEntry> {
    val projection = arrayOf(
        MediaStore.Video.Media._ID,
        MediaStore.Video.Media.DISPLAY_NAME,
        MediaStore.Video.Media.DURATION,
        MediaStore.Video.Media.SIZE,
        MediaStore.Video.Media.DATE_ADDED,
        MediaStore.Video.Media.WIDTH,
        MediaStore.Video.Media.HEIGHT
    )
    val selection = "${MediaStore.Video.Media.RELATIVE_PATH} LIKE ? AND " +
            "${MediaStore.Video.Media.IS_PENDING} = 0"
    val selectionArgs = arrayOf("%Zeryth Recordings%")
    val sortOrder = "${MediaStore.Video.Media.DATE_ADDED} DESC"

    val results = mutableListOf<RecordingEntry>()
    context.contentResolver.query(
        MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
        projection, selection, selectionArgs, sortOrder
    )?.use { cursor ->
        val idCol   = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
        val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
        val durCol  = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)
        val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE)
        val dateCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_ADDED)
        val wCol    = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.WIDTH)
        val hCol    = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.HEIGHT)
        while (cursor.moveToNext()) {
            val id = cursor.getLong(idCol)
            results += RecordingEntry(
                uri = ContentUris.withAppendedId(
                    MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id
                ),
                id = id,
                displayName = cursor.getString(nameCol) ?: "recording.mp4",
                durationMs  = cursor.getLong(durCol),
                sizeBytes   = cursor.getLong(sizeCol),
                dateAddedSec = cursor.getLong(dateCol),
                width  = cursor.getInt(wCol),
                height = cursor.getInt(hCol)
            )
        }
    }
    return results
}

private fun loadThumbnail(context: Context, uri: Uri): Bitmap? = runCatching {
    MediaMetadataRetriever().use { r ->
        r.setDataSource(context, uri)
        r.getFrameAtTime(0L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
    }
}.getOrNull()

private fun formatDuration(ms: Long): String {
    if (ms <= 0L) return ""
    val sec = ms / 1000L
    val h = sec / 3600; val m = (sec % 3600) / 60; val s = sec % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

private fun playRecording(context: Context, uri: Uri) {
    val intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, "video/mp4")
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    runCatching { context.startActivity(Intent.createChooser(intent, null).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }) }
}

private fun shareRecording(context: Context, uri: Uri) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "video/mp4"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    runCatching { context.startActivity(Intent.createChooser(intent, null).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }) }
}
