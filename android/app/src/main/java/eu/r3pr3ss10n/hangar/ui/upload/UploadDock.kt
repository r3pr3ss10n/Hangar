package eu.r3pr3ss10n.hangar.ui.upload

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import eu.r3pr3ss10n.hangar.R
import eu.r3pr3ss10n.hangar.data.upload.UploadItem
import eu.r3pr3ss10n.hangar.data.upload.UploadStatus

/**
 * UploadDock is a compact banner over the bottom nav that summarises the upload
 * queue: the active item's progress, or a done/failed summary. Dismissing clears
 * finished entries.
 */
@Composable
fun UploadDock(
    items: List<UploadItem>,
    onDismiss: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val active = items.firstOrNull { it.status == UploadStatus.UPLOADING }
        ?: items.firstOrNull { it.status == UploadStatus.PENDING }
    val pending = items.count { it.status == UploadStatus.PENDING }
    val done = items.count { it.status == UploadStatus.COMPLETED }
    val failed = items.count { it.status == UploadStatus.FAILED }

    AnimatedVisibility(visible = items.isNotEmpty()) {
        Surface(
            tonalElevation = 3.dp,
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = modifier.fillMaxWidth(),
        ) {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val icon = when {
                        active != null -> Icons.Filled.UploadFile
                        failed > 0 -> Icons.Filled.Error
                        else -> Icons.Filled.CheckCircle
                    }
                    Icon(icon, contentDescription = null)
                    Column(
                        Modifier
                            .weight(1f)
                            .padding(start = 12.dp),
                    ) {
                        Text(
                            text = active?.name ?: summary(done, failed),
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (active != null) {
                            val remaining = if (pending > 0) {
                                pluralStringResource(R.plurals.upload_queued, pending, pending)
                            } else ""
                            Text(
                                stringResource(R.string.upload_uploading) + remaining,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    // While uploading, the button cancels; once finished, it
                    // clears the queue summary.
                    IconButton(onClick = { if (active != null) onCancel() else onDismiss() }) {
                        Icon(
                            Icons.Filled.Close,
                            stringResource(if (active != null) R.string.common_cancel else R.string.upload_dismiss),
                        )
                    }
                }
                if (active != null) {
                    if (active.size > 0) {
                        LinearProgressIndicator(
                            progress = { active.progress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp),
                        )
                    } else {
                        LinearProgressIndicator(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun summary(done: Int, failed: Int): String = buildString {
    if (done > 0) append(pluralStringResource(R.plurals.upload_done, done, done))
    if (failed > 0) {
        if (isNotEmpty()) append(", ")
        append(pluralStringResource(R.plurals.upload_failed, failed, failed))
    }
    if (isEmpty()) append(stringResource(R.string.upload_uploads))
}
