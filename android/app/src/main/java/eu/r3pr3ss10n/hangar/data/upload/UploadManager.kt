package eu.r3pr3ss10n.hangar.data.upload

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.r3pr3ss10n.hangar.data.remote.ServerUrlProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.Call
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okio.BufferedSink
import okio.source
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * UploadManager streams uploads to the backend via the plain POST /api/files
 * path — the same route the web client uses — over the shared OkHttp client, so
 * the session cookie and any reverse-proxy redirects are handled normally. It
 * keeps an observable queue, uploads sequentially on a background scope, and
 * emits a completion event so the visible drive screen can refresh.
 *
 * (An earlier version used the resumable tus protocol, but tus runs over
 * HttpURLConnection and broke behind the TLS-terminating proxy with cross-scheme
 * 301s. The streaming POST is simpler and robust; resumability can return later
 * via a tus client layered on OkHttp.)
 */
@Singleton
class UploadManager @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val serverUrl: ServerUrlProvider,
    private val client: OkHttpClient,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val queueLock = Mutex()
    private var running = false

    // The in-flight HTTP call, so an active upload can be aborted on cancel.
    @Volatile
    private var currentCall: Call? = null

    private val _items = MutableStateFlow<List<UploadItem>>(emptyList())
    val items: StateFlow<List<UploadItem>> = _items.asStateFlow()

    private val _completed = MutableSharedFlow<String?>(extraBufferCapacity = 16)
    val completed = _completed.asSharedFlow()

    val hasActiveWork: Boolean
        get() = _items.value.any { it.status == UploadStatus.PENDING || it.status == UploadStatus.UPLOADING }

    fun enqueue(uris: List<Uri>, folderId: String?) {
        val newItems = uris.map { uri ->
            val (name, size, mime) = probe(uri)
            UploadItem(
                id = UUID.randomUUID().toString(),
                uri = uri,
                name = name,
                size = size,
                mime = mime,
                folderId = folderId,
            )
        }
        _items.value = _items.value + newItems
        ensureRunning()
    }

    fun clearFinished() {
        _items.value = _items.value.filter {
            it.status == UploadStatus.PENDING || it.status == UploadStatus.UPLOADING
        }
    }

    /**
     * Cancels everything still in flight: aborts the active HTTP call and drops
     * all pending/uploading items, leaving finished entries for reference. The
     * aborted upload's call throws, but its item is already gone so it just
     * vanishes rather than showing as failed.
     */
    fun cancelAll() {
        currentCall?.cancel()
        _items.value = _items.value.filter {
            it.status != UploadStatus.PENDING && it.status != UploadStatus.UPLOADING
        }
    }

    private fun ensureRunning() {
        scope.launch {
            queueLock.withLock {
                if (running) return@withLock
                running = true
            }
            try {
                while (true) {
                    val next = _items.value.firstOrNull { it.status == UploadStatus.PENDING } ?: break
                    runOne(next)
                }
            } finally {
                queueLock.withLock { running = false }
            }
        }
    }

    private fun runOne(item: UploadItem) {
        update(item.id) { it.copy(status = UploadStatus.UPLOADING) }
        try {
            val base = serverUrl.baseUrl ?: error("No server connected")
            val url = base.newBuilder()
                .addPathSegments("api/files")
                .apply { item.folderId?.let { addQueryParameter("folder_id", it) } }
                .build()

            val body = object : RequestBody() {
                override fun contentType() =
                    (item.mime.ifBlank { "application/octet-stream" }).toMediaTypeOrNull()

                // Known length so OkHttp sets Content-Length, which the backend requires.
                override fun contentLength(): Long = item.size

                override fun writeTo(sink: BufferedSink) {
                    val input = context.contentResolver.openInputStream(item.uri)
                        ?: error("Cannot open ${item.name}")
                    input.use { stream ->
                        val src = stream.source()
                        val buf = okio.Buffer()
                        var uploaded = 0L
                        while (true) {
                            val read = src.read(buf, 256 * 1024)
                            if (read == -1L) break
                            sink.write(buf, read)
                            uploaded += read
                            update(item.id) { it.copy(uploadedBytes = uploaded) }
                        }
                    }
                }
            }

            val request = Request.Builder()
                .url(url)
                .header("X-Upload-Filename", headerSafeFilename(item.name))
                .post(body)
                .build()

            val call = client.newCall(request)
            currentCall = call
            call.execute().use { resp ->
                if (!resp.isSuccessful) {
                    val msg = resp.body?.string()?.takeIf { it.isNotBlank() } ?: "HTTP ${resp.code}"
                    error(msg)
                }
            }

            update(item.id) {
                it.copy(uploadedBytes = item.size, status = UploadStatus.COMPLETED)
            }
            _completed.tryEmit(item.folderId)
        } catch (e: Exception) {
            // A cancelled upload was already removed from the queue; update() then
            // no-ops, so it disappears instead of being marked failed.
            update(item.id) {
                it.copy(status = UploadStatus.FAILED, error = e.message ?: e.javaClass.simpleName)
            }
        } finally {
            currentCall = null
        }
    }

    private fun update(id: String, transform: (UploadItem) -> UploadItem) {
        _items.value = _items.value.map { if (it.id == id) transform(it) else it }
    }

    /**
     * HTTP headers must be ASCII. Non-ASCII filenames are percent-encoded so the
     * request is well-formed; ASCII names pass through unchanged.
     */
    private fun headerSafeFilename(name: String): String {
        if (name.all { it.code in 0x20..0x7e }) return name
        return java.net.URLEncoder.encode(name, "UTF-8").replace("+", "%20")
    }

    /** Resolves a content Uri's display name, size, and MIME type. */
    private fun probe(uri: Uri): Triple<String, Long, String> {
        var name = "upload"
        var size = 0L
        val resolver = context.contentResolver
        resolver.query(uri, null, null, null, null)?.use { c ->
            val nameIdx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            val sizeIdx = c.getColumnIndex(OpenableColumns.SIZE)
            if (c.moveToFirst()) {
                if (nameIdx >= 0) c.getString(nameIdx)?.let { name = it }
                if (sizeIdx >= 0 && !c.isNull(sizeIdx)) size = c.getLong(sizeIdx)
            }
        }
        val mime = resolver.getType(uri) ?: "application/octet-stream"
        return Triple(name, size, mime)
    }
}
