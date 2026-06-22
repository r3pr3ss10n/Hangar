package eu.r3pr3ss10n.hangar.ui.shared

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.People
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
fun SharedScreen(
    title: String,
    isRoot: Boolean,
    onBack: (() -> Unit)?,
    onOpenFolder: (Folder) -> Unit,
    onOpenFile: (FileItem) -> Unit,
    viewModel: SharedViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val sharedByLabel = stringResource(R.string.shared_shared_by)
    ResourceListScaffold(
        title = title,
        loading = state.loading,
        folders = state.folders,
        files = state.files,
        error = state.error,
        emptyIcon = Icons.Filled.People,
        emptyTitle = stringResource(R.string.shared_empty_title),
        emptySubtitle = stringResource(R.string.shared_empty_subtitle),
        thumbUrl = viewModel::thumbUrl,
        onOpenFolder = onOpenFolder,
        onOpenFile = onOpenFile,
        onBack = onBack,
        folderSubtitle = { f -> state.ownerByFolder[f.id]?.let { sharedByLabel.format(it) } },
        fileSubtitle = { f -> state.ownerByFile[f.id]?.let { sharedByLabel.format(it) } },
        onRefresh = viewModel::refresh,
        isRefreshing = state.refreshing,
    )
}
