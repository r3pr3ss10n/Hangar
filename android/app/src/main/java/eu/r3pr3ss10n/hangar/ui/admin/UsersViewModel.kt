package eu.r3pr3ss10n.hangar.ui.admin

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import eu.r3pr3ss10n.hangar.R
import eu.r3pr3ss10n.hangar.data.repository.AdminRepository
import eu.r3pr3ss10n.hangar.domain.Role
import eu.r3pr3ss10n.hangar.domain.User
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class UsersUiState(
    val loading: Boolean = true,
    val users: List<User> = emptyList(),
    val error: String? = null,
    val message: String? = null,
)

@HiltViewModel
class UsersViewModel @Inject constructor(
    private val adminRepository: AdminRepository,
    @param:dagger.hilt.android.qualifiers.ApplicationContext private val context: Context,
) : ViewModel() {

    private val _state = MutableStateFlow(UsersUiState())
    val state: StateFlow<UsersUiState> = _state.asStateFlow()

    init { refresh() }

    fun refresh() {
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = _state.value.users.isEmpty(), error = null)
            try {
                _state.value = UsersUiState(loading = false, users = adminRepository.listUsers())
            } catch (e: Exception) {
                _state.value = _state.value.copy(loading = false, error = e.message ?: context.getString(R.string.users_failed_load))
            }
        }
    }

    fun create(username: String, password: String, role: Role) = mutate {
        adminRepository.createUser(username.trim(), password, role)
    }

    fun delete(id: String) = mutate { adminRepository.deleteUser(id) }

    fun setPassword(id: String, password: String) = mutate {
        adminRepository.setUserPassword(id, password)
        _state.value = _state.value.copy(message = context.getString(R.string.users_password_updated))
    }

    private fun mutate(block: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                block()
                refresh()
            } catch (e: Exception) {
                _state.value = _state.value.copy(message = e.message ?: context.getString(R.string.users_something_went_wrong))
            }
        }
    }

    fun consumeMessage() { _state.value = _state.value.copy(message = null) }
}
