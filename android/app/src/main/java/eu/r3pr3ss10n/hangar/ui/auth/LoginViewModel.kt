package eu.r3pr3ss10n.hangar.ui.auth

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import eu.r3pr3ss10n.hangar.R
import eu.r3pr3ss10n.hangar.data.repository.ApiException
import eu.r3pr3ss10n.hangar.data.repository.AuthRepository
import eu.r3pr3ss10n.hangar.data.repository.NetworkException
import eu.r3pr3ss10n.hangar.domain.User
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class LoginUiState(
    val username: String = "",
    val password: String = "",
    val loading: Boolean = false,
    val error: String? = null,
)

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    @param:dagger.hilt.android.qualifiers.ApplicationContext private val context: Context,
) : ViewModel() {

    private val _state = MutableStateFlow(LoginUiState())
    val state: StateFlow<LoginUiState> = _state.asStateFlow()

    fun onUsernameChange(v: String) { _state.value = _state.value.copy(username = v, error = null) }
    fun onPasswordChange(v: String) { _state.value = _state.value.copy(password = v, error = null) }

    fun login(onSuccess: (User) -> Unit) {
        val s = _state.value
        if (s.username.isBlank() || s.password.isBlank()) {
            _state.value = s.copy(error = context.getString(R.string.login_enter_credentials))
            return
        }
        viewModelScope.launch {
            _state.value = s.copy(loading = true, error = null)
            try {
                val user = authRepository.login(s.username.trim(), s.password)
                onSuccess(user)
            } catch (e: ApiException) {
                _state.value = _state.value.copy(loading = false, error = e.message)
            } catch (e: NetworkException) {
                _state.value = _state.value.copy(loading = false, error = context.getString(R.string.login_network_error))
            }
        }
    }
}
