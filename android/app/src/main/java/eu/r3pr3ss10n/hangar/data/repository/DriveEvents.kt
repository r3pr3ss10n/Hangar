package eu.r3pr3ss10n.hangar.data.repository

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * DriveEvents is a lightweight app-wide bus signalling that drive contents
 * changed (e.g. a move performed from a sheet outside the owning folder screen),
 * so any visible listing can refresh. Emits nothing but a tick.
 */
@Singleton
class DriveEvents @Inject constructor() {
    private val _changed = MutableSharedFlow<Unit>(extraBufferCapacity = 8)
    val changed: SharedFlow<Unit> = _changed.asSharedFlow()

    fun notifyChanged() {
        _changed.tryEmit(Unit)
    }
}
