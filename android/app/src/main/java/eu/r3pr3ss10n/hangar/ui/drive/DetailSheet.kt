package eu.r3pr3ss10n.hangar.ui.drive

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DriveFileMove
import androidx.compose.material.icons.automirrored.filled.Label
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.People
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import eu.r3pr3ss10n.hangar.R
import eu.r3pr3ss10n.hangar.domain.FileItem
import eu.r3pr3ss10n.hangar.domain.Folder
import eu.r3pr3ss10n.hangar.ui.util.fileIcon
import eu.r3pr3ss10n.hangar.ui.util.formatSize

/** Identifies the item a detail sheet is acting on. */
sealed interface SheetTarget {
    data class FileTarget(val file: FileItem) : SheetTarget
    data class FolderTarget(val folder: Folder) : SheetTarget
}

/**
 * The action sheet content for a file or folder. Mutations (rename/move/delete)
 * are owned by the drive screen; cross-cutting actions (download/share/tags) are
 * passed through as callbacks so other features wire them.
 */
@Composable
fun DetailSheetContent(
    target: SheetTarget,
    onRename: () -> Unit,
    onMove: () -> Unit,
    onDelete: () -> Unit,
    onManageTags: () -> Unit,
    onDownload: () -> Unit,
    onShareLink: () -> Unit,
    onShareUser: () -> Unit,
) {
    val isFolder = target is SheetTarget.FolderTarget
    val name = when (target) {
        is SheetTarget.FileTarget -> target.file.name
        is SheetTarget.FolderTarget -> target.folder.name
    }

    Column(modifier = Modifier.padding(bottom = 12.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            val icon = when (target) {
                is SheetTarget.FileTarget -> fileIcon(target.file.mime, target.file.name)
                is SheetTarget.FolderTarget -> Icons.AutoMirrored.Filled.DriveFileMove
            }
            Icon(icon, contentDescription = null, modifier = Modifier.size(36.dp))
            Column {
                Text(
                    name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (target is SheetTarget.FileTarget) {
                    Text(
                        formatSize(target.file.size),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        HorizontalDivider()

        if (target is SheetTarget.FileTarget) {
            SheetAction(Icons.Filled.Download, stringResource(R.string.common_download), onDownload)
            SheetAction(Icons.Filled.Link, stringResource(R.string.drive_share_link), onShareLink)
        }
        SheetAction(Icons.Filled.People, stringResource(R.string.drive_share_with_user), onShareUser)
        SheetAction(Icons.AutoMirrored.Filled.Label, stringResource(R.string.drive_tags), onManageTags)
        HorizontalDivider()
        SheetAction(Icons.Filled.Edit, stringResource(R.string.common_rename), onRename)
        SheetAction(Icons.AutoMirrored.Filled.DriveFileMove, stringResource(R.string.common_move), onMove)
        SheetAction(
            Icons.Filled.Delete,
            stringResource(R.string.common_delete),
            onDelete,
            tint = MaterialTheme.colorScheme.error,
        )
    }
}

@Composable
private fun SheetAction(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    tint: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(24.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge, color = tint)
    }
}
