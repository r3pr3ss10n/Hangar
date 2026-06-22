package eu.r3pr3ss10n.hangar.ui.drive

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import eu.r3pr3ss10n.hangar.R
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import eu.r3pr3ss10n.hangar.domain.FileItem
import eu.r3pr3ss10n.hangar.domain.Folder
import eu.r3pr3ss10n.hangar.ui.components.DragPayload
import eu.r3pr3ss10n.hangar.ui.components.EmptyState
import eu.r3pr3ss10n.hangar.ui.components.FileGridItem
import eu.r3pr3ss10n.hangar.ui.components.FileRow
import eu.r3pr3ss10n.hangar.ui.components.FolderGridItem
import eu.r3pr3ss10n.hangar.ui.components.FolderRow
import eu.r3pr3ss10n.hangar.ui.components.hangarDragSource
import eu.r3pr3ss10n.hangar.ui.components.hangarFolderDropTarget
import kotlinx.coroutines.launch

/**
 * DriveScreen renders one folder level: a top bar (with search + view toggle),
 * the folder/file listing in list or grid form, a FAB for upload / new folder,
 * and a detail action sheet. Navigation and cross-cutting actions are passed in.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DriveScreen(
    title: String,
    parentId: String?,
    isRoot: Boolean,
    gridView: Boolean,
    onToggleView: () -> Unit,
    onOpenFolder: (Folder) -> Unit,
    onOpenFile: (FileItem) -> Unit,
    onSearch: () -> Unit,
    onBack: (() -> Unit)?,
    onUpload: (parentId: String?) -> Unit,
    onManageTags: (SheetTarget) -> Unit,
    onShareLink: (FileItem) -> Unit,
    onShareUser: (SheetTarget) -> Unit,
    onDownload: (FileItem) -> Unit,
    onMove: (SheetTarget) -> Unit,
    downloadProgress: Map<String, Float> = emptyMap(),
    viewModel: DriveViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    LaunchedEffect(parentId) { viewModel.load(parentId) }

    LaunchedEffect(state.message) {
        state.message?.let {
            snackbar.showSnackbar(it)
            viewModel.consumeMessage()
        }
    }

    var sheetTarget by remember { mutableStateOf<SheetTarget?>(null) }
    var showCreateFolder by remember { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf<SheetTarget?>(null) }
    var deleteTarget by remember { mutableStateOf<SheetTarget?>(null) }
    var showFabMenu by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    if (onBack != null) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.drive_back))
                        }
                    }
                },
                actions = {
                    IconButton(onClick = onSearch) { Icon(Icons.Filled.Search, stringResource(R.string.drive_search)) }
                    IconButton(onClick = onToggleView) {
                        Icon(
                            if (gridView) Icons.AutoMirrored.Filled.ViewList else Icons.Filled.GridView,
                            contentDescription = if (gridView) stringResource(R.string.drive_list_view) else stringResource(R.string.drive_grid_view),
                        )
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showFabMenu = true },
                icon = { Icon(Icons.Filled.Add, null) },
                text = { Text(stringResource(R.string.drive_new)) },
            )
        },
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = state.refreshing,
            onRefresh = viewModel::refresh,
            modifier = Modifier.padding(padding).fillMaxSize(),
        ) {
            when {
                state.loading -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                    CircularProgressIndicator()
                }

                state.error != null -> EmptyState(
                    icon = Icons.Filled.FolderOpen,
                    title = stringResource(R.string.drive_load_error_title),
                    subtitle = state.error,
                    actionLabel = stringResource(R.string.drive_retry),
                    onAction = viewModel::refresh,
                )

                state.isEmpty -> EmptyState(
                    icon = Icons.Filled.FolderOpen,
                    title = stringResource(R.string.drive_empty_title),
                    subtitle = stringResource(R.string.drive_empty_subtitle),
                )

                gridView -> DriveGrid(
                    state = state,
                    thumbUrl = viewModel::thumbUrl,
                    downloadProgress = downloadProgress,
                    onOpenFolder = onOpenFolder,
                    onOpenFile = { if (it.isImage) onOpenFile(it) else sheetTarget = SheetTarget.FileTarget(it) },
                    onFolderMenu = { sheetTarget = SheetTarget.FolderTarget(it) },
                    onFileMenu = { sheetTarget = SheetTarget.FileTarget(it) },
                    onMoveItem = { payload, folderId -> viewModel.moveInto(payload, folderId) },
                )

                else -> DriveList(
                    state = state,
                    thumbUrl = viewModel::thumbUrl,
                    downloadProgress = downloadProgress,
                    onOpenFolder = onOpenFolder,
                    onOpenFile = { if (it.isImage) onOpenFile(it) else sheetTarget = SheetTarget.FileTarget(it) },
                    onFolderMenu = { sheetTarget = SheetTarget.FolderTarget(it) },
                    onFileMenu = { sheetTarget = SheetTarget.FileTarget(it) },
                    onMoveItem = { payload, folderId -> viewModel.moveInto(payload, folderId) },
                )
            }
        }
    }

    // ---- detail action sheet ----
    sheetTarget?.let { target ->
        ModalBottomSheet(
            onDismissRequest = { sheetTarget = null },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        ) {
            DetailSheetContent(
                target = target,
                onRename = { renameTarget = target; sheetTarget = null },
                onMove = { onMove(target); sheetTarget = null },
                onDelete = { deleteTarget = target; sheetTarget = null },
                onManageTags = { onManageTags(target); sheetTarget = null },
                onDownload = {
                    (target as? SheetTarget.FileTarget)?.let { onDownload(it.file) }
                    sheetTarget = null
                },
                onShareLink = {
                    (target as? SheetTarget.FileTarget)?.let { onShareLink(it.file) }
                    sheetTarget = null
                },
                onShareUser = { onShareUser(target); sheetTarget = null },
            )
        }
    }

    // ---- FAB action sheet ----
    if (showFabMenu) {
        ModalBottomSheet(onDismissRequest = { showFabMenu = false }) {
            Column(Modifier.padding(bottom = 12.dp)) {
                FabAction(Icons.Filled.UploadFile, stringResource(R.string.drive_upload_files)) {
                    showFabMenu = false
                    onUpload(parentId)
                }
                FabAction(Icons.Filled.CreateNewFolder, stringResource(R.string.drive_new_folder)) {
                    showFabMenu = false
                    showCreateFolder = true
                }
            }
        }
    }

    // ---- dialogs ----
    if (showCreateFolder) {
        TextInputDialog(
            title = stringResource(R.string.drive_new_folder),
            label = stringResource(R.string.drive_folder_name),
            confirmLabel = stringResource(R.string.common_create),
            onConfirm = { viewModel.createFolder(it); showCreateFolder = false },
            onDismiss = { showCreateFolder = false },
        )
    }

    renameTarget?.let { target ->
        val current = when (target) {
            is SheetTarget.FileTarget -> target.file.name
            is SheetTarget.FolderTarget -> target.folder.name
        }
        TextInputDialog(
            title = stringResource(R.string.common_rename),
            label = stringResource(R.string.common_name),
            initial = current,
            confirmLabel = stringResource(R.string.common_save),
            onConfirm = { newName ->
                when (target) {
                    is SheetTarget.FileTarget -> viewModel.renameFile(target.file.id, newName)
                    is SheetTarget.FolderTarget -> viewModel.renameFolder(target.folder.id, newName)
                }
                renameTarget = null
            },
            onDismiss = { renameTarget = null },
        )
    }

    deleteTarget?.let { target ->
        val name = when (target) {
            is SheetTarget.FileTarget -> target.file.name
            is SheetTarget.FolderTarget -> target.folder.name
        }
        ConfirmDialog(
            title = stringResource(R.string.drive_delete_title),
            message = stringResource(R.string.drive_delete_message, name),
            confirmLabel = stringResource(R.string.common_delete),
            destructive = true,
            onConfirm = {
                when (target) {
                    is SheetTarget.FileTarget -> viewModel.deleteFile(target.file.id)
                    is SheetTarget.FolderTarget -> viewModel.deleteFolder(target.folder.id)
                }
                deleteTarget = null
            },
            onDismiss = { deleteTarget = null },
        )
    }
}

@Composable
private fun DriveList(
    state: DriveUiState,
    thumbUrl: (String) -> String,
    downloadProgress: Map<String, Float>,
    onOpenFolder: (Folder) -> Unit,
    onOpenFile: (FileItem) -> Unit,
    onFolderMenu: (Folder) -> Unit,
    onFileMenu: (FileItem) -> Unit,
    onMoveItem: (eu.r3pr3ss10n.hangar.ui.components.DragPayload, String) -> Unit,
) {
    LazyColumn(Modifier.fillMaxSize()) {
        items(state.folders, key = { "folder-${it.id}" }) { folder ->
            var hovered by remember(folder.id) { mutableStateOf(false) }
            FolderRow(
                folder = folder,
                tags = state.tagsFor(folder.id, isFolder = true),
                onClick = { onOpenFolder(folder) },
                onMenu = { onFolderMenu(folder) },
                hovered = hovered,
                modifier = Modifier
                    .hangarDragSource(DragPayload(isFolder = true, id = folder.id))
                    .hangarFolderDropTarget(
                        folderId = folder.id,
                        onDropItem = { onMoveItem(it, folder.id) },
                        onHoverChange = { hovered = it },
                    ),
            )
        }
        items(state.files, key = { "file-${it.id}" }) { file ->
            FileRow(
                file = file,
                thumbUrl = if (file.hasThumb) thumbUrl(file.id) else null,
                tags = state.tagsFor(file.id, isFolder = false),
                downloadProgress = downloadProgress[file.id],
                onClick = { onOpenFile(file) },
                onMenu = { onFileMenu(file) },
                modifier = Modifier.hangarDragSource(DragPayload(isFolder = false, id = file.id)),
            )
        }
    }
}

@Composable
private fun DriveGrid(
    state: DriveUiState,
    thumbUrl: (String) -> String,
    downloadProgress: Map<String, Float>,
    onOpenFolder: (Folder) -> Unit,
    onOpenFile: (FileItem) -> Unit,
    onFolderMenu: (Folder) -> Unit,
    onFileMenu: (FileItem) -> Unit,
    onMoveItem: (eu.r3pr3ss10n.hangar.ui.components.DragPayload, String) -> Unit,
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 160.dp),
        modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(12.dp),
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(12.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 12.dp),
    ) {
        items(state.folders, key = { "folder-${it.id}" }) { folder ->
            var hovered by remember(folder.id) { mutableStateOf(false) }
            FolderGridItem(
                folder = folder,
                onClick = { onOpenFolder(folder) },
                onMenu = { onFolderMenu(folder) },
                hovered = hovered,
                modifier = Modifier
                    .hangarDragSource(DragPayload(isFolder = true, id = folder.id))
                    .hangarFolderDropTarget(
                        folderId = folder.id,
                        onDropItem = { onMoveItem(it, folder.id) },
                        onHoverChange = { hovered = it },
                    ),
            )
        }
        items(state.files, key = { "file-${it.id}" }) { file ->
            FileGridItem(
                file = file,
                thumbUrl = if (file.hasThumb) thumbUrl(file.id) else null,
                downloadProgress = downloadProgress[file.id],
                onClick = { onOpenFile(file) },
                onMenu = { onFileMenu(file) },
                modifier = Modifier.hangarDragSource(DragPayload(isFolder = false, id = file.id)),
            )
        }
    }
}

@Composable
private fun FabAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    androidx.compose.material3.ListItem(
        modifier = Modifier.clickable(onClick = onClick),
        colors = androidx.compose.material3.ListItemDefaults.colors(
            containerColor = androidx.compose.ui.graphics.Color.Transparent,
        ),
        headlineContent = { Text(label) },
        leadingContent = { Icon(icon, contentDescription = null) },
    )
}
