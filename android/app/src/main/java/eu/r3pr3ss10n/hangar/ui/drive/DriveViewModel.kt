package eu.r3pr3ss10n.hangar.ui.drive

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import eu.r3pr3ss10n.hangar.R
import eu.r3pr3ss10n.hangar.data.repository.ApiException
import eu.r3pr3ss10n.hangar.data.repository.DriveEvents
import eu.r3pr3ss10n.hangar.data.repository.DriveRepository
import eu.r3pr3ss10n.hangar.data.repository.NetworkException
import eu.r3pr3ss10n.hangar.data.repository.TagsRepository
import eu.r3pr3ss10n.hangar.data.upload.UploadManager
import eu.r3pr3ss10n.hangar.domain.FileItem
import eu.r3pr3ss10n.hangar.domain.Folder
import eu.r3pr3ss10n.hangar.domain.Labels
import eu.r3pr3ss10n.hangar.domain.Tag
import eu.r3pr3ss10n.hangar.ui.util.UrlBuilder
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DriveUiState(
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val folders: List<Folder> = emptyList(),
    val files: List<FileItem> = emptyList(),
    val labels: Labels? = null,
    val error: String? = null,
    val message: String? = null,
) {
    val isEmpty: Boolean get() = folders.isEmpty() && files.isEmpty()

    fun tagsFor(fileId: String, isFolder: Boolean): List<Tag> {
        val l = labels ?: return emptyList()
        val ids = (if (isFolder) l.folderTags else l.fileTags)[fileId].orEmpty()
        return ids.mapNotNull { id -> l.tags.find { it.id == id } }
    }
}

@HiltViewModel
class DriveViewModel @Inject constructor(
    @param:dagger.hilt.android.qualifiers.ApplicationContext private val context: Context,
    private val driveRepository: DriveRepository,
    private val tagsRepository: TagsRepository,
    private val urlBuilder: UrlBuilder,
    uploadManager: UploadManager,
    private val driveEvents: DriveEvents,
) : ViewModel() {

    private val _state = MutableStateFlow(DriveUiState())
    val state: StateFlow<DriveUiState> = _state.asStateFlow()

    private var folderId: String? = null
    private var loaded = false

    init {
        // Refresh this level when an upload completes into the folder it shows.
        viewModelScope.launch {
            uploadManager.completed.collect { completedFolderId ->
                if (loaded && completedFolderId == folderId) refresh()
            }
        }
        // Refresh on any app-wide drive change (e.g. a move from a sheet).
        viewModelScope.launch {
            driveEvents.changed.collect { if (loaded) refresh() }
        }
    }

    fun thumbUrl(fileId: String): String = urlBuilder.thumb(fileId)

    /** Loads the contents of [parentId] (null = root). Idempotent per id. */
    fun load(parentId: String?) {
        folderId = parentId
        loaded = true
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _state.value = _state.value.copy(
                loading = _state.value.isEmpty,
                refreshing = true,
                error = null,
            )
            try {
                val contents = driveRepository.list(folderId)
                val labels = runCatching { tagsRepository.labels() }.getOrNull()
                _state.value = _state.value.copy(
                    loading = false,
                    refreshing = false,
                    folders = contents.folders,
                    files = contents.files,
                    labels = labels,
                )
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    loading = false,
                    refreshing = false,
                    error = e.toMessage(),
                )
            }
        }
    }

    fun createFolder(name: String) {
        viewModelScope.launch {
            try {
                driveRepository.createFolder(name.trim(), folderId)
                refresh()
            } catch (e: Exception) {
                _state.value = _state.value.copy(message = e.toMessage())
            }
        }
    }

    fun renameFolder(id: String, name: String) = mutate { driveRepository.renameFolder(id, name.trim()) }
    fun renameFile(id: String, name: String) = mutate { driveRepository.renameFile(id, name.trim()) }
    fun deleteFolder(id: String) = mutate { driveRepository.deleteFolder(id) }
    fun deleteFile(id: String) = mutate { driveRepository.deleteFile(id) }
    fun moveFile(id: String, targetFolderId: String?) = mutate { driveRepository.moveFile(id, targetFolderId) }
    fun moveFolder(id: String, targetParentId: String?) = mutate { driveRepository.moveFolder(id, targetParentId) }

    /** Moves a dragged file/folder into [targetFolderId] (drag-and-drop). */
    fun moveInto(payload: eu.r3pr3ss10n.hangar.ui.components.DragPayload, targetFolderId: String) = mutate {
        if (payload.isFolder) driveRepository.moveFolder(payload.id, targetFolderId)
        else driveRepository.moveFile(payload.id, targetFolderId)
    }

    private fun mutate(block: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                block()
                refresh()
            } catch (e: Exception) {
                _state.value = _state.value.copy(message = e.toMessage())
            }
        }
    }

    fun consumeMessage() { _state.value = _state.value.copy(message = null) }

    private fun Exception.toMessage(): String = when (this) {
        is ApiException -> message ?: context.getString(R.string.drive_request_failed)
        is NetworkException -> context.getString(R.string.drive_network_error)
        else -> message ?: context.getString(R.string.drive_something_went_wrong)
    }
}
