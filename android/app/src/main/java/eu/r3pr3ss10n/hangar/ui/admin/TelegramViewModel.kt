package eu.r3pr3ss10n.hangar.ui.admin

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import eu.r3pr3ss10n.hangar.R
import eu.r3pr3ss10n.hangar.data.repository.AdminRepository
import eu.r3pr3ss10n.hangar.domain.TelegramState
import eu.r3pr3ss10n.hangar.domain.TelegramStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Stage of the Telegram link wizard. */
enum class LinkStage { IDLE, PHONE, CODE, PASSWORD }

data class TelegramUiState(
    val loading: Boolean = true,
    val status: TelegramStatus = TelegramStatus.NOT_LINKED,
    val isPremium: Boolean = false,
    val stage: LinkStage = LinkStage.IDLE,
    val phone: String = "",
    val code: String = "",
    val password: String = "",
    val busy: Boolean = false,
    val error: String? = null,
)

@HiltViewModel
class TelegramViewModel @Inject constructor(
    private val adminRepository: AdminRepository,
    @param:dagger.hilt.android.qualifiers.ApplicationContext private val context: Context,
) : ViewModel() {

    private val _state = MutableStateFlow(TelegramUiState())
    val state: StateFlow<TelegramUiState> = _state.asStateFlow()

    private var linkId: String? = null

    init { refresh() }

    fun refresh() {
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true, error = null)
            try {
                applyStatus(adminRepository.telegramStatus())
            } catch (e: Exception) {
                _state.value = _state.value.copy(loading = false, error = e.message ?: context.getString(R.string.tg_failed_load_status))
            }
        }
    }

    private fun applyStatus(s: TelegramState) {
        _state.value = _state.value.copy(
            loading = false,
            status = s.status,
            isPremium = s.isPremium,
        )
    }

    fun onPhoneChange(v: String) { _state.value = _state.value.copy(phone = v, error = null) }
    fun onCodeChange(v: String) { _state.value = _state.value.copy(code = v, error = null) }
    fun onPasswordChange(v: String) { _state.value = _state.value.copy(password = v, error = null) }

    fun beginLink() { _state.value = _state.value.copy(stage = LinkStage.PHONE) }

    fun submitPhone() {
        val phone = _state.value.phone.trim()
        if (phone.isEmpty()) return
        viewModelScope.launch {
            _state.value = _state.value.copy(busy = true, error = null)
            try {
                linkId = adminRepository.linkStart(phone)
                _state.value = _state.value.copy(busy = false, stage = LinkStage.CODE)
            } catch (e: Exception) {
                _state.value = _state.value.copy(busy = false, error = e.message ?: context.getString(R.string.tg_failed_send_code))
            }
        }
    }

    fun submitCode() {
        val id = linkId ?: return
        val code = _state.value.code.trim()
        if (code.isEmpty()) return
        viewModelScope.launch {
            _state.value = _state.value.copy(busy = true, error = null)
            try {
                val needPassword = adminRepository.linkCode(id, code)
                if (needPassword) {
                    _state.value = _state.value.copy(busy = false, stage = LinkStage.PASSWORD)
                } else {
                    finishLink()
                }
            } catch (e: Exception) {
                _state.value = _state.value.copy(busy = false, error = e.message ?: context.getString(R.string.tg_invalid_code))
            }
        }
    }

    fun submitPassword() {
        val id = linkId ?: return
        val password = _state.value.password
        if (password.isEmpty()) return
        viewModelScope.launch {
            _state.value = _state.value.copy(busy = true, error = null)
            try {
                adminRepository.linkPassword(id, password)
                finishLink()
            } catch (e: Exception) {
                _state.value = _state.value.copy(busy = false, error = e.message ?: context.getString(R.string.tg_invalid_password))
            }
        }
    }

    private suspend fun finishLink() {
        linkId = null
        _state.value = _state.value.copy(
            busy = false,
            stage = LinkStage.IDLE,
            phone = "",
            code = "",
            password = "",
        )
        applyStatus(adminRepository.telegramStatus())
    }

    fun cancelLink() {
        val id = linkId
        viewModelScope.launch {
            if (id != null) runCatching { adminRepository.linkCancel(id) }
            linkId = null
            _state.value = _state.value.copy(
                stage = LinkStage.IDLE, phone = "", code = "", password = "", busy = false, error = null,
            )
        }
    }

    fun unlink() {
        viewModelScope.launch {
            _state.value = _state.value.copy(busy = true)
            try {
                adminRepository.unlink()
                applyStatus(adminRepository.telegramStatus())
            } catch (e: Exception) {
                _state.value = _state.value.copy(busy = false, error = e.message ?: context.getString(R.string.tg_failed_unlink))
            }
        }
    }
}
