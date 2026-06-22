package eu.r3pr3ss10n.hangar.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.FolderOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import eu.r3pr3ss10n.hangar.R
import eu.r3pr3ss10n.hangar.domain.FileItem
import eu.r3pr3ss10n.hangar.domain.Folder

/**
 * A read-only folder/file listing with a back-navigable top bar, used by the tag
 * items and shared-with-me screens. Tapping an item opens it; there is no detail
 * sheet here (those screens are browse-only). Pass [onRefresh] to enable
 * pull-to-refresh.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResourceListScaffold(
    title: String,
    loading: Boolean,
    folders: List<Folder>,
    files: List<FileItem>,
    error: String?,
    emptyIcon: ImageVector,
    emptyTitle: String,
    emptySubtitle: String?,
    thumbUrl: (String) -> String,
    onOpenFolder: (Folder) -> Unit,
    onOpenFile: (FileItem) -> Unit,
    onBack: (() -> Unit)? = null,
    folderSubtitle: (Folder) -> String? = { null },
    fileSubtitle: (FileItem) -> String? = { null },
    onRefresh: (() -> Unit)? = null,
    isRefreshing: Boolean = false,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    if (onBack != null) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.common_extra_back))
                        }
                    }
                },
            )
        },
    ) { padding ->
        val content: @Composable () -> Unit = {
            when {
                loading -> Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
                error != null -> EmptyState(icon = Icons.Filled.FolderOff, title = stringResource(R.string.common_extra_couldnt_load), subtitle = error)
                folders.isEmpty() && files.isEmpty() -> EmptyState(
                    icon = emptyIcon,
                    title = emptyTitle,
                    subtitle = emptySubtitle,
                )

                else -> LazyColumn(Modifier.fillMaxSize()) {
                    items(folders, key = { "folder-${it.id}" }) { folder ->
                        FolderRow(
                            folder = folder,
                            tags = emptyList(),
                            onClick = { onOpenFolder(folder) },
                            onMenu = { onOpenFolder(folder) },
                            subtitle = folderSubtitle(folder),
                        )
                    }
                    items(files, key = { "file-${it.id}" }) { file ->
                        FileRow(
                            file = file,
                            thumbUrl = if (file.hasThumb) thumbUrl(file.id) else null,
                            tags = emptyList(),
                            onClick = { onOpenFile(file) },
                            onMenu = { onOpenFile(file) },
                            subtitle = fileSubtitle(file),
                        )
                    }
                }
            }
        }

        if (onRefresh != null) {
            PullToRefreshBox(
                isRefreshing = isRefreshing,
                onRefresh = onRefresh,
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize(),
            ) { content() }
        } else {
            Box(
                Modifier
                    .padding(padding)
                    .fillMaxSize(),
            ) { content() }
        }
    }
}
