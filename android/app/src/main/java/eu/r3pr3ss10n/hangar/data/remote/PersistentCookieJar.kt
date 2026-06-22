package eu.r3pr3ss10n.hangar.data.remote

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import javax.inject.Inject
import javax.inject.Singleton

/**
 * PersistentCookieJar stores cookies in EncryptedSharedPreferences so the
 * session cookie (hangar_session) survives process death. Cookies are kept in a
 * single encrypted blob keyed by name+domain+path; expired cookies are dropped
 * on read. This is deliberately simple: a self-hosted client talks to one
 * origin, and the only cookie that matters is the session.
 */
@Singleton
class PersistentCookieJar @Inject constructor(
    @dagger.hilt.android.qualifiers.ApplicationContext context: Context,
) : CookieJar {

    private val prefs: SharedPreferences = run {
        val key = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            "hangar_cookies",
            key,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    private val json = Json { ignoreUnknownKeys = true }

    @Serializable
    private data class StoredCookie(
        val name: String,
        val value: String,
        val expiresAt: Long,
        val domain: String,
        val path: String,
        val secure: Boolean,
        val httpOnly: Boolean,
        val hostOnly: Boolean,
    )

    private val lock = Any()

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        if (cookies.isEmpty()) return
        synchronized(lock) {
            val current = load().associateBy { it.uniqueKey() }.toMutableMap()
            for (c in cookies) {
                current[c.uniqueKey()] = c.toStored()
            }
            persist(current.values.toList())
        }
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        synchronized(lock) {
            val now = System.currentTimeMillis()
            val all = load()
            val live = all.filter { it.expiresAt > now }
            if (live.size != all.size) persist(live)
            return live.mapNotNull { it.toCookie() }.filter { it.matches(url) }
        }
    }

    /** Drops every stored cookie (used on logout / server switch). */
    fun clear() {
        synchronized(lock) { prefs.edit().remove(STORE_KEY).apply() }
    }

    /**
     * Builds a "name=value; ..." Cookie header for [url], for callers that bypass
     * OkHttp (e.g. the tus client, which uses HttpURLConnection). Returns null
     * when no cookie applies.
     */
    fun cookieHeader(url: HttpUrl): String? {
        val cookies = loadForRequest(url)
        if (cookies.isEmpty()) return null
        return cookies.joinToString("; ") { "${it.name}=${it.value}" }
    }

    private fun load(): List<StoredCookie> {
        val raw = prefs.getString(STORE_KEY, null) ?: return emptyList()
        return runCatching {
            json.decodeFromString(ListSerializer(StoredCookie.serializer()), raw)
        }.getOrDefault(emptyList())
    }

    private fun persist(cookies: List<StoredCookie>) {
        prefs.edit().putString(
            STORE_KEY,
            json.encodeToString(ListSerializer(StoredCookie.serializer()), cookies),
        ).apply()
    }

    private fun Cookie.uniqueKey() = "$name|$domain|$path"
    private fun StoredCookie.uniqueKey() = "$name|$domain|$path"

    private fun Cookie.toStored() = StoredCookie(
        name = name,
        value = value,
        // A session-only cookie (no Max-Age/Expires) reports persistent=false and
        // expiresAt far in the future; clamp it to a 30-day window so it survives
        // restarts but does not live forever.
        expiresAt = if (persistent) expiresAt else System.currentTimeMillis() + THIRTY_DAYS_MS,
        domain = domain,
        path = path,
        secure = secure,
        httpOnly = httpOnly,
        hostOnly = hostOnly,
    )

    private fun StoredCookie.toCookie(): Cookie? = runCatching {
        Cookie.Builder()
            .name(name)
            .value(value)
            .expiresAt(expiresAt)
            .path(path)
            .apply {
                if (hostOnly) hostOnlyDomain(domain) else domain(domain)
                if (secure) secure()
                if (httpOnly) httpOnly()
            }
            .build()
    }.getOrNull()

    companion object {
        private const val STORE_KEY = "cookies_v1"
        private const val THIRTY_DAYS_MS = 30L * 24 * 60 * 60 * 1000
    }
}
