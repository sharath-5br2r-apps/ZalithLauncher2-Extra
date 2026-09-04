package com.movtery.zalithlauncher.ui.screens.content

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.movtery.zalithlauncher.R
import com.movtery.zalithlauncher.game.path.getGameHome
import com.movtery.zalithlauncher.ui.androidText
import com.movtery.zalithlauncher.ui.base.BaseScreen
import com.movtery.zalithlauncher.ui.components.CardTitleLayout
import com.movtery.zalithlauncher.ui.components.IconTextButton
import com.movtery.zalithlauncher.ui.components.SimpleTextInputField
import com.movtery.zalithlauncher.ui.screens.NormalNavKey
import com.movtery.zalithlauncher.ui.screens.content.elements.CreateNewDirDialog
import com.movtery.zalithlauncher.ui.screens.content.elements.CreateNewFileDialog
import com.movtery.zalithlauncher.ui.screens.content.elements.SortByDropdownMenu
import com.movtery.zalithlauncher.ui.screens.content.elements.SortByEnum
import com.movtery.zalithlauncher.ui.screens.content.versions.layouts.VersionChunkBackground
import com.movtery.zalithlauncher.ui.theme.itemColor
import com.movtery.zalithlauncher.ui.theme.onItemColor
import com.movtery.zalithlauncher.utils.animation.getAnimateTween
import com.movtery.zalithlauncher.utils.logging.Logger
import com.movtery.zalithlauncher.viewmodel.ErrorViewModel
import com.movtery.zalithlauncher.viewmodel.ScreenBackStackViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val TAG = "BuiltInFileManager"

private val SIDEBAR_WIDTH = 180.dp

private val SidebarSlideEnterSpec: FiniteAnimationSpec<IntOffset> =
    tween(320, easing = FastOutSlowInEasing)
private val SidebarSlideExitSpec: FiniteAnimationSpec<IntOffset> =
    tween(280, easing = FastOutSlowInEasing)
private val SectionExpandSpec: FiniteAnimationSpec<IntSize> =
    tween(260, easing = FastOutSlowInEasing)
private val SectionShrinkSpec: FiniteAnimationSpec<IntSize> =
    tween(220, easing = FastOutSlowInEasing)
private val SectionFadeInSpec: FiniteAnimationSpec<Float> =
    tween(200, delayMillis = 50)
private val SectionFadeOutSpec: FiniteAnimationSpec<Float> =
    tween(160)

private val EDITABLE_EXTENSIONS = setOf(
    "txt", "json", "json5", "properties", "yml", "yaml", "cfg", "conf",
    "toml", "log", "mcmeta", "lang", "ini", "xml", "md", "gradle", "sh"
)

