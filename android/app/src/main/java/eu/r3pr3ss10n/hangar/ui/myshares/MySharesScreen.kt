package eu.r3pr3ss10n.hangar.ui.myshares

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import eu.r3pr3ss10n.hangar.R
import eu.r3pr3ss10n.hangar.domain.FileItem
import eu.r3pr3ss10n.hangar.ui.components.EmptyState
import eu.r3pr3ss10n.hangar.ui.components.FileRow

/**
 * "My links" — the files the user has created public share links for, with
 * per-link copy / share / revoke. Tapping a row opens (or downloads) the file.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MySharesScreen(
    onBack: () -> Unit,
    onOpenFile: (FileItem) -> Unit,
    viewModel: MySharesViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var menuFor by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.my_links_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.common_extra_back))
                    }
                },
            )
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
                state.loading -> Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
                state.error != null -> EmptyState(
                    icon = Icons.Filled.Link,
                    title = stringResource(R.string.common_extra_couldnt_load),
                    subtitle = state.error,
                )
                state.isEmpty -> EmptyState(
                    icon = Icons.Filled.Link,
                    title = stringResource(R.string.my_links_empty_title),
                    subtitle = stringResource(R.string.my_links_empty_subtitle),
                )
                else -> LazyColumn(Modifier.fillMaxSize()) {
                    items(state.shares, key = { it.token }) { share ->
                        val url = viewModel.shareUrl(share.token)
                        Box {
                            FileRow(
                                file = share.file,
                                thumbUrl = if (share.file.hasThumb) viewModel.thumbUrl(share.file.id) else null,
                                tags = emptyList(),
                                onClick = { onOpenFile(share.file) },
                                onMenu = { menuFor = share.token },
                                subtitle = url,
                            )
                            DropdownMenu(
                                expanded = menuFor == share.token,
                                onDismissRequest = { menuFor = null },
                            ) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.share_copy)) },
                                    leadingIcon = { Icon(Icons.Filled.ContentCopy, null) },
                                    onClick = { menuFor = null; copyToClipboard(context, url) },
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.share_action)) },
                                    leadingIcon = { Icon(Icons.Filled.Share, null) },
                                    onClick = { menuFor = null; shareVia(context, url) },
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.share_revoke)) },
                                    leadingIcon = { Icon(Icons.Filled.Delete, null) },
                                    onClick = { menuFor = null; viewModel.revoke(share.token) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun copyToClipboard(context: Context, text: String) {
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    cm.setPrimaryClip(ClipData.newPlainText("Hangar share link", text))
}

private fun shareVia(context: Context, text: String) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
    }
    context.startActivity(Intent.createChooser(intent, context.getString(R.string.share_action)))
}
