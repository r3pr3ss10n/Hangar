package eu.r3pr3ss10n.hangar.data.remote

import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import javax.inject.Inject
import javax.inject.Singleton

/**
 * ServerUrlProvider holds the currently connected Hangar server origin. Retrofit
 * is built once against a placeholder base URL; [ServerUrlInterceptor] rewrites
 * every request's scheme/host/port from the value held here, so switching
 * servers needs no Retrofit rebuild. The value is seeded at startup from
 * [eu.r3pr3ss10n.hangar.data.local.AppPreferences] and updated on connect.
 */
@Singleton
class ServerUrlProvider @Inject constructor() {
    @Volatile
    var baseUrl: HttpUrl? = null
        private set

    fun set(url: String?) {
        baseUrl = url?.trim()?.takeIf { it.isNotEmpty() }?.let { normalize(it).toHttpUrlOrNull() }
    }

    companion object {
        /** Adds a scheme if missing and trims trailing slashes for a clean origin. */
        fun normalize(raw: String): String {
            var s = raw.trim()
            if (!s.startsWith("http://") && !s.startsWith("https://")) {
                s = "https://$s"
            }
            return s.trimEnd('/')
        }
    }
}