private fun isEditableTextFile(file: File): Boolean {
    val ext = file.extension.lowercase()
    return ext.isEmpty() || ext in EDITABLE_EXTENSIONS
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BuiltInFileManagerScreen(
    key: NormalNavKey.BuiltInFileManager,
    backStackViewModel: ScreenBackStackViewModel,
    submitError: (ErrorViewModel.ThrowableMessage) -> Unit,
    navigateToEditor: (path: String) -> Unit,
) {
    val context = LocalContext.current
    val rootDirectory = remember { File(getGameHome()) }
    val initialPath = key.startPath?.let { File(it) }
        ?.takeIf { it.exists() && it.isDirectory }
        ?: rootDirectory

    var currentDirectory by remember { mutableStateOf(initialPath) }
    LaunchedEffect(key.startPath) {
        val requested = key.startPath?.let { File(it) }?.takeIf { it.exists() && it.isDirectory }
        if (requested != null) currentDirectory = requested
    }
    var refreshCounter by remember { mutableStateOf(0) }

    var sidebarVisible by rememberSaveable { mutableStateOf(true) }

    var gameSectionExpanded by rememberSaveable { mutableStateOf(true) }
    var contentSectionExpanded by rememberSaveable { mutableStateOf(false) }
    var mediaSectionExpanded by rememberSaveable { mutableStateOf(false) }

    var showGoToPathDialog by remember { mutableStateOf(false) }
    var goToPathText by remember { mutableStateOf("") }
    var goToPathError by remember { mutableStateOf<String?>(null) }

    var selectedFile by remember { mutableStateOf<File?>(null) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var renameText by remember { mutableStateOf("") }

    var fileToDelete by remember { mutableStateOf<File?>(null) }
    var showDeleteDialog by remember { mutableStateOf(false) }

    var showBulkDeleteDialog by remember { mutableStateOf(false) }

    var propertiesFiles by remember { mutableStateOf<List<File>>(emptyList()) }
    var showPropertiesDialog by remember { mutableStateOf(false) }

    var showCreateFolderDialog by remember { mutableStateOf(false) }
    var newFolderName by remember { mutableStateOf("") }

    var showCreateFileDialog by remember { mutableStateOf(false) }

    var searchQuery by remember { mutableStateOf("") }
    var sortByEnum by remember { mutableStateOf(SortByEnum.FileName) }
    var isAscending by remember { mutableStateOf(true) }

    var clipboardFiles by remember { mutableStateOf<List<File>>(emptyList()) }
    var clipboardIsCut by remember { mutableStateOf(false) }
    var showClipboardDialog by remember { mutableStateOf(false) }

    var selectionMode by remember { mutableStateOf(false) }
    var selectedPaths by remember { mutableStateOf<Set<String>>(emptySet()) }

    val modsFolder = remember(rootDirectory) { File(rootDirectory, "mods") }
    val resourcePacksFolder = remember(rootDirectory) { File(rootDirectory, "resourcepacks") }
    val screenshotsFolder = remember(rootDirectory) { File(rootDirectory, "screenshots") }

    val inModsSubtree = remember(currentDirectory, modsFolder) {
        val base = modsFolder.absolutePath
        currentDirectory.absolutePath == base ||
            currentDirectory.absolutePath.startsWith(base + File.separator)
    }
    val inResourcePacksSubtree = remember(currentDirectory, resourcePacksFolder) {
        val base = resourcePacksFolder.absolutePath
        currentDirectory.absolutePath == base ||
            currentDirectory.absolutePath.startsWith(base + File.separator)
    }
    val inScreenshotsSubtree = remember(currentDirectory, screenshotsFolder) {
        val base = screenshotsFolder.absolutePath
        currentDirectory.absolutePath == base ||
            currentDirectory.absolutePath.startsWith(base + File.separator)
    }
    val inGameRoot = !inModsSubtree && !inResourcePacksSubtree && !inScreenshotsSubtree

    var files by remember { mutableStateOf<List<File>>(emptyList()) }
    var isLoadingFiles by remember { mutableStateOf(true) }

    LaunchedEffect(currentDirectory, refreshCounter, sortByEnum, isAscending) {
        isLoadingFiles = true
        val result = withContext(Dispatchers.IO) {
            currentDirectory.listFiles()
                ?.sortedWith(Comparator { a, b ->
                    val dirFirst = b.isDirectory.compareTo(a.isDirectory)
                    if (dirFirst != 0) return@Comparator dirFirst
                    val cmp = when (sortByEnum) {
                        SortByEnum.FileName ->
                            a.name.lowercase().compareTo(b.name.lowercase())
                        SortByEnum.FileModifiedTime ->
                            b.lastModified().compareTo(a.lastModified())
                        else ->
                            a.name.lowercase().compareTo(b.name.lowercase())
                    }
                    if (isAscending) cmp else -cmp
                }) ?: emptyList()
        }
        files = result
        isLoadingFiles = false
    }

    val displayedFiles = remember(files, searchQuery) {
        if (searchQuery.isBlank()) files
        else files.filter { it.name.contains(searchQuery, ignoreCase = true) }
    }

    val selectedFiles = remember(selectedPaths, displayedFiles) {
        displayedFiles.filter { it.absolutePath in selectedPaths }
    }

    fun importFile(uri: Uri) {
        val fileName = context.contentResolver
            .query(uri, null, null, null, null)
            ?.use { cursor ->
                val col = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (cursor.moveToFirst() && col >= 0) cursor.getString(col) else null
            } ?: "ImportedFile"
        val dest = File(currentDirectory, fileName)
        context.contentResolver.openInputStream(uri)?.use { inp ->
            dest.outputStream().use { out -> inp.copyTo(out) }
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        uris.forEach { importFile(it) }
        refreshCounter++
    }

    BaseScreen(
        screenKey = key,
        currentKey = backStackViewModel.mainScreen.currentKey,
        useClassEquality = true
    ) { isVisible ->
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            AnimatedVisibility(
                visible = sidebarVisible,
                enter = slideInHorizontally(SidebarSlideEnterSpec) { -it } +
                    fadeIn(tween(260, delayMillis = 80)),
                exit = fadeOut(tween(160)) +
                    slideOutHorizontally(SidebarSlideExitSpec) { -it }
            ) {
                Row(modifier = Modifier.fillMaxHeight()) {
                    Column(
                        modifier = Modifier
                            .width(SIDEBAR_WIDTH)
                            .fillMaxHeight()
                    ) {
                        val hasClipboard = clipboardFiles.isNotEmpty()
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(28.dp),
                            elevation = CardDefaults.cardElevation(2.dp),
                            onClick = {
                                if (hasClipboard) {
                                    showClipboardDialog = true
                                } else {
                                    goToPathText = currentDirectory.absolutePath
                                    goToPathError = null
                                    showGoToPathDialog = true
                                }
                            }
                        ) {
                            Crossfade(
                                targetState = hasClipboard,
                                label = "sidebar_pill_crossfade"
                            ) { isClipboardActive ->
                                Row(
                                    modifier = Modifier.padding(
                                        horizontal = 14.dp, vertical = 12.dp
                                    ),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    if (isClipboardActive) {
                                        Icon(
                                            painter = painterResource(
                                                if (clipboardIsCut) R.drawable.ic_file_copy_filled
                                                else R.drawable.ic_copy_all_outlined
                                            ),
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp),
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                        Spacer(Modifier.width(8.dp))
                                        Column {
                                            Text(
                                                text = stringResource(R.string.file_manager_clipboard),
                                                style = MaterialTheme.typography.titleSmall,
                                                maxLines = 1,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                            Text(
                                                text = buildClipboardLabel(
                                                    clipboardFiles, clipboardIsCut
                                                ),
                                                style = MaterialTheme.typography.bodySmall,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    } else {
                                        Icon(
                                            painter = painterResource(R.drawable.ic_folder_outlined),
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(Modifier.width(8.dp))
                                        Column {
                                            Text(
                                                text = stringResource(R.string.file_manager_current_folder),
                                                style = MaterialTheme.typography.titleSmall,
                                                maxLines = 1
                                            )
                                            Text(
                                                text = currentDirectory.name.ifEmpty { stringResource(R.string.file_manager_game_folder) },
                                                style = MaterialTheme.typography.bodySmall,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(Modifier.height(12.dp))

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .verticalScroll(rememberScrollState()),
                                verticalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                CollapsibleSidebarSection(
                                    title = stringResource(R.string.file_manager_sidebar_game),
                                    expanded = gameSectionExpanded,
                                    onToggle = { gameSectionExpanded = !gameSectionExpanded }
                                ) {
                                    SidebarNavItem(
                                        icon = R.drawable.ic_folder_outlined,
                                        label = stringResource(R.string.file_manager_game_folder),
                                        selected = inGameRoot,
                                        onClick = { currentDirectory = rootDirectory }
                                    )
                                }

                                Spacer(Modifier.height(4.dp))

                                CollapsibleSidebarSection(
                                    title = stringResource(R.string.file_manager_sidebar_content),
                                    expanded = contentSectionExpanded,
                                    onToggle = { contentSectionExpanded = !contentSectionExpanded }
                                ) {
                                    SidebarNavItem(
                                        icon = R.drawable.ic_extension_outlined,
                                        label = stringResource(R.string.file_manager_sidebar_mods),
                                        selected = inModsSubtree,
                                        onClick = {
                                            if (!modsFolder.exists()) modsFolder.mkdirs()
                                            currentDirectory = modsFolder
                                        }
                                    )
                                    SidebarNavItem(
                                        icon = R.drawable.ic_folder_zip_outlined,
                                        label = stringResource(R.string.file_manager_sidebar_resourcepacks),
                                        selected = inResourcePacksSubtree,
                                        onClick = {
                                            if (!resourcePacksFolder.exists())
                                                resourcePacksFolder.mkdirs()
                                            currentDirectory = resourcePacksFolder
                                        }
                                    )
                                }

                                Spacer(Modifier.height(4.dp))

                                CollapsibleSidebarSection(
                                    title = stringResource(R.string.file_manager_sidebar_media),
                                    expanded = mediaSectionExpanded,
                                    onToggle = { mediaSectionExpanded = !mediaSectionExpanded }
                                ) {
                                    SidebarNavItem(
                                        icon = R.drawable.ic_image_outlined,
                                        label = stringResource(R.string.file_manager_sidebar_screenshots),
                                        selected = inScreenshotsSubtree,
                                        onClick = {
                                            if (!screenshotsFolder.exists())
                                                screenshotsFolder.mkdirs()
                                            currentDirectory = screenshotsFolder
                                        }
                                    )
                                }

                                Spacer(Modifier.height(4.dp))
                            }
                        }

                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 8.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                        )

                        SidebarStorageFooter(rootDirectory = rootDirectory)

                        Spacer(Modifier.height(8.dp))
                    }

                    Spacer(Modifier.width(12.dp))
                }
            }

            VersionChunkBackground(
                modifier = Modifier.fillMaxSize(),
                paddingValues = PaddingValues()
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    CardTitleLayout(modifier = Modifier.fillMaxWidth()) {
                        if (selectionMode) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 4.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                IconButton(onClick = { sidebarVisible = !sidebarVisible }) {
                                    Icon(
                                        painter = painterResource(
                                            if (sidebarVisible) R.drawable.ic_menu_open
                                            else R.drawable.ic_menu
                                        ),
                                        contentDescription = if (sidebarVisible)
                                            stringResource(R.string.file_manager_sidebar_hide) else stringResource(R.string.file_manager_sidebar_show)
                                    )
                                }
                                IconButton(onClick = {
                                    selectionMode = false
                                    selectedPaths = emptySet()
                                }) {
                                    Icon(
                                        painter = painterResource(R.drawable.ic_close),
                                        contentDescription = stringResource(R.string.file_manager_exit_selection)
                                    )
                                }
                                Text(
                                    text = stringResource(R.string.file_manager_items_selected, selectedPaths.size),
                                    style = MaterialTheme.typography.titleSmall,
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(start = 2.dp),
                                    maxLines = 1
                                )

                                val hasSelection = selectedPaths.isNotEmpty()
                                val singleSelection = selectedPaths.size == 1

                                IconButton(
                                    onClick = {
                                        clipboardFiles = selectedFiles
                                        clipboardIsCut = true
                                        selectionMode = false
                                        selectedPaths = emptySet()
                                    },
                                    enabled = hasSelection
                                ) {
                                    Icon(
                                        painter = painterResource(R.drawable.ic_file_copy_filled),
                                        contentDescription = stringResource(R.string.file_manager_cut),
                                        tint = if (hasSelection) LocalContentColor.current
                                        else LocalContentColor.current.copy(alpha = 0.38f)
                                    )
                                }
                                IconButton(
                                    onClick = {
                                        clipboardFiles = selectedFiles
                                        clipboardIsCut = false
                                        selectionMode = false
                                        selectedPaths = emptySet()
                                    },
                                    enabled = hasSelection
                                ) {
                                    Icon(
                                        painter = painterResource(R.drawable.ic_copy_all_outlined),
                                        contentDescription = stringResource(R.string.file_manager_copy),
                                        tint = if (hasSelection) LocalContentColor.current
                                        else LocalContentColor.current.copy(alpha = 0.38f)
                                    )
                                }
                                IconButton(
                                    onClick = { showBulkDeleteDialog = true },
                                    enabled = hasSelection
                                ) {
                                    Icon(
                                        painter = painterResource(R.drawable.ic_delete_outlined),
                                        contentDescription = stringResource(R.string.file_manager_delete),
                                        tint = if (hasSelection)
                                            MaterialTheme.colorScheme.error
                                        else LocalContentColor.current.copy(alpha = 0.38f)
                                    )
                                }
                                IconButton(
                                    onClick = {
                                        val f = selectedFiles.firstOrNull() ?: return@IconButton
                                        selectedFile = f
                                        renameText = f.name
                                        showRenameDialog = true
                                    },
                                    enabled = singleSelection
                                ) {
                                    Icon(
                                        painter = painterResource(R.drawable.ic_edit_outlined),
                                        contentDescription = stringResource(R.string.file_manager_rename),
                                        tint = if (singleSelection) LocalContentColor.current
                                        else LocalContentColor.current.copy(alpha = 0.38f)
                                    )
                                }
                                IconButton(
                                    onClick = {
                                        propertiesFiles = selectedFiles
                                        showPropertiesDialog = true
                                    },
                                    enabled = hasSelection
                                ) {
                                    Icon(
                                        painter = painterResource(R.drawable.ic_info_outlined),
                                        contentDescription = stringResource(R.string.file_manager_properties),
                                        tint = if (hasSelection) LocalContentColor.current
                                        else LocalContentColor.current.copy(alpha = 0.38f)
                                    )
                                }
                                IconButton(onClick = {
                                    selectedPaths = displayedFiles.map { it.absolutePath }.toSet()
                                }) {
                                    Icon(
                                        painter = painterResource(R.drawable.ic_select_all),
                                        contentDescription = stringResource(R.string.file_manager_select_all)
                                    )
                                }
                                IconButton(
                                    onClick = { selectedPaths = emptySet() },
                                    enabled = hasSelection
                                ) {
                                    Icon(
                                        painter = painterResource(R.drawable.ic_deselect),
                                        contentDescription = stringResource(R.string.file_manager_deselect_all),
                                        tint = if (hasSelection) LocalContentColor.current
                                        else LocalContentColor.current.copy(alpha = 0.38f)
                                    )
                                }
                            }
                        } else {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 8.dp)
                                    .padding(top = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                IconButton(onClick = { sidebarVisible = !sidebarVisible }) {
                                    Icon(
                                        painter = painterResource(
                                            if (sidebarVisible) R.drawable.ic_menu_open
                                            else R.drawable.ic_menu
                                        ),
                                        contentDescription = if (sidebarVisible)
                                            stringResource(R.string.file_manager_sidebar_hide) else stringResource(R.string.file_manager_sidebar_show)
                                    )
                                }

                                Box {
                                    var sortExpanded by remember { mutableStateOf(false) }
                                    IconButton(onClick = { sortExpanded = !sortExpanded }) {
                                        Icon(
                                            painter = painterResource(R.drawable.ic_sort),
                                            contentDescription = stringResource(R.string.file_manager_sort)
                                        )
                                    }
                                    SortByDropdownMenu(
                                        expanded = sortExpanded,
                                        onClose = { sortExpanded = false },
                                        enums = listOf(
                                            SortByEnum.FileName,
                                            SortByEnum.FileModifiedTime
                                        ),
                                        currentEnum = sortByEnum,
                                        onEnumChanged = { sortByEnum = it },
                                        isAscending = isAscending,
                                        onToggleSortOrder = { isAscending = !isAscending }
                                    )
                                }

                                SimpleTextInputField(
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(start = 4.dp),
                                    value = searchQuery,
                                    onValueChange = { searchQuery = it },
                                    hint = {
                                        Text(
                                            text = stringResource(R.string.file_manager_search_files),
                                            style = TextStyle(
                                                color = LocalContentColor.current,
                                                fontSize = 12.sp
                                            )
                                        )
                                    },
                                    color = itemColor(),
                                    contentColor = onItemColor(),
                                    singleLine = true
                                )

                                IconButton(onClick = {
                                    selectionMode = true
                                    selectedPaths = emptySet()
                                }) {
                                    Icon(
                                        painter = painterResource(R.drawable.ic_select_all),
                                        contentDescription = stringResource(R.string.file_manager_selection_mode)
                                    )
                                }

                                if (clipboardFiles.isNotEmpty()) {
                                    TextButton(onClick = {
                                        clipboardFiles.forEach { source ->
                                            val dest = File(currentDirectory, source.name)
                                            if (clipboardIsCut) source.renameTo(dest)
                                            else source.copyRecursively(dest, overwrite = true)
                                        }
                                        if (clipboardIsCut) clipboardFiles = emptyList()
                                        clipboardIsCut = false
                                        refreshCounter++
                                    }) {
                                        Text(if (clipboardIsCut) stringResource(R.string.file_manager_move_here) else stringResource(R.string.file_manager_paste_here))
                                    }
                                }

                                Spacer(Modifier.width(6.dp))

                                IconTextButton(
                                    onClick = {
                                        newFolderName = ""
                                        showCreateFolderDialog = true
                                    },
                                    painter = painterResource(R.drawable.ic_folder_outlined),
                                    text = stringResource(R.string.file_manager_new_folder)
                                )
                                IconTextButton(
                                    onClick = { showCreateFileDialog = true },
                                    painter = painterResource(R.drawable.ic_article_outlined),
                                    text = stringResource(R.string.file_manager_new_file)
                                )
                                IconTextButton(
                                    onClick = { importLauncher.launch(arrayOf("*/*")) },
                                    painter = painterResource(R.drawable.ic_upload),
                                    text = stringResource(R.string.file_manager_import)
                                )
                                IconButton(onClick = { refreshCounter++ }) {
                                    Icon(
                                        painter = painterResource(R.drawable.ic_refresh),
                                        contentDescription = stringResource(R.string.file_manager_refresh)
                                    )
                                }
                            }
                        }
                    }

                    val breadcrumbSegments = remember(currentDirectory, rootDirectory) {
                        try {
                            currentDirectory.relativeTo(rootDirectory)
                                .invariantSeparatorsPath
                                .split("/")
                                .filter { it.isNotBlank() }
                        } catch (_: IllegalArgumentException) {
                            emptyList()
                        }
                    }

                    val atRoot = remember(currentDirectory, rootDirectory) {
                        currentDirectory.absolutePath == rootDirectory.absolutePath
                    }

                    val breadcrumbScrollState = rememberScrollState()
                    LaunchedEffect(currentDirectory) {
                        breadcrumbScrollState.animateScrollTo(Int.MAX_VALUE)
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AnimatedVisibility(
                            visible = !atRoot,
                            enter = fadeIn(tween(180)) + expandHorizontally(
                                animationSpec = tween(200, easing = FastOutSlowInEasing),
                                expandFrom = Alignment.Start
                            ),
                            exit = fadeOut(tween(140)) + shrinkHorizontally(
                                animationSpec = tween(180, easing = FastOutSlowInEasing),
                                shrinkTowards = Alignment.Start
                            )
                        ) {
                            IconButton(
                                onClick = {
                                    val parent = currentDirectory.parentFile
                                    if (parent != null) currentDirectory = parent
                                },
                                modifier = Modifier
                                    .padding(start = 4.dp)
                                    .size(36.dp)
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_arrow_back),
                                    contentDescription = stringResource(R.string.file_manager_up_one_folder),
                                    modifier = Modifier
                                        .size(18.dp)
                                        .graphicsLayer { rotationZ = 90f }
                                )
                            }
                        }

                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .horizontalScroll(breadcrumbScrollState)
                                .padding(start = 4.dp, end = 12.dp, top = 2.dp, bottom = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TextButton(
                                onClick = { currentDirectory = rootDirectory },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = stringResource(R.string.file_manager_game_folder),
                                    style = MaterialTheme.typography.labelMedium,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }
                            breadcrumbSegments.forEachIndexed { index, segment ->
                                val isLast = index == breadcrumbSegments.lastIndex
                                Text(
                                    text = " \u203A ",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                    maxLines = 1,
                                    softWrap = false
                                )
                                if (!isLast) {
                                    val target = File(
                                        rootDirectory,
                                        breadcrumbSegments.take(index + 1).joinToString("/")
                                    )
                                    TextButton(
                                        onClick = { currentDirectory = target },
                                        contentPadding = PaddingValues(
                                            horizontal = 8.dp, vertical = 2.dp
                                        )
                                    ) {
                                        Text(
                                            text = segment,
                                            style = MaterialTheme.typography.labelMedium,
                                            maxLines = 1,
                                            softWrap = false
                                        )
                                    }
                                } else {
                                    Text(
                                        text = segment,
                                        style = MaterialTheme.typography.labelMedium.copy(
                                            fontWeight = FontWeight.SemiBold
                                        ),
                                        modifier = Modifier.padding(horizontal = 4.dp),
                                        maxLines = 1,
                                        softWrap = false
                                    )
                                }
                            }
                        }
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        if (isLoadingFiles) {
                            CircularProgressIndicator(modifier = Modifier.size(36.dp))
                        } else if (displayedFiles.isEmpty()) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_folder_outlined),
                                    contentDescription = null,
                                    modifier = Modifier.size(48.dp),
                                    tint = LocalContentColor.current.copy(alpha = 0.35f)
                                )
                                Text(
                                    text = if (searchQuery.isBlank()) stringResource(R.string.file_manager_no_files)
                                    else stringResource(R.string.file_manager_no_matching_files),
                                    style = MaterialTheme.typography.titleSmall
                                )
                                Text(
                                    text = if (searchQuery.isBlank())
                                        stringResource(R.string.file_manager_import_files_hint)
                                    else stringResource(R.string.file_manager_try_different_search),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                )
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(all = 12.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                items(
                                    items = displayedFiles,
                                    key = { it.absolutePath }
                                ) { file ->
                                    val isChecked = file.absolutePath in selectedPaths
                                    FileItemLayout(
                                        modifier = Modifier.fillMaxWidth(),
                                        file = file,
                                        selectionMode = selectionMode,
                                        isChecked = isChecked,
                                        onCheckedChange = { checked ->
                                            selectedPaths = if (checked)
                                                selectedPaths + file.absolutePath
                                            else
                                                selectedPaths - file.absolutePath
                                        },
                                        onClick = {
                                            if (selectionMode) {
                                                selectedPaths = if (isChecked)
                                                    selectedPaths - file.absolutePath
                                                else
                                                    selectedPaths + file.absolutePath
                                            } else {
                                                if (file.isDirectory) currentDirectory = file
                                                else if (isEditableTextFile(file)) navigateToEditor(file.absolutePath)
                                            }
                                        },
                                        onLongClick = {
                                            if (!selectionMode) {
                                                selectionMode = true
                                                selectedPaths = setOf(file.absolutePath)
                                            }
                                        },
                                        onRename = {
                                            renameText = file.name
                                            selectedFile = file
                                            showRenameDialog = true
                                        },
                                        onCopy = {
                                            clipboardFiles = listOf(file)
                                            clipboardIsCut = false
                                        },
                                        onCut = {
                                            clipboardFiles = listOf(file)
                                            clipboardIsCut = true
                                        },
                                        onProperties = {
                                            propertiesFiles = listOf(file)
                                            showPropertiesDialog = true
                                        },
                                        onDelete = {
                                            fileToDelete = file
                                            showDeleteDialog = true
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showGoToPathDialog) {
        val focusRequester = remember { FocusRequester() }
        val goToPathLabel = stringResource(R.string.file_manager_directory_path)
        val dirNotExist = context.getString(R.string.file_manager_dir_not_exist)
        val notADir = context.getString(R.string.file_manager_not_a_dir)
        val cannotAccess = context.getString(R.string.file_manager_cannot_access)
        AlertDialog(
            onDismissRequest = { showGoToPathDialog = false },
            title = { Text(stringResource(R.string.file_manager_go_to_path)) },
            text = {
                OutlinedTextField(
                    value = goToPathText,
                    onValueChange = { goToPathText = it; goToPathError = null },
                    label = { Text(goToPathLabel) },
                    singleLine = true,
                    isError = goToPathError != null,
                    supportingText = goToPathError?.let { err -> { Text(err) } },
                    modifier = Modifier.fillMaxWidth().focusRequester(focusRequester)
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val target = File(goToPathText.trim())
                    when {
                        !target.exists() -> goToPathError = dirNotExist
                        !target.isDirectory -> goToPathError = notADir
                        !target.canRead() -> goToPathError = cannotAccess
                        else -> { currentDirectory = target; showGoToPathDialog = false }
                    }
                }) { Text(stringResource(R.string.file_manager_go)) }
            },
            dismissButton = {
                TextButton(onClick = { showGoToPathDialog = false }) { Text(stringResource(R.string.generic_cancel)) }
            }
        )
        LaunchedEffect(Unit) { focusRequester.requestFocus() }
    }

    if (showRenameDialog) {
        AlertDialog(
            onDismissRequest = { showRenameDialog = false },
            title = { Text(stringResource(R.string.file_manager_rename)) },
            text = {
                OutlinedTextField(
                    value = renameText,
                    onValueChange = { renameText = it },
                    label = { Text(stringResource(R.string.file_manager_new_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    selectedFile?.renameTo(File(selectedFile!!.parent, renameText))
                    refreshCounter++
                    showRenameDialog = false
                }) { Text(stringResource(R.string.file_manager_rename)) }
            },
            dismissButton = {
                TextButton(onClick = { showRenameDialog = false }) { Text(stringResource(R.string.generic_cancel)) }
            }
        )
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text(stringResource(R.string.file_manager_delete)) },
            text = { Text(stringResource(R.string.file_manager_delete_confirm_file, fileToDelete?.name ?: "")) },
            confirmButton = {
                TextButton(onClick = {
                    fileToDelete?.deleteRecursively()
                    fileToDelete = null
                    refreshCounter++
                    showDeleteDialog = false
                }) { Text(stringResource(R.string.file_manager_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text(stringResource(R.string.generic_cancel)) }
            }
        )
    }

    if (showBulkDeleteDialog) {
        val count = selectedPaths.size
        AlertDialog(
            onDismissRequest = { showBulkDeleteDialog = false },
            title = { Text(stringResource(R.string.file_manager_delete_items_title)) },
            text = {
                Text(stringResource(R.string.file_manager_delete_items_confirm, count))
            },
            confirmButton = {
                TextButton(onClick = {
                    selectedFiles.forEach { it.deleteRecursively() }
                    selectionMode = false
                    selectedPaths = emptySet()
                    refreshCounter++
                    showBulkDeleteDialog = false
                }) {
                    Text(stringResource(R.string.file_manager_delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showBulkDeleteDialog = false }) { Text(stringResource(R.string.generic_cancel)) }
            }
        )
    }

    if (showPropertiesDialog && propertiesFiles.isNotEmpty()) {
        PropertiesDialog(
            files = propertiesFiles,
            onDismiss = { showPropertiesDialog = false }
        )
    }

    if (showCreateFolderDialog) {
        CreateNewDirDialog(
            onDismissRequest = {
                showCreateFolderDialog = false
            },
            createDir = { name ->
                File(currentDirectory, name).mkdirs()
                refreshCounter++
                showCreateFolderDialog = false
            }
        )
    }

    if (showCreateFileDialog) {
        CreateNewFileDialog(
            onDismissRequest = { showCreateFileDialog = false },
            createFile = { name ->
                runCatching {
                    val target = File(currentDirectory, name)
                    if (!target.createNewFile()) throw IllegalStateException("createNewFile returned false")
                }.onFailure { e ->
                    Logger.warning(TAG, "Failed to create file", e)
                    submitError(
                        ErrorViewModel.ThrowableMessage(
                            title = androidText(R.string.generic_warning),
                            message = androidText(R.string.file_manager_create_file_failed, e.message ?: e.javaClass.simpleName)
                        )
                    )
                }
                refreshCounter++
                showCreateFileDialog = false
            }
        )
    }

    if (showClipboardDialog && clipboardFiles.isNotEmpty()) {
        AlertDialog(
            onDismissRequest = { showClipboardDialog = false },
            icon = {
                Icon(
                    painter = painterResource(
                        if (clipboardIsCut) R.drawable.ic_file_copy_filled
                        else R.drawable.ic_copy_all_outlined
                    ),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            },
            title = {
                Text(if (clipboardIsCut) stringResource(R.string.file_manager_clipboard_cut_title) else stringResource(R.string.file_manager_clipboard_copy_title))
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = buildClipboardLabel(clipboardFiles, clipboardIsCut),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(4.dp))
                    clipboardFiles.take(5).forEach { file ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                painter = painterResource(getFileIcon(file)),
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = LocalContentColor.current.copy(alpha = 0.7f)
                            )
                            Text(
                                text = file.name,
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                    if (clipboardFiles.size > 5) {
                        Text(
                            text = stringResource(R.string.file_manager_and_more, clipboardFiles.size - 5),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = stringResource(R.string.file_manager_clipboard_destination, currentDirectory.name.ifEmpty { stringResource(R.string.file_manager_game_folder) }),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    clipboardFiles.forEach { source ->
                        val dest = File(currentDirectory, source.name)
                        if (clipboardIsCut) source.renameTo(dest)
                        else source.copyRecursively(dest, overwrite = true)
                    }
                    if (clipboardIsCut) clipboardFiles = emptyList()
                    clipboardIsCut = false
                    refreshCounter++
                    showClipboardDialog = false
                }) {
                    Text(stringResource(R.string.file_manager_paste_here))
                }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = {
                        clipboardFiles = emptyList()
                        clipboardIsCut = false
                        showClipboardDialog = false
                    }) { Text(stringResource(R.string.file_manager_clipboard_clear)) }
                    TextButton(onClick = { showClipboardDialog = false }) { Text(stringResource(R.string.generic_cancel)) }
                }
            }
        )
    }
}

@Composable
private fun CollapsibleSidebarSection(
    title: String,
    expanded: Boolean,
    onToggle: () -> Unit,
    content: @Composable () -> Unit
) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.medium)
                .clickable(onClick = onToggle)
                .padding(start = 16.dp, end = 8.dp, top = 5.dp, bottom = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelSmall.copy(
                    letterSpacing = 1.sp,
                    fontWeight = FontWeight.Medium
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
            )

            val chevronRotation by animateFloatAsState(
                targetValue = if (expanded) -90f else 180f,
                animationSpec = tween(240, easing = FastOutSlowInEasing),
                label = "chevron_$title"
            )
            Icon(
                painter = painterResource(R.drawable.ic_arrow_back),
                contentDescription = if (expanded) stringResource(R.string.file_manager_collapse_section, title) else stringResource(R.string.file_manager_expand_section, title),
                modifier = Modifier
                    .size(14.dp)
                    .graphicsLayer { rotationZ = chevronRotation },
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
            )
        }

        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(
                animationSpec = SectionExpandSpec,
                expandFrom = Alignment.Top
            ) + fadeIn(SectionFadeInSpec),
            exit = shrinkVertically(
                animationSpec = SectionShrinkSpec,
                shrinkTowards = Alignment.Top
            ) + fadeOut(SectionFadeOutSpec)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                content()
            }
        }
    }
}

@Composable
private fun SidebarNavItem(
    icon: Int,
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val backgroundColor by animateColorAsState(
        targetValue = if (selected)
            MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
        else Color.Transparent,
        animationSpec = tween(220),
        label = "navBg_$label"
    )
    val accentColor by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
        animationSpec = tween(220),
        label = "navAccent_$label"
    )
    val contentColor =
        if (selected) MaterialTheme.colorScheme.primary else LocalContentColor.current

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .background(backgroundColor)
            .combinedClickable(onClick = onClick)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .padding(start = 4.dp)
                .width(3.dp)
                .height(20.dp)
                .clip(RoundedCornerShape(50))
                .background(accentColor)
        )
        Spacer(Modifier.width(9.dp))
        Icon(
            painter = painterResource(id = icon),
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = contentColor
        )
        Spacer(Modifier.width(10.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
            ),
            color = contentColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun SidebarStorageFooter(rootDirectory: File) {
    var gameDirBytes by remember(rootDirectory) { mutableLongStateOf(0L) }
    var partitionTotal by remember(rootDirectory) { mutableLongStateOf(1L) }
    var partitionFree by remember(rootDirectory) { mutableLongStateOf(0L) }

    LaunchedEffect(rootDirectory) {
        withContext(Dispatchers.IO) {
            gameDirBytes = try {
                rootDirectory.walkTopDown().filter { it.isFile }.sumOf { it.length() }
            } catch (_: Exception) { 0L }
            partitionTotal = try {
                rootDirectory.totalSpace.coerceAtLeast(1L)
            } catch (_: Exception) { 1L }
            partitionFree = try {
                rootDirectory.usableSpace
            } catch (_: Exception) { 0L }
        }
    }

    val partitionUsed = (partitionTotal - partitionFree).coerceAtLeast(0L)
    val partitionProgress =
        (partitionUsed.toFloat() / partitionTotal.toFloat()).coerceIn(0f, 1f)

    val pathText = remember(rootDirectory) {
        val p = rootDirectory.absolutePath
        if (p.length > 28) "\u2026${p.takeLast(26)}" else p
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 2.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_folder_outlined),
                contentDescription = null,
                modifier = Modifier.size(11.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            )
            Text(
                text = pathText,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.file_manager_game_data),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            )
            Text(
                text = formatBytesShort(gameDirBytes),
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Medium
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.file_manager_storage),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            )
            Text(
                text = "${formatBytesShort(partitionUsed)} / ${formatBytesShort(partitionTotal)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            )
        }

        LinearProgressIndicator(
            progress = { partitionProgress },
            modifier = Modifier
                .fillMaxWidth()
                .height(3.dp)
                .clip(RoundedCornerShape(50)),
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.65f),
            trackColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
        )
    }
}

