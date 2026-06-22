package eu.r3pr3ss10n.hangar.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import eu.r3pr3ss10n.hangar.domain.Tag
import eu.r3pr3ss10n.hangar.ui.util.tagColorHex

/** A small colored dot for a tag, used in listings next to a name. */
@Composable
fun TagDot(color: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(8.dp)
            .clip(CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            color = Color(tagColorHex(color)),
            modifier = Modifier.size(8.dp),
            shape = CircleShape,
        ) {}
    }
}

/** A tinted pill badge showing a tag's name in its colour. */
@Composable
fun TagBadge(tag: Tag, modifier: Modifier = Modifier) {
    val c = Color(tagColorHex(tag.color))
    Surface(
        color = c.copy(alpha = 0.16f),
        shape = MaterialTheme.shapes.small,
        modifier = modifier,
    ) {
        Text(
            tag.name,
            style = MaterialTheme.typography.labelSmall,
            color = c,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
        )
    }
}
