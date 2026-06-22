package eu.r3pr3ss10n.hangar.ui.sharing

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import eu.r3pr3ss10n.hangar.R
import eu.r3pr3ss10n.hangar.data.repository.SharingRepository
import eu.r3pr3ss10n.hangar.domain.Grant
import eu.r3pr3ss10n.hangar.domain.ShareableUser
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class GrantUiState(
    val loading: Boolean = true,
    val grants: List<Grant> = emptyList(),
    val shareableUsers: List<ShareableUser> = emptyList(),
    val error: String? = null,
) {
    /** Users not already granted access, for the add picker. */
    val addable: List<ShareableUser>
        get() = shareableUsers.filterNot { u -> grants.any { it.recipientId == u.id } }
}

/** Manages user-to-user grants on a file or folder. */
@HiltViewModel
class GrantViewModel @Inject constructor(
    private val sharingRepository: SharingRepository,
    @param:dagger.hilt.android.qualifiers.ApplicationContext private val context: Context,
) : ViewModel() {

    private val _state = MutableStateFlow(GrantUiState())
    val state: StateFlow<GrantUiState> = _state.asStateFlow()

    private var id: String = ""
    private var isFolder: Boolean = false

    fun load(id: String, isFolder: Boolean) {
        this.id = id
        this.isFolder = isFolder
        viewModelScope.launch {
            _state.value = GrantUiState(loading = true)
            try {
                val grants = if (isFolder) sharingRepository.folderGrants(id) else sharingRepository.fileGrants(id)
                val users = runCatching { sharingRepository.shareableUsers() }.getOrDefault(emptyList())
                _state.value = GrantUiState(loading = false, grants = grants, shareableUsers = users)
            } catch (e: Exception) {
                _state.value = GrantUiState(loading = false, error = e.message ?: context.getString(R.string.grant_load_failed))
            }
        }
    }

    fun grant(recipientId: String) {
        viewModelScope.launch {
            try {
                val grants = if (isFolder) sharingRepository.grantFolder(id, recipientId)
                else sharingRepository.grantFile(id, recipientId)
                _state.value = _state.value.copy(grants = grants)
            } catch (e: Exception) {
                _state.value = _state.value.copy(error = e.message ?: context.getString(R.string.grant_grant_failed))
            }
        }
    }

    fun revoke(recipientId: String) {
        viewModelScope.launch {
            try {
                if (isFolder) sharingRepository.revokeFolderGrant(id, recipientId)
                else sharingRepository.revokeFileGrant(id, recipientId)
                val grants = if (isFolder) sharingRepository.folderGrants(id) else sharingRepository.fileGrants(id)
                _state.value = _state.value.copy(grants = grants)
            } catch (e: Exception) {
                _state.value = _state.value.copy(error = e.message ?: context.getString(R.string.grant_revoke_failed))
            }
        }
    }
}
