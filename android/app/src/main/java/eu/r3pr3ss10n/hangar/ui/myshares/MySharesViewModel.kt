package eu.r3pr3ss10n.hangar.ui.myshares

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.r3pr3ss10n.hangar.R
import eu.r3pr3ss10n.hangar.data.remote.ServerUrlProvider
import eu.r3pr3ss10n.hangar.data.repository.SharingRepository
import eu.r3pr3ss10n.hangar.domain.MyShare
import eu.r3pr3ss10n.hangar.ui.util.UrlBuilder
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class MySharesUiState(
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val shares: List<MyShare> = emptyList(),
    val error: String? = null,
) {
    val isEmpty get() = shares.isEmpty()
}

/**
 * Backs the "My links" screen: every public share link the user has created,
 * each paired with its file. Listing-only here — revoking removes the link, the
 * full per-file management lives in the drive's ShareLinkSheet.
 */
@HiltViewModel
class MySharesViewModel @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val sharingRepository: SharingRepository,
    private val serverUrl: ServerUrlProvider,
    private val urlBuilder: UrlBuilder,
) : ViewModel() {

    private val _state = MutableStateFlow(MySharesUiState())
    val state: StateFlow<MySharesUiState> = _state.asStateFlow()

    init { refresh() }

    fun thumbUrl(fileId: String): String = urlBuilder.thumb(fileId)

    /** Builds the public share URL the web app would render for [token]. */
    fun shareUrl(token: String): String {
        val base = serverUrl.baseUrl ?: return token
        val b = StringBuilder("${base.scheme}://${base.host}")
        val isDefault = (base.scheme == "https" && base.port == 443) || (base.scheme == "http" && base.port == 80)
        if (!isDefault) b.append(":${base.port}")
        b.append("/s/").append(token)
        return b.toString()
    }

    fun refresh() {
        viewModelScope.launch {
            _state.value = _state.value.copy(
                loading = _state.value.isEmpty,
                refreshing = true,
                error = null,
            )
            try {
                _state.value = MySharesUiState(loading = false, shares = sharingRepository.myShares())
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    loading = false,
                    refreshing = false,
                    error = e.message ?: context.getString(R.string.my_links_failed_to_load),
                )
            }
        }
    }

    fun revoke(token: String) {
        viewModelScope.launch {
            try {
                sharingRepository.deleteShare(token)
                _state.value = _state.value.copy(shares = _state.value.shares.filterNot { it.token == token })
            } catch (e: Exception) {
                _state.value = _state.value.copy(error = e.message ?: context.getString(R.string.share_revoke_failed))
            }
        }
    }
}
