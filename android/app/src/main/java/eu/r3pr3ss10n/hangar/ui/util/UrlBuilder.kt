package eu.r3pr3ss10n.hangar.ui.util

import eu.r3pr3ss10n.hangar.data.remote.ServerUrlProvider

/**
 * UrlBuilder produces absolute URLs for byte/thumbnail routes that Coil and the
 * downloader hit directly (outside Retrofit). It reads the connected origin from
 * [ServerUrlProvider]; the OkHttp interceptor would rewrite the host anyway, but
 * building the real origin keeps the request self-describing and cache keys
 * stable per server.
 */
class UrlBuilder(private val serverUrl: ServerUrlProvider) {
    private fun origin(): String = serverUrl.baseUrl?.let {
        val b = StringBuilder("${it.scheme}://${it.host}")
        val isDefault = (it.scheme == "https" && it.port == 443) || (it.scheme == "http" && it.port == 80)
        if (!isDefault) b.append(":${it.port}")
        b.toString()
    } ?: ""

    fun thumb(fileId: String) = "${origin()}/api/files/$fileId/thumb"
    fun download(fileId: String) = "${origin()}/api/files/$fileId"
    fun fileBytes(fileId: String) = "${origin()}/api/files/$fileId"
}
