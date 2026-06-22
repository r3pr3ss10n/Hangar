package eu.r3pr3ss10n.hangar.ui.tags

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Label
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import eu.r3pr3ss10n.hangar.R
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import eu.r3pr3ss10n.hangar.domain.Tag
import eu.r3pr3ss10n.hangar.ui.components.EmptyState
import eu.r3pr3ss10n.hangar.ui.components.TagDot
import eu.r3pr3ss10n.hangar.ui.drive.ConfirmDialog

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TagsScreen(
    onOpenTag: (Tag) -> Unit,
    viewModel: TagsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var showCreate by remember { mutableStateOf(false) }
    var editTarget by remember { mutableStateOf<Tag?>(null) }
    var deleteTarget by remember { mutableStateOf<Tag?>(null) }

    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.tag_title)) }) },
        floatingActionButton = {
            FloatingActionButton(onClick = { showCreate = true }) {
                Icon(Icons.Filled.Add, stringResource(R.string.tag_new_tag))
            }
        },
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = state.refreshing,
            onRefresh = viewModel::refresh,
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
        ) {
            when {
                state.loading -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                    CircularProgressIndicator()
                }

                state.tags.isEmpty() -> EmptyState(
                    icon = Icons.AutoMirrored.Filled.Label,
                    title = stringResource(R.string.tag_empty_no_tags),
                    subtitle = stringResource(R.string.tag_empty_subtitle),
                )

                else -> LazyColumn(Modifier.fillMaxSize()) {
                    items(state.tags, key = { it.id }) { tag ->
                        TagListRow(
                            tag = tag,
                            onClick = { onOpenTag(tag) },
                            onEdit = { editTarget = tag },
                            onDelete = { deleteTarget = tag },
                        )
                    }
                }
            }
        }
    }

    if (showCreate) {
        TagEditorDialog(
            title = stringResource(R.string.tag_new_tag),
            confirmLabel = stringResource(R.string.common_create),
            onConfirm = { name, color -> viewModel.create(name, color); showCreate = false },
            onDismiss = { showCreate = false },
        )
    }

    editTarget?.let { tag ->
        TagEditorDialog(
            title = stringResource(R.string.tag_edit_tag),
            initialName = tag.name,
            initialColor = tag.color,
            confirmLabel = stringResource(R.string.common_save),
            onConfirm = { name, color -> viewModel.update(tag.id, name, color); editTarget = null },
            onDismiss = { editTarget = null },
        )
    }

    deleteTarget?.let { tag ->
        ConfirmDialog(
            title = stringResource(R.string.tag_delete_confirm_title),
            message = stringResource(R.string.tag_delete_confirm_message, tag.name),
            confirmLabel = stringResource(R.string.common_delete),
            destructive = true,
            onConfirm = { viewModel.delete(tag.id); deleteTarget = null },
            onDismiss = { deleteTarget = null },
        )
    }
}

@Composable
private fun TagListRow(
    tag: Tag,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    ListItem(
        modifier = Modifier.clickable(onClick = onClick),
        leadingContent = { TagDot(tag.color, Modifier.size(16.dp)) },
        headlineContent = { Text(tag.name) },
        supportingContent = {
            Text(
                pluralStringResource(
                    R.plurals.tag_item_count,
                    tag.itemCount.toInt(),
                    tag.itemCount,
                ),
                style = MaterialTheme.typography.bodySmall,
            )
        },
        trailingContent = {
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, stringResource(R.string.tag_more)) }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.tag_edit)) },
                        leadingIcon = { Icon(Icons.Filled.Edit, null) },
                        onClick = { menu = false; onEdit() },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.common_delete)) },
                        leadingIcon = { Icon(Icons.Filled.Delete, null) },
                        onClick = { menu = false; onDelete() },
                    )
                }
            }
        },
    )
}
