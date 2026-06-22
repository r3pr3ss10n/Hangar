package eu.r3pr3ss10n.hangar.ui.upload

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.r3pr3ss10n.hangar.data.upload.UploadItem
import eu.r3pr3ss10n.hangar.data.upload.UploadManager
import eu.r3pr3ss10n.hangar.data.upload.UploadService
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/** Bridges the upload queue to the UI and starts the foreground service. */
@HiltViewModel
class UploadViewModel @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val uploadManager: UploadManager,
) : ViewModel() {

    val items: StateFlow<List<UploadItem>> = uploadManager.items.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList(),
    )

    /** Flow of completed-upload destination folder ids (null = root). */
    val completed = uploadManager.completed

    fun upload(uris: List<Uri>, folderId: String?) {
        if (uris.isEmpty()) return
        uploadManager.enqueue(uris, folderId)
        UploadService.start(context)
    }

    fun clearFinished() = uploadManager.clearFinished()

    /** Aborts the active upload and clears anything still queued. */
    fun cancel() = uploadManager.cancelAll()
}
