package eu.r3pr3ss10n.hangar.ui.tags

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.r3pr3ss10n.hangar.R
import eu.r3pr3ss10n.hangar.data.repository.TagsRepository
import eu.r3pr3ss10n.hangar.domain.FileItem
import eu.r3pr3ss10n.hangar.domain.Folder
import eu.r3pr3ss10n.hangar.ui.util.UrlBuilder
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class TagItemsUiState(
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val folders: List<Folder> = emptyList(),
    val files: List<FileItem> = emptyList(),
    val error: String? = null,
) {
    val isEmpty get() = folders.isEmpty() && files.isEmpty()
}

@HiltViewModel
class TagItemsViewModel @Inject constructor(
    private val tagsRepository: TagsRepository,
    private val urlBuilder: UrlBuilder,
    @param:ApplicationContext private val context: Context,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val tagId: String = savedStateHandle.get<String>("id").orEmpty()

    private val _state = MutableStateFlow(TagItemsUiState())
    val state: StateFlow<TagItemsUiState> = _state.asStateFlow()

    init { refresh() }

    fun thumbUrl(fileId: String): String = urlBuilder.thumb(fileId)

    fun refresh() {
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = _state.value.isEmpty, refreshing = true, error = null)
            try {
                val contents = tagsRepository.items(tagId)
                _state.value = TagItemsUiState(
                    loading = false,
                    folders = contents.folders,
                    files = contents.files,
                )
            } catch (e: Exception) {
                _state.value = _state.value.copy(loading = false, refreshing = false, error = e.message ?: context.getString(R.string.tag_items_load_failed))
            }
        }
    }
}
