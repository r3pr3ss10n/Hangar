package eu.r3pr3ss10n.hangar.ui.components

import androidx.compose.material3.ListItemColors
import androidx.compose.material3.ListItemDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * ListItem colors with a transparent container, for use inside a ModalBottomSheet
 * (or any surface that already provides its own background). The default ListItem
 * container is `surface`, which paints a different shade over the sheet's
 * `surfaceContainerLow` in dark/Monet palettes — this keeps the sheet seamless.
 */
@Composable
fun transparentListItemColors(): ListItemColors =
    ListItemDefaults.colors(containerColor = Color.Transparent)
