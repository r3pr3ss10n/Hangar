package eu.r3pr3ss10n.hangar.ui.sharing

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import eu.r3pr3ss10n.hangar.R

/** Content of the "share with users" bottom sheet for a file or folder. */
@Composable
fun GrantSheet(
    id: String,
    isFolder: Boolean,
    name: String,
    viewModel: GrantViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var showPicker by remember { mutableStateOf(false) }

    LaunchedEffect(id, isFolder) { viewModel.load(id, isFolder) }

    Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
        Text(stringResource(R.string.grant_sheet_title, name), style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(
            stringResource(R.string.grant_subtitle),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 2.dp, bottom = 8.dp),
        )

        if (state.loading) {
            CircularProgressIndicator(Modifier.padding(16.dp))
            return@Column
        }

        if (state.grants.isEmpty()) {
            Text(
                stringResource(R.string.grant_no_grants),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 8.dp),
            )
        } else {
            state.grants.forEach { grant ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(grant.recipientUsername, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                    Text(grant.permission, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    IconButton(onClick = { viewModel.revoke(grant.recipientId) }) {
                        Icon(Icons.Filled.Close, stringResource(R.string.grant_revoke), tint = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }

        HorizontalDivider(Modifier.padding(vertical = 8.dp))

        ListItem(
            modifier = Modifier.clickable(enabled = state.addable.isNotEmpty()) { showPicker = true },
            colors = eu.r3pr3ss10n.hangar.ui.components.transparentListItemColors(),
            leadingContent = { Icon(Icons.Filled.PersonAdd, null) },
            headlineContent = { Text(stringResource(R.string.grant_add_people)) },
            supportingContent = if (state.addable.isEmpty()) {
                { Text(stringResource(R.string.grant_no_users_available)) }
            } else null,
        )

        if (showPicker && state.addable.isNotEmpty()) {
            LazyColumn(Modifier.padding(top = 4.dp)) {
                items(state.addable, key = { it.id }) { user ->
                    ListItem(
                        modifier = Modifier.clickable {
                            viewModel.grant(user.id)
                            showPicker = false
                        },
                        colors = eu.r3pr3ss10n.hangar.ui.components.transparentListItemColors(),
                        headlineContent = { Text(user.username) },
                    )
                }
            }
        }
    }
}
