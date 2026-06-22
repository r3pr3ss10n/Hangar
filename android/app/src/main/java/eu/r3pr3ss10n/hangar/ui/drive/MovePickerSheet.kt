package eu.r3pr3ss10n.hangar.ui.drive

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import eu.r3pr3ss10n.hangar.R
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/** A folder-tree picker for choosing a move destination. */
@Composable
fun MovePickerSheet(
    movingId: String,
    isFolder: Boolean,
    onPicked: () -> Unit,
    onDismiss: () -> Unit,
    viewModel: MovePickerViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(movingId, isFolder) { viewModel.start(movingId, isFolder) }

    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (state.parentStack.isNotEmpty()) {
                IconButton(onClick = viewModel::up) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.drive_move_up))
                }
            }
            Text(
                stringResource(R.string.drive_move_to, state.currentName),
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        Box(Modifier.heightIn(min = 120.dp, max = 360.dp).fillMaxWidth().padding(top = 8.dp)) {
            when {
                state.loading -> Box(Modifier.fillMaxWidth(), Alignment.Center) {
                    CircularProgressIndicator(Modifier.padding(24.dp))
                }

                state.folders.isEmpty() -> Text(
                    stringResource(R.string.drive_no_subfolders),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp),
                )

                else -> LazyColumn(Modifier.fillMaxWidth()) {
                    items(state.folders, key = { it.id }) { folder ->
                        ListItem(
                            modifier = Modifier.clickable { viewModel.open(folder.id, folder.name) },
                            colors = eu.r3pr3ss10n.hangar.ui.components.transparentListItemColors(),
                            leadingContent = { Icon(Icons.Filled.Folder, null, tint = MaterialTheme.colorScheme.primary) },
                            headlineContent = { Text(folder.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        )
                    }
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp, bottom = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
        ) {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) }
            Button(
                onClick = { viewModel.moveHere(onPicked) },
                enabled = !state.moving,
            ) { Text(stringResource(R.string.drive_move_here)) }
        }
    }
}
