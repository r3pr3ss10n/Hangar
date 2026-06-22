package eu.r3pr3ss10n.hangar.ui.search

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import eu.r3pr3ss10n.hangar.R
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.LaunchedEffect
import eu.r3pr3ss10n.hangar.domain.FileItem
import eu.r3pr3ss10n.hangar.domain.Folder
import eu.r3pr3ss10n.hangar.domain.PathSegment
import eu.r3pr3ss10n.hangar.ui.components.EmptyState
import eu.r3pr3ss10n.hangar.ui.components.FileRow
import eu.r3pr3ss10n.hangar.ui.components.FolderRow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    onBack: () -> Unit,
    onOpenFolder: (Folder) -> Unit,
    onOpenFile: (FileItem) -> Unit,
    viewModel: SearchViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current

    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.search_back))
                    }
                },
                title = {
                    TextField(
                        value = state.query,
                        onValueChange = viewModel::onQueryChange,
                        placeholder = { Text(stringResource(R.string.search_placeholder)) },
                        singleLine = true,
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = androidx.compose.ui.graphics.Color.Transparent,
                            unfocusedContainerColor = androidx.compose.ui.graphics.Color.Transparent,
                            focusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                            unfocusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                        ),
                        trailingIcon = {
                            if (state.query.isNotEmpty()) {
                                IconButton(onClick = { viewModel.onQueryChange("") }) {
                                    Icon(Icons.Filled.Clear, stringResource(R.string.search_clear))
                                }
                            }
                        },
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { keyboard?.hide() }),
                        modifier = Modifier.focusRequester(focusRequester),
                    )
                },
            )
        },
    ) { padding ->
        Box(
            Modifier
                .padding(padding)
                .fillMaxSize(),
        ) {
            when {
                state.loading -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                    CircularProgressIndicator()
                }

                !state.searched -> EmptyState(
                    icon = Icons.Filled.Search,
                    title = stringResource(R.string.search_empty_title),
                    subtitle = stringResource(R.string.search_empty_subtitle),
                )

                state.folders.isEmpty() && state.files.isEmpty() -> EmptyState(
                    icon = Icons.Filled.Search,
                    title = stringResource(R.string.search_no_results_title),
                    subtitle = stringResource(R.string.search_no_results_subtitle, state.query),
                )

                else -> LazyColumn(Modifier.fillMaxSize()) {
                    items(state.folders, key = { "f-${it.folder.id}" }) { hit ->
                        FolderRow(
                            folder = hit.folder,
                            tags = emptyList(),
                            onClick = { onOpenFolder(hit.folder) },
                            onMenu = { onOpenFolder(hit.folder) },
                        )
                        PathLine(hit.path)
                    }
                    items(state.files, key = { "fi-${it.file.id}" }) { hit ->
                        FileRow(
                            file = hit.file,
                            thumbUrl = if (hit.file.hasThumb) viewModel.thumbUrl(hit.file.id) else null,
                            tags = emptyList(),
                            onClick = { onOpenFile(hit.file) },
                            onMenu = { onOpenFile(hit.file) },
                        )
                        PathLine(hit.path)
                    }
                }
            }
        }
    }
}

@Composable
private fun PathLine(path: List<PathSegment>) {
    if (path.isEmpty()) return
    Text(
        text = stringResource(R.string.search_path_in, path.joinToString(" / ") { it.name }),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 72.dp, bottom = 6.dp, end = 16.dp),
    )
}
