package eu.r3pr3ss10n.hangar.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import eu.r3pr3ss10n.hangar.data.repository.AuthRepository
import eu.r3pr3ss10n.hangar.domain.User
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Top-level app state deciding which flow the user sees on launch. */
sealed interface AppState {
    data object Loading : AppState
    data object NeedServer : AppState
    data object NeedSetup : AppState // first admin must be created in the web app
    data object NeedLogin : AppState
    data class Authenticated(val user: User) : AppState
}

@HiltViewModel
class AppViewModel @Inject constructor(
    private val authRepository: AuthRepository,
) : ViewModel() {

    private val _state = MutableStateFlow<AppState>(AppState.Loading)
    val state: StateFlow<AppState> = _state.asStateFlow()

    init {
        resolve()
    }

    /** Resolves the launch state: restore server, then session, then setup status. */
    fun resolve() {
        viewModelScope.launch {
            _state.value = AppState.Loading
            val server = authRepository.restoreServer()
            if (server == null) {
                _state.value = AppState.NeedServer
                return@launch
            }
            decideAfterServer()
        }
    }

    private suspend fun decideAfterServer() {
        try {
            val user = authRepository.currentUser()
            if (user != null) {
                _state.value = AppState.Authenticated(user)
                return
            }
            // No live session: route to login, unless the instance still needs
            // its first admin (which can only be created in the web app).
            _state.value = if (authRepository.needsSetup()) AppState.NeedSetup else AppState.NeedLogin
        } catch (e: Exception) {
            // Server unreachable / error: fall back to login so the user can retry
            // or change the server.
            _state.value = AppState.NeedLogin
        }
    }

    fun onServerConnected() {
        viewModelScope.launch {
            _state.value = AppState.Loading
            decideAfterServer()
        }
    }

    fun onLoggedIn(user: User) {
        _state.value = AppState.Authenticated(user)
    }

    fun onLoggedOut() {
        _state.value = AppState.NeedLogin
    }

    fun onForgetServer() {
        viewModelScope.launch {
            authRepository.forgetServer()
            _state.value = AppState.NeedServer
        }
    }
}
