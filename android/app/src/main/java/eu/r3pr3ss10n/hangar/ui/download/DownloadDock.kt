package eu.r3pr3ss10n.hangar.ui.download

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Error
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import eu.r3pr3ss10n.hangar.R
import eu.r3pr3ss10n.hangar.data.download.DownloadItem
import eu.r3pr3ss10n.hangar.data.download.DownloadStatus

/**
 * DownloadDock summarises the download queue in-app (no system notification):
 * the active item's progress, or a finished item with an Open action.
 */
@Composable
fun DownloadDock(
    items: List<DownloadItem>,
    onOpen: (DownloadItem) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val active = items.firstOrNull { it.status == DownloadStatus.DOWNLOADING }
    // The most recent finished item (completed or failed), shown when nothing is active.
    val finished = items.lastOrNull { it.status != DownloadStatus.DOWNLOADING }

    AnimatedVisibility(visible = items.isNotEmpty()) {
        Surface(
            tonalElevation = 3.dp,
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = modifier.fillMaxWidth(),
        ) {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val icon = when {
                        active != null -> Icons.Filled.Download
                        finished?.status == DownloadStatus.FAILED -> Icons.Filled.Error
                        else -> Icons.Filled.CheckCircle
                    }
                    Icon(icon, contentDescription = null)
                    Column(
                        Modifier
                            .weight(1f)
                            .padding(start = 12.dp),
                    ) {
                        val item = active ?: finished
                        Text(
                            text = item?.fileName ?: stringResource(R.string.download_downloads),
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = when {
                                active != null -> stringResource(R.string.download_downloading)
                                finished?.status == DownloadStatus.FAILED ->
                                    finished.error ?: stringResource(R.string.download_failed)
                                else -> stringResource(R.string.download_saved)
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (active == null && finished != null) {
                        if (finished.status == DownloadStatus.COMPLETED && finished.contentUri != null) {
                            TextButton(onClick = { onOpen(finished) }) { Text(stringResource(R.string.common_open)) }
                        }
                        IconButton(onClick = onDismiss) { Icon(Icons.Filled.Close, stringResource(R.string.download_dismiss)) }
                    }
                }
                if (active != null) {
                    if (active.total > 0) {
                        LinearProgressIndicator(
                            progress = { active.progress },
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        )
                    } else {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
                    }
                }
            }
        }
    }
}
