package eu.r3pr3ss10n.hangar.ui.sharing

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import eu.r3pr3ss10n.hangar.R
import eu.r3pr3ss10n.hangar.data.remote.ServerUrlProvider
import eu.r3pr3ss10n.hangar.data.repository.SharingRepository
import eu.r3pr3ss10n.hangar.domain.Share
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ShareLinkUiState(
    val loading: Boolean = true,
    val shares: List<Share> = emptyList(),
    val error: String? = null,
)

/** Manages a file's public share links. The full URL is built from the server origin. */
@HiltViewModel
class ShareLinkViewModel @Inject constructor(
    private val sharingRepository: SharingRepository,
    private val serverUrl: ServerUrlProvider,
    @param:dagger.hilt.android.qualifiers.ApplicationContext private val context: Context,
) : ViewModel() {

    private val _state = MutableStateFlow(ShareLinkUiState())
    val state: StateFlow<ShareLinkUiState> = _state.asStateFlow()

    private var fileId: String = ""

    fun load(fileId: String) {
        this.fileId = fileId
        viewModelScope.launch {
            _state.value = ShareLinkUiState(loading = true)
            try {
                _state.value = ShareLinkUiState(loading = false, shares = sharingRepository.listShares(fileId))
            } catch (e: Exception) {
                _state.value = ShareLinkUiState(loading = false, error = e.message ?: context.getString(R.string.share_load_failed))
            }
        }
    }

    /** Builds the public share URL the web app would render for [token]. */
    fun shareUrl(token: String): String {
        val base = serverUrl.baseUrl ?: return token
        val b = StringBuilder("${base.scheme}://${base.host}")
        val isDefault = (base.scheme == "https" && base.port == 443) || (base.scheme == "http" && base.port == 80)
        if (!isDefault) b.append(":${base.port}")
        b.append("/s/").append(token)
        return b.toString()
    }

    fun createLink(expiresInSeconds: Long?) {
        viewModelScope.launch {
            try {
                sharingRepository.createShare(fileId, expiresInSeconds)
                _state.value = _state.value.copy(shares = sharingRepository.listShares(fileId))
            } catch (e: Exception) {
                _state.value = _state.value.copy(error = e.message ?: context.getString(R.string.share_create_failed))
            }
        }
    }

    fun revoke(token: String) {
        viewModelScope.launch {
            try {
                sharingRepository.deleteShare(token)
                _state.value = _state.value.copy(shares = sharingRepository.listShares(fileId))
            } catch (e: Exception) {
                _state.value = _state.value.copy(error = e.message ?: context.getString(R.string.share_revoke_failed))
            }
        }
    }
}
