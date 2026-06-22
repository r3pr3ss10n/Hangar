package eu.r3pr3ss10n.hangar.ui.account

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import eu.r3pr3ss10n.hangar.data.repository.AuthRepository
import eu.r3pr3ss10n.hangar.data.repository.SettingsRepository
import eu.r3pr3ss10n.hangar.ui.util.formatSize
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AccountUiState(
    val storageText: String = "—",
    val generateThumbnails: Boolean = true,
)

@HiltViewModel
class AccountViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(AccountUiState())
    val state: StateFlow<AccountUiState> = _state.asStateFlow()

    init { load() }

    private fun load() {
        viewModelScope.launch {
            val used = runCatching { settingsRepository.storageUsedBytes() }.getOrNull()
            val thumbs = runCatching { settingsRepository.generateThumbnails() }.getOrDefault(true)
            _state.value = AccountUiState(
                storageText = used?.let { formatSize(it) } ?: "—",
                generateThumbnails = thumbs,
            )
        }
    }

    fun setGenerateThumbnails(on: Boolean) {
        _state.value = _state.value.copy(generateThumbnails = on)
        viewModelScope.launch {
            runCatching { settingsRepository.setGenerateThumbnails(on) }
        }
    }

    fun logout(onDone: () -> Unit) {
        viewModelScope.launch {
            authRepository.logout()
            onDone()
        }
    }
}