@Composable
private fun FileItemLayout(
    modifier: Modifier = Modifier,
    file: File,
    selectionMode: Boolean,
    isChecked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onRename: () -> Unit,
    onCopy: () -> Unit,
    onCut: () -> Unit,
    onProperties: () -> Unit,
    onDelete: () -> Unit
) {
    val scale = remember { Animatable(initialValue = 0.95f) }
    LaunchedEffect(Unit) {
        scale.animateTo(targetValue = 1f, animationSpec = getAnimateTween())
    }

    Surface(
        modifier = modifier
            .graphicsLayer(scaleX = scale.value, scaleY = scale.value)
            .clip(MaterialTheme.shapes.large)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        shape = MaterialTheme.shapes.large,
        color = itemColor(),
        contentColor = onItemColor()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AnimatedVisibility(
                visible = selectionMode,
                enter = fadeIn(tween(180)) + expandHorizontally(tween(220)),
                exit = fadeOut(tween(140)) + shrinkHorizontally(tween(180))
            ) {
                Checkbox(
                    checked = isChecked,
                    onCheckedChange = onCheckedChange,
                    modifier = Modifier.padding(end = 4.dp)
                )
            }

            Icon(
                painter = painterResource(getFileIcon(file)),
                contentDescription = null,
                modifier = Modifier.size(32.dp),
                tint = LocalContentColor.current
            )

            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = file.name,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = if (file.isDirectory) stringResource(R.string.file_manager_folder)
                    else formatBytesShort(file.length()),
                    style = MaterialTheme.typography.bodySmall
                )
            }

            AnimatedVisibility(
                visible = !selectionMode,
                enter = fadeIn(tween(180)),
                exit = fadeOut(tween(140))
            ) {
                Row {
                    IconButton(onClick = onProperties) {
                        Icon(
                            painter = painterResource(R.drawable.ic_info_outlined),
                            contentDescription = stringResource(R.string.file_manager_properties)
                        )
                    }
                    Box {
                        var menuExpanded by remember { mutableStateOf(false) }
                        IconButton(onClick = { menuExpanded = true }) {
                            Icon(
                                painter = painterResource(R.drawable.ic_more_vert),
                                contentDescription = stringResource(R.string.file_manager_more_options)
                            )
                        }
                        DropdownMenu(
                            expanded = menuExpanded,
                            onDismissRequest = { menuExpanded = false },
                            shape = MaterialTheme.shapes.large
                        ) {
                            DropdownMenuItem(
                                leadingIcon = {
                                    Icon(painterResource(R.drawable.ic_edit_outlined), null)
                                },
                                text = { Text(stringResource(R.string.file_manager_rename)) },
                                onClick = { onRename(); menuExpanded = false }
                            )
                            DropdownMenuItem(
                                leadingIcon = {
                                    Icon(painterResource(R.drawable.ic_copy_all_outlined), null)
                                },
                                text = { Text(stringResource(R.string.file_manager_copy)) },
                                onClick = { onCopy(); menuExpanded = false }
                            )
                            DropdownMenuItem(
                                leadingIcon = {
                                    Icon(painterResource(R.drawable.ic_file_copy_filled), null)
                                },
                                text = { Text(stringResource(R.string.file_manager_cut)) },
                                onClick = { onCut(); menuExpanded = false }
                            )
                            DropdownMenuItem(
                                leadingIcon = {
                                    Icon(painterResource(R.drawable.ic_delete_outlined), null)
                                },
                                text = { Text(stringResource(R.string.file_manager_delete)) },
                                onClick = { onDelete(); menuExpanded = false }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PropertiesDialog(files: List<File>, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                if (files.size == 1) stringResource(R.string.file_manager_properties)
                else stringResource(R.string.file_manager_properties_title_multi, files.size)
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (files.size == 1) {
                    val f = files.first()
                    PropertyRow(stringResource(R.string.file_manager_properties_item_name), f.name)
                    PropertyRow(stringResource(R.string.file_manager_properties_type), if (f.isDirectory) stringResource(R.string.file_manager_folder) else stringResource(R.string.file_manager_properties_file))
                    PropertyRow(stringResource(R.string.file_manager_properties_location), f.parentFile?.absolutePath ?: "\u2014")
                    PropertyRow(
                        stringResource(R.string.file_manager_properties_size),
                        if (f.isDirectory) stringResource(R.string.file_manager_folder) else formatBytesShort(f.length())
                    )
                    PropertyRow(stringResource(R.string.file_manager_properties_modified), formatFileDate(f.lastModified()))
                    PropertyRow(stringResource(R.string.file_manager_properties_readable), if (f.canRead()) stringResource(R.string.file_manager_properties_yes) else stringResource(R.string.file_manager_properties_no))
                    PropertyRow(stringResource(R.string.file_manager_properties_writable), if (f.canWrite()) stringResource(R.string.file_manager_properties_yes) else stringResource(R.string.file_manager_properties_no))
                    PropertyRow(stringResource(R.string.file_manager_properties_executable), if (f.canExecute()) stringResource(R.string.file_manager_properties_yes) else stringResource(R.string.file_manager_properties_no))
                } else {
                    val fileCount = files.count { !it.isDirectory }
                    val folderCount = files.count { it.isDirectory }
                    val totalSize = files.filter { !it.isDirectory }.sumOf { it.length() }
                    val parents = files.mapNotNull { it.parentFile?.absolutePath }.toSet()
                    val locationLabel = when {
                        parents.isEmpty() -> "\u2014"
                        parents.size == 1 -> parents.first()
                        else -> stringResource(R.string.file_manager_n_locations, parents.size)
                    }

                    if (fileCount > 0)
                        PropertyRow(stringResource(R.string.file_manager_properties_files), "$fileCount")
                    if (folderCount > 0)
                        PropertyRow(stringResource(R.string.file_manager_properties_folders), "$folderCount")
                    PropertyRow(stringResource(R.string.file_manager_properties_total_size), formatBytesShort(totalSize))
                    PropertyRow(stringResource(R.string.file_manager_properties_location), locationLabel)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.file_manager_properties_close)) }
        }
    )
}

@Composable
private fun PropertyRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "$label:",
            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
            modifier = Modifier.width(72.dp)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun formatBytesShort(bytes: Long): String = when {
    bytes < 1_024L -> "$bytes ${stringResource(R.string.file_manager_bytes)}"
    bytes < 1_024L * 1_024L -> "%.1f ${stringResource(R.string.file_manager_kb)}".format(bytes / 1_024.0)
    bytes < 1_024L * 1_024L * 1_024L -> "%.1f ${stringResource(R.string.file_manager_mb)}".format(bytes / (1_024.0 * 1_024.0))
    else -> "%.2f ${stringResource(R.string.file_manager_gb)}".format(bytes / (1_024.0 * 1_024.0 * 1_024.0))
}

private fun formatFileDate(millis: Long): String =
    SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(millis))

