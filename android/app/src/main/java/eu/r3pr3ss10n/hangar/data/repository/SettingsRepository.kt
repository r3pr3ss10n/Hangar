package eu.r3pr3ss10n.hangar.data.repository

import eu.r3pr3ss10n.hangar.data.remote.HangarApi
import eu.r3pr3ss10n.hangar.data.remote.dto.SettingsPatchBody
import javax.inject.Inject
import javax.inject.Singleton

/** SettingsRepository covers per-user preferences and the storage-used figure. */
@Singleton
class SettingsRepository @Inject constructor(
    private val api: HangarApi,
) {
    suspend fun generateThumbnails(): Boolean = runApi { api.getSettings().generateThumbnails }

    suspend fun setGenerateThumbnails(on: Boolean): Boolean = runApi {
        api.patchSettings(SettingsPatchBody(generateThumbnails = on)).generateThumbnails
    }

    suspend fun storageUsedBytes(): Long = runApi { api.storage().usedBytes }
}
