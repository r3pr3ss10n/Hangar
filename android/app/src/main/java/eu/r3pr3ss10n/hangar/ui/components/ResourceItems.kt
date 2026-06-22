package eu.r3pr3ss10n.hangar.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import androidx.compose.ui.platform.LocalContext
import eu.r3pr3ss10n.hangar.R
import eu.r3pr3ss10n.hangar.domain.FileItem
import eu.r3pr3ss10n.hangar.domain.Folder
import eu.r3pr3ss10n.hangar.domain.Tag
import eu.r3pr3ss10n.hangar.ui.util.fileIcon
import eu.r3pr3ss10n.hangar.ui.util.formatSize

@Composable
fun FolderRow(
    folder: Folder,
    tags: List<Tag>,
    onClick: () -> Unit,
    onMenu: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    hovered: Boolean = false,
) {
    ListItem(
        modifier = modifier.clickable(onClick = onClick),
        colors = if (hovered) {
            androidx.compose.material3.ListItemDefaults.colors(
                containerColor = MaterialTheme.colorScheme.primaryContainer,
            )
        } else {
            androidx.compose.material3.ListItemDefaults.colors()
        },
        headlineContent = { Text(folder.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = when {
            subtitle != null -> {
                {
                    Column {
                        Text(subtitle, style = MaterialTheme.typography.bodySmall)
                        if (tags.isNotEmpty()) TagRow(tags)
                    }
                }
            }
            tags.isNotEmpty() -> {
                { TagRow(tags) }
            }
            else -> null
        },
        leadingContent = {
            Icon(
                Icons.Filled.Folder,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(40.dp),
            )
        },
        trailingContent = {
            IconButton(onClick = onMenu) { Icon(Icons.Filled.MoreVert, stringResource(R.string.common_extra_more)) }
        },
    )
}

@Composable
fun FileRow(
    file: FileItem,
    thumbUrl: String?,
    tags: List<Tag>,
    onClick: () -> Unit,
    onMenu: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    downloadProgress: Float? = null,
) {
    ListItem(
        modifier = modifier.clickable(onClick = onClick),
        headlineContent = { Text(file.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = {
            Column {
                Text(
                    if (downloadProgress != null) stringResource(R.string.common_extra_downloading) else (subtitle ?: formatSize(file.size)),
                    style = MaterialTheme.typography.bodySmall,
                )
                if (tags.isNotEmpty()) TagRow(tags)
            }
        },
        leadingContent = { FileLeading(file, thumbUrl, downloadProgress) },
        trailingContent = {
            IconButton(onClick = onMenu) { Icon(Icons.Filled.MoreVert, stringResource(R.string.common_extra_more)) }
        },
    )
}

@Composable
private fun FileLeading(file: FileItem, thumbUrl: String?, downloadProgress: Float? = null) {
    val shape = RoundedCornerShape(8.dp)
    when {
        downloadProgress != null -> {
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = shape,
                modifier = Modifier.size(40.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(
                        progress = { downloadProgress },
                        strokeWidth = 2.5.dp,
                        modifier = Modifier.size(34.dp),
                    )
                    Text(
                        "${(downloadProgress * 100).toInt()}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        file.hasThumb && thumbUrl != null -> {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(thumbUrl)
                    .crossfade(true)
                    .build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(40.dp)
                    .clip(shape),
            )
        }

        else -> {
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = shape,
                modifier = Modifier.size(40.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        fileIcon(file.mime, file.name),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(22.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun TagRow(tags: List<Tag>) {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        tags.take(3).forEach { TagBadge(it) }
    }
}

// ---- grid items ----

@Composable
fun FolderGridItem(
    folder: Folder,
    onClick: () -> Unit,
    onMenu: () -> Unit,
    modifier: Modifier = Modifier,
    hovered: Boolean = false,
) {
    Card(
        modifier = modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = if (hovered) {
            androidx.compose.material3.CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer,
            )
        } else {
            androidx.compose.material3.CardDefaults.cardColors()
        },
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Filled.Folder,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(28.dp),
            )
            Text(
                folder.name,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 8.dp),
            )
            IconButton(onClick = onMenu) { Icon(Icons.Filled.MoreVert, stringResource(R.string.common_extra_more)) }
        }
    }
}

@Composable
fun FileGridItem(
    file: FileItem,
    thumbUrl: String?,
    onClick: () -> Unit,
    onMenu: () -> Unit,
    modifier: Modifier = Modifier,
    downloadProgress: Float? = null,
) {
    Card(modifier = modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1.4f),
                contentAlignment = Alignment.Center,
            ) {
                when {
                    downloadProgress != null -> {
                        Surface(color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.fillMaxSize()) {
                            Box(contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(
                                    progress = { downloadProgress },
                                    strokeWidth = 3.dp,
                                    modifier = Modifier.size(48.dp),
                                )
                                Text(
                                    "${(downloadProgress * 100).toInt()}%",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }

                    file.hasThumb && thumbUrl != null -> {
                        AsyncImage(
                            model = ImageRequest.Builder(LocalContext.current)
                                .data(thumbUrl)
                                .crossfade(true)
                                .build(),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }

                    else -> {
                        Surface(color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.fillMaxSize()) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    fileIcon(file.mime, file.name),
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(36.dp),
                                )
                            }
                        }
                    }
                }
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    file.name,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onMenu) { Icon(Icons.Filled.MoreVert, stringResource(R.string.common_extra_more)) }
            }
        }
    }
}
