package eu.r3pr3ss10n.hangar.ui.tags

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.r3pr3ss10n.hangar.R
import eu.r3pr3ss10n.hangar.data.repository.TagsRepository
import eu.r3pr3ss10n.hangar.domain.Tag
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AssignTagsUiState(
    val loading: Boolean = true,
    val tags: List<Tag> = emptyList(),
    val assigned: Set<String> = emptySet(),
    val error: String? = null,
)

/** Toggles tags on a single file or folder, seeded from the labels bundle. */
@HiltViewModel
class AssignTagsViewModel @Inject constructor(
    private val tagsRepository: TagsRepository,
    @param:ApplicationContext private val context: Context,
) : ViewModel() {

    private val _state = MutableStateFlow(AssignTagsUiState())
    val state: StateFlow<AssignTagsUiState> = _state.asStateFlow()

    private var itemId: String = ""
    private var isFolder: Boolean = false

    fun load(itemId: String, isFolder: Boolean) {
        this.itemId = itemId
        this.isFolder = isFolder
        viewModelScope.launch {
            _state.value = AssignTagsUiState(loading = true)
            try {
                val labels = tagsRepository.labels()
                val assigned = (if (isFolder) labels.folderTags else labels.fileTags)[itemId].orEmpty().toSet()
                _state.value = AssignTagsUiState(loading = false, tags = labels.tags, assigned = assigned)
            } catch (e: Exception) {
                _state.value = AssignTagsUiState(loading = false, error = e.message ?: context.getString(R.string.tag_load_failed))
            }
        }
    }

    fun toggle(tagId: String) {
        val currently = _state.value.assigned.contains(tagId)
        val next = if (currently) _state.value.assigned - tagId else _state.value.assigned + tagId
        // Optimistic update; revert on failure.
        _state.value = _state.value.copy(assigned = next)
        viewModelScope.launch {
            try {
                if (isFolder) tagsRepository.setFolderTag(itemId, tagId, !currently)
                else tagsRepository.setFileTag(itemId, tagId, !currently)
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    assigned = if (currently) _state.value.assigned + tagId else _state.value.assigned - tagId,
                    error = e.message ?: context.getString(R.string.tag_assign_failed),
                )
            }
        }
    }
}
