package eu.r3pr3ss10n.hangar.data.repository

import eu.r3pr3ss10n.hangar.data.local.AppPreferences
import eu.r3pr3ss10n.hangar.data.remote.HangarApi
import eu.r3pr3ss10n.hangar.data.remote.PersistentCookieJar
import eu.r3pr3ss10n.hangar.data.remote.ServerUrlProvider
import eu.r3pr3ss10n.hangar.data.remote.dto.CredentialsBody
import eu.r3pr3ss10n.hangar.domain.User
import javax.inject.Inject
import javax.inject.Singleton

/**
 * AuthRepository owns the connect/session lifecycle: pointing the client at a
 * server, checking whether first-time setup is still pending (web-only), login,
 * resolving the current user from the persisted cookie, and logout.
 */
@Singleton
class AuthRepository @Inject constructor(
    private val api: HangarApi,
    private val prefs: AppPreferences,
    private val serverUrl: ServerUrlProvider,
    private val cookieJar: PersistentCookieJar,
) {
    /** Seeds the in-memory server origin from persisted prefs at startup. */
    suspend fun restoreServer(): String? {
        val url = prefs.serverUrlOnce()
        serverUrl.set(url)
        return url
    }

    val hasServer: Boolean get() = serverUrl.baseUrl != null

    /** Points the client at [rawUrl], normalising and persisting it. */
    suspend fun connect(rawUrl: String) {
        val normalized = ServerUrlProvider.normalize(rawUrl)
        serverUrl.set(normalized)
        prefs.setServerUrl(normalized)
    }

    /** Forgets the server and clears the session cookie. */
    suspend fun forgetServer() {
        cookieJar.clear()
        prefs.clearServerUrl()
        serverUrl.set(null)
    }

    /** True when the instance still needs its first admin (created in web only). */
    suspend fun needsSetup(): Boolean = runApi { api.setupStatus().needsSetup }

    suspend fun login(username: String, password: String): User =
        runApi { api.login(CredentialsBody(username, password)).user.toDomain() }

    /** Resolves the current user from the session cookie; null if unauthenticated. */
    suspend fun currentUser(): User? = try {
        runApi { api.me().user.toDomain() }
    } catch (e: ApiException) {
        if (e.status == 401) null else throw e
    }

    suspend fun logout() {
        runCatching { runApi { api.logout() } }
        cookieJar.clear()
    }
}
