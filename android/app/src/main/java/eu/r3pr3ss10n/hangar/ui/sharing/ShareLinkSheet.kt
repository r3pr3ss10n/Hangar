package eu.r3pr3ss10n.hangar.ui.sharing

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import eu.r3pr3ss10n.hangar.R
import eu.r3pr3ss10n.hangar.domain.Share

/** Content of the share-links bottom sheet for a file. */
@Composable
fun ShareLinkSheet(
    fileId: String,
    fileName: String,
    viewModel: ShareLinkViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(fileId) { viewModel.load(fileId) }

    Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
        Text(stringResource(R.string.share_sheet_title, fileName), style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(
            stringResource(R.string.share_subtitle),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 2.dp, bottom = 12.dp),
        )

        when {
            state.loading -> CircularProgressIndicator(Modifier.padding(16.dp))
            else -> {
                if (state.shares.isEmpty()) {
                    Text(
                        stringResource(R.string.share_no_links),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 8.dp),
                    )
                } else {
                    LazyColumn {
                        items(state.shares, key = { it.token }) { share ->
                            ShareRow(
                                url = viewModel.shareUrl(share.token),
                                share = share,
                                onCopy = { copyToClipboard(context, it) },
                                onShare = { shareVia(context, it) },
                                onRevoke = { viewModel.revoke(share.token) },
                            )
                            HorizontalDivider()
                        }
                    }
                }
                Button(
                    onClick = { viewModel.createLink(null) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp, bottom = 8.dp),
                ) {
                    Icon(Icons.Filled.Share, null, Modifier.padding(end = 8.dp))
                    Text(stringResource(R.string.share_create))
                }
            }
        }
    }
}

@Composable
private fun ShareRow(
    url: String,
    share: Share,
    onCopy: (String) -> Unit,
    onShare: (String) -> Unit,
    onRevoke: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(url, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                share.expiresAt?.let { stringResource(R.string.share_expires_on, it.substringBefore('T')) }
                    ?: stringResource(R.string.share_expires_never),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = { onCopy(url) }) { Icon(Icons.Filled.ContentCopy, stringResource(R.string.share_copy)) }
        IconButton(onClick = { onShare(url) }) { Icon(Icons.Filled.Share, stringResource(R.string.share_action)) }
        IconButton(onClick = onRevoke) {
            Icon(Icons.Filled.Delete, stringResource(R.string.share_revoke), tint = MaterialTheme.colorScheme.error)
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
