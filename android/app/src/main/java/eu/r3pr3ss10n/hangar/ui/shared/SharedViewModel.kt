package eu.r3pr3ss10n.hangar.ui.shared

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.r3pr3ss10n.hangar.R
import eu.r3pr3ss10n.hangar.data.repository.SharingRepository
import eu.r3pr3ss10n.hangar.domain.FileItem
import eu.r3pr3ss10n.hangar.domain.Folder
import eu.r3pr3ss10n.hangar.ui.util.UrlBuilder
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SharedUiState(
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val folders: List<Folder> = emptyList(),
    val files: List<FileItem> = emptyList(),
    val ownerByFolder: Map<String, String> = emptyMap(),
    val ownerByFile: Map<String, String> = emptyMap(),
    val error: String? = null,
) {
    val isEmpty get() = folders.isEmpty() && files.isEmpty()
}

/**
 * Backs both the "Shared with me" root and a shared sub-folder. When [folderId]
 * (the "id" nav arg) is present it lists that folder's children; otherwise it
 * lists the roots shared directly with the user (which also carry the owner).
 */
@HiltViewModel
class SharedViewModel @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val sharingRepository: SharingRepository,
    private val urlBuilder: UrlBuilder,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val folderId: String? = savedStateHandle.get<String>("id")

    private val _state = MutableStateFlow(SharedUiState())
    val state: StateFlow<SharedUiState> = _state.asStateFlow()

    init { refresh() }

    fun thumbUrl(fileId: String): String = urlBuilder.thumb(fileId)

    fun refresh() {
        viewModelScope.launch {
            _state.value = _state.value.copy(
                loading = _state.value.isEmpty,
                refreshing = true,
                error = null,
            )
            try {
                if (folderId == null) {
                    val roots = sharingRepository.sharedRoots()
                    _state.value = SharedUiState(
                        loading = false,
                        folders = roots.folders.map { it.folder },
                        files = roots.files.map { it.file },
                        ownerByFolder = roots.folders.associate { it.folder.id to it.ownerUsername },
                        ownerByFile = roots.files.associate { it.file.id to it.ownerUsername },
                    )
                } else {
                    val children = sharingRepository.sharedChildren(folderId)
                    _state.value = SharedUiState(
                        loading = false,
                        folders = children.folders,
                        files = children.files,
                    )
                }
            } catch (e: Exception) {
                _state.value = _state.value.copy(loading = false, refreshing = false, error = e.message ?: context.getString(R.string.shared_failed_to_load))
            }
        }
    }
}
