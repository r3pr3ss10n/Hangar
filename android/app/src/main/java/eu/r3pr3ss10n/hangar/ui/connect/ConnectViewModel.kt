package eu.r3pr3ss10n.hangar.ui.connect

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import eu.r3pr3ss10n.hangar.R
import eu.r3pr3ss10n.hangar.data.repository.AuthRepository
import eu.r3pr3ss10n.hangar.data.repository.NetworkException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ConnectUiState(
    val url: String = "",
    val loading: Boolean = false,
    val error: String? = null,
)

@HiltViewModel
class ConnectViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    @param:dagger.hilt.android.qualifiers.ApplicationContext private val context: Context,
) : ViewModel() {

    private val _state = MutableStateFlow(ConnectUiState())
    val state: StateFlow<ConnectUiState> = _state.asStateFlow()

    fun onUrlChange(value: String) {
        _state.value = _state.value.copy(url = value, error = null)
    }

    /** Connects to the entered server and probes /setup/status to validate it. */
    fun connect(onConnected: () -> Unit) {
        val raw = _state.value.url.trim()
        if (raw.isEmpty()) {
            _state.value = _state.value.copy(error = context.getString(R.string.connect_enter_address))
            return
        }
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true, error = null)
            try {
                authRepository.connect(raw)
                // Validate reachability before committing the user to the next screen.
                authRepository.needsSetup()
                onConnected()
            } catch (e: NetworkException) {
                authRepository.forgetServer()
                _state.value = _state.value.copy(
                    loading = false,
                    error = context.getString(R.string.connect_unreachable),
                )
            } catch (e: Exception) {
                authRepository.forgetServer()
                _state.value = _state.value.copy(
                    loading = false,
                    error = context.getString(R.string.connect_not_hangar),
                )
            }
        }
    }
}
