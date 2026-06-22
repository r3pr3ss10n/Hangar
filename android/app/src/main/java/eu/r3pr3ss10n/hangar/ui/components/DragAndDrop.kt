package eu.r3pr3ss10n.hangar.ui.components

import android.content.ClipData
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.draganddrop.DragAndDropSourceScope
import androidx.compose.foundation.draganddrop.dragAndDropSource
import androidx.compose.foundation.draganddrop.dragAndDropTarget
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draganddrop.DragAndDropEvent
import androidx.compose.ui.draganddrop.DragAndDropTarget
import androidx.compose.ui.draganddrop.DragAndDropTransferData
import androidx.compose.ui.draganddrop.toAndroidDragEvent
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer

/** What is being dragged: a file or folder, by id. */
data class DragPayload(val isFolder: Boolean, val id: String)

/**
 * Marks a composable as a drag source for an internal file/folder move. The drag
 * begins only after a long-press *followed by movement*, so a normal tap still
 * opens the item and a plain swipe still scrolls the list. The drag shadow is a
 * snapshot of the item itself (recorded into a graphics layer), so you drag the
 * actual tile, not a placeholder. The payload travels as the drag's localState,
 * never leaving the app.
 */
@OptIn(ExperimentalFoundationApi::class)
fun Modifier.hangarDragSource(payload: DragPayload): Modifier = composed {
    // Record the item's own drawing so it can be replayed as the drag shadow.
    val layer = rememberGraphicsLayer()
    // Explicitly typed so overload resolution picks the gesture-block overload
    // (the long-press-then-drag detector) rather than the bare long-press one,
    // which proved unreliable inside the scrollable list.
    val startDrag: suspend DragAndDropSourceScope.() -> Unit = {
        detectDragGesturesAfterLongPress(
            onDragStart = {
                startTransfer(
                    DragAndDropTransferData(
                        clipData = ClipData.newPlainText("hangar-item", payload.id),
                        localState = payload,
                    ),
                )
            },
            onDrag = { _, _ -> },
        )
    }
    this
        .drawWithContent {
            layer.record { this@drawWithContent.drawContent() }
            drawLayer(layer)
        }
        .dragAndDropSource(
            // Draw the dragged tile a bit smaller than the real item so it's
            // easier to aim precisely at a folder.
            { scale(0.8f) { drawLayer(layer) } },
            startDrag,
        )
}

/**
 * Makes a folder a drop target for an internal move. [onDropItem] receives the
 * dragged payload; the folder rejects a drop of itself. [highlighted] reflects
 * whether a draggable item is currently hovering, for visual feedback.
 */
fun Modifier.hangarFolderDropTarget(
    folderId: String,
    onDropItem: (DragPayload) -> Unit,
    onHoverChange: (Boolean) -> Unit,
): Modifier = composedDropTarget(folderId, onDropItem, onHoverChange)

private fun Modifier.composedDropTarget(
    folderId: String,
    onDropItem: (DragPayload) -> Unit,
    onHoverChange: (Boolean) -> Unit,
): Modifier = this.then(
    Modifier.dragAndDropTarget(
        shouldStartDragAndDrop = { event ->
            // Accept only our internal payload, never OS file drags.
            (event.toAndroidDragEvent().localState as? DragPayload) != null
        },
        target = folderDropTarget(folderId, onDropItem, onHoverChange),
    ),
)

private fun folderDropTarget(
    folderId: String,
    onDropItem: (DragPayload) -> Unit,
    onHoverChange: (Boolean) -> Unit,
): DragAndDropTarget = object : DragAndDropTarget {
    override fun onEntered(event: DragAndDropEvent) {
        if (payloadOf(event)?.let { canDrop(it, folderId) } == true) onHoverChange(true)
    }

    override fun onExited(event: DragAndDropEvent) = onHoverChange(false)

    override fun onEnded(event: DragAndDropEvent) = onHoverChange(false)

    override fun onDrop(event: DragAndDropEvent): Boolean {
        onHoverChange(false)
        val payload = payloadOf(event) ?: return false
        if (!canDrop(payload, folderId)) return false
        onDropItem(payload)
        return true
    }
}

private fun payloadOf(event: DragAndDropEvent): DragPayload? =
    event.toAndroidDragEvent().localState as? DragPayload

/** A folder cannot be dropped onto itself. */
private fun canDrop(payload: DragPayload, folderId: String): Boolean =
    !(payload.isFolder && payload.id == folderId)
