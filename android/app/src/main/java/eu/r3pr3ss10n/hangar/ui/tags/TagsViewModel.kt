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

data class TagsUiState(
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val tags: List<Tag> = emptyList(),
    val error: String? = null,
)

@HiltViewModel
class TagsViewModel @Inject constructor(
    private val tagsRepository: TagsRepository,
    @param:ApplicationContext private val context: Context,
) : ViewModel() {

    private val _state = MutableStateFlow(TagsUiState())
    val state: StateFlow<TagsUiState> = _state.asStateFlow()

    init { refresh() }

    fun refresh() {
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = _state.value.tags.isEmpty(), refreshing = true, error = null)
            try {
                _state.value = TagsUiState(loading = false, tags = tagsRepository.list())
            } catch (e: Exception) {
                _state.value = _state.value.copy(loading = false, refreshing = false, error = e.message ?: context.getString(R.string.tag_load_failed))
            }
        }
    }

    fun create(name: String, color: String) = mutate { tagsRepository.create(name.trim(), color) }
    fun update(id: String, name: String, color: String) = mutate { tagsRepository.update(id, name.trim(), color) }
    fun delete(id: String) = mutate { tagsRepository.delete(id) }

    private fun mutate(block: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                block()
                refresh()
            } catch (e: Exception) {
                _state.value = _state.value.copy(error = e.message ?: context.getString(R.string.tag_something_wrong))
            }
        }
    }
}
