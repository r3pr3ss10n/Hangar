package eu.r3pr3ss10n.hangar.ui.preview

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.r3pr3ss10n.hangar.R
import eu.r3pr3ss10n.hangar.data.download.Downloader
import eu.r3pr3ss10n.hangar.data.repository.DriveRepository
import eu.r3pr3ss10n.hangar.domain.FileItem
import eu.r3pr3ss10n.hangar.ui.util.UrlBuilder
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PreviewUiState(
    val loading: Boolean = true,
    val file: FileItem? = null,
    val error: String? = null,
)

@HiltViewModel
class PreviewViewModel @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val driveRepository: DriveRepository,
    private val downloader: Downloader,
    private val urlBuilder: UrlBuilder,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val fileId: String = savedStateHandle.get<String>("id").orEmpty()

    private val _state = MutableStateFlow(PreviewUiState())
    val state: StateFlow<PreviewUiState> = _state.asStateFlow()

    init { load() }

    fun imageUrl(): String = urlBuilder.fileBytes(fileId)

    private fun load() {
        viewModelScope.launch {
            try {
                val file = driveRepository.fileMeta(fileId)
                _state.value = PreviewUiState(loading = false, file = file)
            } catch (e: Exception) {
                _state.value = PreviewUiState(loading = false, error = e.message ?: context.getString(R.string.preview_failed_to_load))
            }
        }
    }

    fun download() {
        val f = _state.value.file ?: return
        downloader.enqueue(f.id, f.name, f.mime)
    }
}
