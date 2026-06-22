package eu.r3pr3ss10n.hangar.ui.tags

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Label
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import eu.r3pr3ss10n.hangar.R
import eu.r3pr3ss10n.hangar.domain.FileItem
import eu.r3pr3ss10n.hangar.domain.Folder
import eu.r3pr3ss10n.hangar.ui.components.ResourceListScaffold

@Composable
fun TagItemsScreen(
    title: String,
    onBack: () -> Unit,
    onOpenFolder: (Folder) -> Unit,
    onOpenFile: (FileItem) -> Unit,
    viewModel: TagItemsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    ResourceListScaffold(
        title = title,
        loading = state.loading,
        folders = state.folders,
        files = state.files,
        error = state.error,
        emptyIcon = Icons.AutoMirrored.Filled.Label,
        emptyTitle = stringResource(R.string.tag_items_empty_title),
        emptySubtitle = stringResource(R.string.tag_items_empty_subtitle),
        thumbUrl = viewModel::thumbUrl,
        onOpenFolder = onOpenFolder,
        onOpenFile = onOpenFile,
        onBack = onBack,
        onRefresh = viewModel::refresh,
        isRefreshing = state.refreshing,
    )
}
