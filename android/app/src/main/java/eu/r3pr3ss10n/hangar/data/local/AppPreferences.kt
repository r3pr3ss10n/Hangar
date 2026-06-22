package eu.r3pr3ss10n.hangar.data.local

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore by preferencesDataStore(name = "hangar_settings")

/**
 * AppPreferences persists the connected server URL and lightweight UI prefs in
 * DataStore. The server URL is read synchronously (blocking, once) by the
 * networking layer's base-URL interceptor and observed as a Flow by the UI.
 */
@Singleton
class AppPreferences @Inject constructor(
    @param:dagger.hilt.android.qualifiers.ApplicationContext private val context: Context,
) {
    private val serverUrlKey = stringPreferencesKey("server_url")
    private val gridViewKey = booleanPreferencesKey("grid_view")

    val serverUrl: Flow<String?> = context.dataStore.data.map { it[serverUrlKey] }
    val gridView: Flow<Boolean> = context.dataStore.data.map { it[gridViewKey] ?: false }

    suspend fun serverUrlOnce(): String? = serverUrl.first()

    suspend fun setServerUrl(url: String) {
        context.dataStore.edit { it[serverUrlKey] = url }
    }

    suspend fun clearServerUrl() {
        context.dataStore.edit { it.remove(serverUrlKey) }
    }

    suspend fun setGridView(grid: Boolean) {
        context.dataStore.edit { it[gridViewKey] = grid }
    }
}