private fun buildClipboardLabel(files: List<File>, isCut: Boolean): String {
    if (files.isEmpty()) return ""
    val op = if (isCut) "cut" else "copied"
    val fileCount = files.count { !it.isDirectory }
    val folderCount = files.count { it.isDirectory }
    return when {
        fileCount > 0 && folderCount > 0 -> {
            val filePart = "$fileCount ${if (fileCount == 1) "file" else "files"}"
            val folderPart = "$folderCount ${if (folderCount == 1) "folder" else "folders"}"
            "$filePart and $folderPart $op"
        }
        fileCount > 0 ->
            "$fileCount ${if (fileCount == 1) "file" else "files"} $op"
        else ->
            "$folderCount ${if (folderCount == 1) "folder" else "folders"} $op"
    }
}

private fun getFileIcon(file: File): Int {
    if (file.isDirectory) {
        return when (file.name.lowercase()) {
            "mods" -> R.drawable.ic_extension_outlined
            "resourcepacks" -> R.drawable.ic_folder_zip_outlined
            "screenshots" -> R.drawable.ic_image_outlined
            else -> R.drawable.ic_folder_outlined
        }
    }
    return when (file.extension.lowercase()) {
        "jar" -> when (file.parentFile?.name?.lowercase()) {
            "mods" -> R.drawable.ic_extension_outlined
            "libraries", "plugins" -> R.drawable.ic_java
            else -> R.drawable.ic_java
        }
        "zip" -> R.drawable.ic_folder_zip_outlined
        "png", "jpg", "jpeg", "gif", "webp" -> R.drawable.ic_image_outlined
        "txt" -> R.drawable.ic_article_outlined
        "toml", "yml", "yaml", "cfg", "properties" -> R.drawable.ic_code
        "json" -> R.drawable.ic_code
        "java" -> R.drawable.ic_java
        "log" -> R.drawable.ic_terminal_outlined
        "dat" -> when (file.name.lowercase()) {
            "level.dat" -> R.drawable.ic_package_2_outlined
            else -> R.drawable.ic_description_outlined
        }
        else -> R.drawable.ic_description_outlined
    }
}
