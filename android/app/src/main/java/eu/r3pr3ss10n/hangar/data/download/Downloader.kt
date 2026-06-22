package eu.r3pr3ss10n.hangar.data.download

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.r3pr3ss10n.hangar.ui.util.UrlBuilder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

enum class DownloadStatus { DOWNLOADING, COMPLETED, FAILED }

/**
 * One in-flight or finished download. [contentUri] is the saved MediaStore entry
 * (set on completion) used to open the file; [mime] lets the opener pick a viewer.
 */
data class DownloadItem(
    val id: String,
    val fileId: String,
    val fileName: String,
    val mime: String,
    val total: Long = 0,
    val downloaded: Long = 0,
    val status: DownloadStatus = DownloadStatus.DOWNLOADING,
    val contentUri: Uri? = null,
    val error: String? = null,
) {
    val progress: Float get() = if (total > 0) (downloaded.toFloat() / total).coerceIn(0f, 1f) else 0f
}

/**
 * Downloader streams a file's bytes from the backend (cookie-authed via the
 * shared OkHttp client) into the public Downloads collection via MediaStore.
 * Progress is surfaced in-app through [items] (no system notification); a
 * [completed] event carries the finished item so the UI can offer to open it.
 */
@Singleton
class Downloader @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val client: OkHttpClient,
    private val urlBuilder: UrlBuilder,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _items = MutableStateFlow<List<DownloadItem>>(emptyList())
    val items: StateFlow<List<DownloadItem>> = _items.asStateFlow()

    private val _completed = MutableSharedFlow<DownloadItem>(extraBufferCapacity = 8)
    val completed = _completed.asSharedFlow()

    fun enqueue(fileId: String, fileName: String, mime: String) {
        val id = UUID.randomUUID().toString()
        _items.value = _items.value + DownloadItem(id = id, fileId = fileId, fileName = fileName, mime = mime)
        scope.launch { download(id, fileId, fileName, mime) }
    }

    fun clearFinished() {
        _items.value = _items.value.filter { it.status == DownloadStatus.DOWNLOADING }
    }

    private fun update(id: String, transform: (DownloadItem) -> DownloadItem) {
        _items.value = _items.value.map { if (it.id == id) transform(it) else it }
    }

    private fun download(id: String, fileId: String, fileName: String, mime: String) {
        val request = Request.Builder().url(urlBuilder.download(fileId)).build()
        try {
            client.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) throw IOException("HTTP ${resp.code}")
                val body = resp.body ?: throw IOException("Empty body")
                val total = body.contentLength()
                update(id) { it.copy(total = total) }

                val uri = createDownloadEntry(fileName, mime)
                    ?: throw IOException("Could not create download entry")

                context.contentResolver.openOutputStream(uri)?.use { out ->
                    body.byteStream().use { input ->
                        val buf = ByteArray(64 * 1024)
                        var read: Int
                        var written = 0L
                        while (input.read(buf).also { read = it } != -1) {
                            out.write(buf, 0, read)
                            written += read
                            update(id) { it.copy(downloaded = written) }
                        }
                    }
                } ?: throw IOException("Could not open output stream")

                finalizeDownloadEntry(uri)

                val finished = _items.value.first { it.id == id }
                    .copy(status = DownloadStatus.COMPLETED, contentUri = uri)
                update(id) { finished }
                _completed.tryEmit(finished)
            }
        } catch (e: Exception) {
            update(id) { it.copy(status = DownloadStatus.FAILED, error = e.message ?: "Download failed") }
        }
    }

    /** Creates a MediaStore Downloads entry; on Q+ marks it pending while writing. */
    private fun createDownloadEntry(fileName: String, mime: String): Uri? {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, fileName)
            if (mime.isNotBlank()) put(MediaStore.Downloads.MIME_TYPE, mime)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Downloads.IS_PENDING, 1)
            }
        }
        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Downloads.EXTERNAL_CONTENT_URI
        } else {
            MediaStore.Files.getContentUri("external")
        }
        return resolver.insert(collection, values)
    }

    private fun finalizeDownloadEntry(uri: Uri) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return
        val values = ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) }
        context.contentResolver.update(uri, values, null, null)
    }
}
