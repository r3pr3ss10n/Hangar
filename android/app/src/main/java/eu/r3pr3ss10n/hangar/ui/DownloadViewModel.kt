package eu.r3pr3ss10n.hangar.ui

import android.content.Context
import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.r3pr3ss10n.hangar.data.download.DownloadItem
import eu.r3pr3ss10n.hangar.data.download.Downloader
import eu.r3pr3ss10n.hangar.domain.FileItem
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/** Bridges the downloader queue to the UI and opens finished files. */
@HiltViewModel
class DownloadViewModel @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val downloader: Downloader,
) : ViewModel() {

    val items: StateFlow<List<DownloadItem>> = downloader.items.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList(),
    )

    val completed = downloader.completed

    fun download(file: FileItem) = downloader.enqueue(file.id, file.name, file.mime)

    fun clearFinished() = downloader.clearFinished()

    /**
     * Opens a finished download in an external viewer via its MediaStore Uri.
     * Returns false when no app can handle the type.
     */
    fun open(item: DownloadItem): Boolean {
        val uri = item.contentUri ?: return false
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, item.mime.ifBlank { "*/*" })
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return try {
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            false
        }
    }
}
