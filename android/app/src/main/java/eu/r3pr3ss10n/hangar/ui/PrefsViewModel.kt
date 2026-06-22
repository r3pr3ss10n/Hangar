package eu.r3pr3ss10n.hangar.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import eu.r3pr3ss10n.hangar.data.local.AppPreferences
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Holds lightweight UI preferences shared across the authenticated app. */
@HiltViewModel
class PrefsViewModel @Inject constructor(
    private val prefs: AppPreferences,
) : ViewModel() {

    val gridView: StateFlow<Boolean> = prefs.gridView.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = false,
    )

    fun toggleGridView() {
        viewModelScope.launch { prefs.setGridView(!gridView.value) }
    }
}
