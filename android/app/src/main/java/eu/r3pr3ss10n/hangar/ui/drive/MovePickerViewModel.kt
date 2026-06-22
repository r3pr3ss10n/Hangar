package eu.r3pr3ss10n.hangar.ui.drive

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import eu.r3pr3ss10n.hangar.R
import eu.r3pr3ss10n.hangar.data.repository.DriveEvents
import eu.r3pr3ss10n.hangar.data.repository.DriveRepository
import eu.r3pr3ss10n.hangar.domain.Folder
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class MovePickerUiState(
    val loading: Boolean = true,
    val currentFolderId: String? = null,
    val currentName: String = "My Drive",
    val folders: List<Folder> = emptyList(),
    val parentStack: List<Pair<String?, String>> = emptyList(),
    val moving: Boolean = false,
    val error: String? = null,
)

/**
 * Backs the move-destination picker: browses the folder tree (folders only),
 * excludes the item being moved when it's a folder, and performs the move into
 * the currently shown folder. Notifies [DriveEvents] so listings refresh.
 */
@HiltViewModel
class MovePickerViewModel @Inject constructor(
    @param:dagger.hilt.android.qualifiers.ApplicationContext private val context: Context,
    private val driveRepository: DriveRepository,
    private val driveEvents: DriveEvents,
) : ViewModel() {

    private val _state = MutableStateFlow(MovePickerUiState())
    val state: StateFlow<MovePickerUiState> = _state.asStateFlow()

    private var movingId: String = ""
    private var isFolder: Boolean = false

    fun start(movingId: String, isFolder: Boolean) {
        this.movingId = movingId
        this.isFolder = isFolder
        open(null, context.getString(R.string.drive_my_drive), pushParent = false)
    }

    fun open(folderId: String?, name: String, pushParent: Boolean = true) {
        viewModelScope.launch {
            val stack = if (pushParent) {
                _state.value.parentStack + (_state.value.currentFolderId to _state.value.currentName)
            } else {
                emptyList()
            }
            _state.value = _state.value.copy(loading = true, error = null)
            try {
                val contents = driveRepository.list(folderId)
                // Don't allow moving a folder into itself.
                val folders = contents.folders.filterNot { isFolder && it.id == movingId }
                _state.value = MovePickerUiState(
                    loading = false,
                    currentFolderId = folderId,
                    currentName = name,
                    folders = folders,
                    parentStack = stack,
                )
            } catch (e: Exception) {
                _state.value = _state.value.copy(loading = false, error = e.message ?: context.getString(R.string.store_load_folder_failed))
            }
        }
    }

    fun up() {
        val stack = _state.value.parentStack
        if (stack.isEmpty()) return
        val (parentId, parentName) = stack.last()
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true)
            try {
                val contents = driveRepository.list(parentId)
                val folders = contents.folders.filterNot { isFolder && it.id == movingId }
                _state.value = MovePickerUiState(
                    loading = false,
                    currentFolderId = parentId,
                    currentName = parentName,
                    folders = folders,
                    parentStack = stack.dropLast(1),
                )
            } catch (e: Exception) {
                _state.value = _state.value.copy(loading = false, error = e.message ?: context.getString(R.string.store_load_folder_failed))
            }
        }
    }

    fun moveHere(onDone: () -> Unit) {
        viewModelScope.launch {
            _state.value = _state.value.copy(moving = true)
            try {
                val dest = _state.value.currentFolderId
                if (isFolder) driveRepository.moveFolder(movingId, dest)
                else driveRepository.moveFile(movingId, dest)
                driveEvents.notifyChanged()
                onDone()
            } catch (e: Exception) {
                _state.value = _state.value.copy(moving = false, error = e.message ?: context.getString(R.string.drive_move_failed))
            }
        }
    }

    val canGoUp: Boolean get() = _state.value.parentStack.isNotEmpty()
}
