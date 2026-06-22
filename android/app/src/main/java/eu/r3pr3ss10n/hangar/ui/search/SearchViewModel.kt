package eu.r3pr3ss10n.hangar.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import eu.r3pr3ss10n.hangar.data.repository.SearchRepository
import eu.r3pr3ss10n.hangar.domain.FileHit
import eu.r3pr3ss10n.hangar.domain.FolderHit
import eu.r3pr3ss10n.hangar.ui.util.UrlBuilder
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SearchUiState(
    val query: String = "",
    val loading: Boolean = false,
    val folders: List<FolderHit> = emptyList(),
    val files: List<FileHit> = emptyList(),
    val searched: Boolean = false,
)

@OptIn(FlowPreview::class)
@HiltViewModel
class SearchViewModel @Inject constructor(
    private val searchRepository: SearchRepository,
    private val urlBuilder: UrlBuilder,
) : ViewModel() {

    private val queryFlow = MutableStateFlow("")
    private val _state = MutableStateFlow(SearchUiState())
    val state: StateFlow<SearchUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            queryFlow
                .debounce(250)
                .distinctUntilChanged()
                .collect { q -> runSearch(q) }
        }
    }

    fun thumbUrl(fileId: String): String = urlBuilder.thumb(fileId)

    fun onQueryChange(value: String) {
        _state.value = _state.value.copy(query = value)
        queryFlow.value = value.trim()
    }

    private suspend fun runSearch(query: String) {
        if (query.isBlank()) {
            _state.value = _state.value.copy(folders = emptyList(), files = emptyList(), searched = false, loading = false)
            return
        }
        _state.value = _state.value.copy(loading = true)
        try {
            val result = searchRepository.search(query)
            _state.value = _state.value.copy(
                loading = false,
                folders = result.folders,
                files = result.files,
                searched = true,
            )
        } catch (e: Exception) {
            _state.value = _state.value.copy(loading = false, searched = true)
        }
    }
}
