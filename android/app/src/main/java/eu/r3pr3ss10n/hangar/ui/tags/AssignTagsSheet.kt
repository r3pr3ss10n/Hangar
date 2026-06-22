package eu.r3pr3ss10n.hangar.ui.tags

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Label
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import eu.r3pr3ss10n.hangar.R
import eu.r3pr3ss10n.hangar.ui.components.TagDot

/** Content of the tag-assignment bottom sheet for a file or folder. */
@Composable
fun AssignTagsSheet(
    itemId: String,
    isFolder: Boolean,
    name: String,
    viewModel: AssignTagsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(itemId, isFolder) { viewModel.load(itemId, isFolder) }

    Column(Modifier.padding(horizontal = 4.dp, vertical = 8.dp)) {
        Text(
            stringResource(R.string.tag_assign_title, name),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        )
        when {
            state.loading -> CircularProgressIndicator(Modifier.padding(16.dp))
            state.tags.isEmpty() -> Text(
                stringResource(R.string.tag_assign_none),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(16.dp),
            )

            else -> LazyColumn {
                items(state.tags, key = { it.id }) { tag ->
                    val checked = state.assigned.contains(tag.id)
                    ListItem(
                        modifier = Modifier.clickable { viewModel.toggle(tag.id) },
                        colors = eu.r3pr3ss10n.hangar.ui.components.transparentListItemColors(),
                        leadingContent = { TagDot(tag.color, Modifier.size(16.dp)) },
                        headlineContent = { Text(tag.name) },
                        trailingContent = {
                            Checkbox(checked = checked, onCheckedChange = { viewModel.toggle(tag.id) })
                        },
                    )
                }
            }
        }
    }
}
