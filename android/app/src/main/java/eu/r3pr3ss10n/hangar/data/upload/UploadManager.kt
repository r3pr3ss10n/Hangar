package eu.r3pr3ss10n.hangar.data.upload

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Base64
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.r3pr3ss10n.hangar.data.remote.HangarApi
import eu.r3pr3ss10n.hangar.data.remote.ServerUrlProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.Call
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okio.BufferedSink
import okio.source
import java.io.InputStream
import java.util.Collections
import java.util.UUID
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton

/**
 * UploadManager uploads files to the backend over the resumable tus endpoint
 * (POST /api/uploads/), splitting each file across several parallel connections
 * via the tus concatenation extension: N "partial" uploads sent concurrently,
 * then one "final" upload that concatenates them. This defeats the per-TCP-flow
 * throughput throttle on the path to the server, mirroring the web client.
 *
 * It runs over the shared OkHttp client, so the session cookie (attached by the
 * persistent CookieJar), the dynamic server origin ([ServerUrlInterceptor]), and
 * reverse-proxy handling all work the same as every other authenticated call —
 * no manual auth headers needed. The protocol is hand-rolled on OkHttp rather
 * than via tus-java-client because that library exposes no concatenation /
 * parallel-upload support and cannot surface the final response header we read.
 *
 * It keeps an observable queue, uploads files sequentially (each fanned out over
 * parallel connections), and emits a completion event so the visible drive
 * screen can refresh.
 */
@Singleton
class UploadManager @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val serverUrl: ServerUrlProvider,
    private val client: OkHttpClient,
    private val api: HangarApi,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val queueLock = Mutex()
    private var running = false

    // Every HTTP call in flight for the current file (create + parallel PATCHes),
    // so an active upload can be aborted on cancel.
    private val activeCalls: MutableSet<Call> = Collections.synchronizedSet(mutableSetOf())

    private val _items = MutableStateFlow<List<UploadItem>>(emptyList())
    val items: StateFlow<List<UploadItem>> = _items.asStateFlow()

    private val _completed = MutableSharedFlow<String?>(extraBufferCapacity = 16)
    val completed = _completed.asSharedFlow()

    private val itemsLock = Any()

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
        synchronized(itemsLock) { _items.value = _items.value + newItems }
        ensureRunning()
    }

    fun clearFinished() {
        synchronized(itemsLock) {
            _items.value = _items.value.filter {
                it.status == UploadStatus.PENDING || it.status == UploadStatus.UPLOADING
            }
        }
    }

    /**
     * Cancels everything still in flight: aborts every active HTTP call and drops
     * all pending/uploading items, leaving finished entries for reference. The
     * aborted calls throw, but their item is already gone so it just vanishes
     * rather than showing as failed.
     */
    fun cancelAll() {
        synchronized(activeCalls) { activeCalls.toList() }.forEach { it.cancel() }
        synchronized(itemsLock) {
            _items.value = _items.value.filter {
                it.status != UploadStatus.PENDING && it.status != UploadStatus.UPLOADING
            }
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

    private suspend fun runOne(item: UploadItem) {
        update(item.id) { it.copy(status = UploadStatus.UPLOADING, uploadedBytes = 0) }
        val uploaded = AtomicLong(0)
        try {
            val base = serverUrl.baseUrl ?: error("No server connected")
            val n = parallelConnections(item.size)

            // The tus concatenation flow needs a fingerprint-free run: upload N
            // partials in parallel, then create the final concat upload whose
            // creation completes it server-side and returns the new file id.
            val fileId = if (n <= 1) {
                uploadPlain(base, item, uploaded)
            } else {
                uploadParallel(base, item, n, uploaded)
            }

            // Mirror the web client: resolve the created file row by the id the
            // backend returned once the assembled file landed in Telegram. This
            // also confirms the upload truly succeeded before we report COMPLETED.
            api.fileMeta(fileId)

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
        }
    }

    /**
     * Plain (single-connection) tus upload, used for files too small to bother
     * splitting. Creates the upload with its length and metadata, streams the
     * whole body in one PATCH, and reads the new file id off the PATCH response
     * (the completion hook fires when the last byte lands).
     */
    private fun uploadPlain(base: HttpUrl, item: UploadItem, uploaded: AtomicLong): String {
        val location = createUpload(
            base = base,
            uploadLength = item.size,
            metadata = metadataHeader(item),
            concat = null,
        )
        val patchResp = patch(location, offset = 0, item = item, start = 0, length = item.size, uploaded = uploaded)
        return patchResp.fileId ?: error(NO_ID_ERROR)
    }

    /**
     * Parallel (concatenation) tus upload. Splits the file into [n] contiguous
     * byte ranges, creates+streams each as a partial upload concurrently, then
     * creates the final concat upload over those partial URLs. The final
     * creation completes the upload server-side, so its response carries the new
     * file id.
     */
    private suspend fun uploadParallel(
        base: HttpUrl,
        item: UploadItem,
        n: Int,
        uploaded: AtomicLong,
    ): String {
        val ranges = partition(item.size, n)
        val locations = coroutineScope {
            ranges.map { range ->
                async(Dispatchers.IO) {
                    val location = createUpload(
                        base = base,
                        uploadLength = range.length,
                        metadata = null, // partials carry no user metadata; the final does
                        concat = "partial",
                    )
                    patch(location, offset = 0, item = item, start = range.start, length = range.length, uploaded = uploaded)
                    location
                }
            }.awaitAll()
        }

        val concat = "final;" + locations.joinToString(" ")
        val finalLocation = createUpload(
            base = base,
            uploadLength = null, // forbidden on a final concat upload
            metadata = metadataHeader(item),
            concat = concat,
        )
        // The id rides the create response for a concat-final upload (it completes
        // at creation). Some setups surface it on a follow-up HEAD instead, so
        // fall back to that if the create response had no header.
        return lastFinalFileId ?: headFileId(finalLocation) ?: error(NO_ID_ERROR)
    }

    // Carries the Hangar-File-Id observed on the most recent final-concat create,
    // set inside createUpload (single-threaded here: only runOne creates finals).
    @Volatile
    private var lastFinalFileId: String? = null

    /**
     * Issues a tus creation POST and returns the upload's resource URL (Location).
     * For a final concat upload the backend completes the upload during creation
     * and stamps Hangar-File-Id on the response; that value is captured into
     * [lastFinalFileId].
     */
    private fun createUpload(
        base: HttpUrl,
        uploadLength: Long?,
        metadata: String?,
        concat: String?,
    ): HttpUrl {
        val url = base.newBuilder().addPathSegments("api/uploads/").build()
        val builder = Request.Builder()
            .url(url)
            .header("Tus-Resumable", TUS_VERSION)
            .post(EMPTY_BODY)
        uploadLength?.let { builder.header("Upload-Length", it.toString()) }
        metadata?.let { builder.header("Upload-Metadata", it) }
        concat?.let { builder.header("Upload-Concat", it) }

        val resp = run(builder.build())
        resp.use {
            if (!it.isSuccessful) error(errorBody(it))
            if (concat?.startsWith("final") == true) {
                lastFinalFileId = it.header(HANGAR_FILE_ID)
            }
            val location = it.header("Location") ?: error("tus create returned no Location")
            return location.toHttpUrlOrNull()
                ?: base.resolve(location)
                ?: error("tus create returned an unusable Location: $location")
        }
    }

    /** Result of a PATCH: any file id the backend attached to the response. */
    private class PatchResult(val fileId: String?)

    /**
     * Streams [length] bytes of [item], starting at byte [start], into the tus
     * upload at [location] in a single PATCH at [offset]. Progress is reported by
     * adding each written slice to [uploaded] (shared across parallel partials).
     */
    private fun patch(
        location: HttpUrl,
        offset: Long,
        item: UploadItem,
        start: Long,
        length: Long,
        uploaded: AtomicLong,
    ): PatchResult {
        val body = RangedRequestBody(item, start, length) { delta ->
            val total = uploaded.addAndGet(delta)
            update(item.id) { it.copy(uploadedBytes = total) }
        }
        val request = Request.Builder()
            .url(location)
            .header("Tus-Resumable", TUS_VERSION)
            .header("Upload-Offset", offset.toString())
            .patch(body)
            .build()
        val resp = run(request)
        resp.use {
            if (!it.isSuccessful) error(errorBody(it))
            return PatchResult(it.header(HANGAR_FILE_ID))
        }
    }

    /** HEAD an upload to read any Hangar-File-Id header (concat fallback). */
    private fun headFileId(location: HttpUrl): String? {
        val request = Request.Builder()
            .url(location)
            .header("Tus-Resumable", TUS_VERSION)
            .head()
            .build()
        return run(request).use { it.header(HANGAR_FILE_ID) }
    }

    /** Executes [request] on the shared client, tracking it so cancel can abort. */
    private fun run(request: Request): okhttp3.Response {
        val call = client.newCall(request)
        activeCalls.add(call)
        return try {
            call.execute()
        } finally {
            activeCalls.remove(call)
        }
    }

    private fun errorBody(resp: okhttp3.Response): String =
        resp.body?.string()?.takeIf { it.isNotBlank() } ?: "HTTP ${resp.code}"

    /** tus Upload-Metadata: comma-separated "key base64(value)" pairs. */
    private fun metadataHeader(item: UploadItem): String {
        val mime = item.mime.ifBlank { "application/octet-stream" }
        val folder = item.folderId ?: "root"
        return listOf(
            "filename" to item.name,
            "filetype" to mime,
            "folder_id" to folder,
        ).joinToString(",") { (k, v) ->
            "$k " + Base64.encodeToString(v.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
        }
    }

    /** Contiguous byte range of the source file assigned to one partial upload. */
    private data class Range(val start: Long, val length: Long)

    /** Splits [size] into [n] contiguous ranges; the last absorbs the remainder. */
    private fun partition(size: Long, n: Int): List<Range> {
        val base = size / n
        return (0 until n).map { i ->
            val start = base * i
            val length = if (i == n - 1) size - start else base
            Range(start, length)
        }
    }

    /**
     * How many parallel connections to split a file across. Matches the web
     * client's ceiling of 8, but never more partials than there are bytes (a tiny
     * file falls back to a single plain upload).
     */
    private fun parallelConnections(size: Long): Int {
        if (size <= 0) return 1
        return minOf(PARALLEL_CONNECTIONS.toLong(), size).toInt()
    }

    private fun update(id: String, transform: (UploadItem) -> UploadItem) {
        synchronized(itemsLock) {
            _items.value = _items.value.map { if (it.id == id) transform(it) else it }
        }
    }

    /**
     * A request body that streams a byte range [start, start+length) of a content
     * Uri as the tus PATCH payload. Content-Length is known so OkHttp sends a
     * single non-chunked PATCH per partial — a continuous stream per connection,
     * avoiding the inter-chunk round-trip stalls that throttle a chunked flow.
     */
    private inner class RangedRequestBody(
        private val item: UploadItem,
        private val start: Long,
        private val length: Long,
        private val onWritten: (Long) -> Unit,
    ) : RequestBody() {
        override fun contentType() = OFFSET_OCTET_STREAM

        override fun contentLength(): Long = length

        override fun writeTo(sink: BufferedSink) {
            val input = context.contentResolver.openInputStream(item.uri)
                ?: error("Cannot open ${item.name}")
            input.use { stream ->
                skipFully(stream, start)
                val src = stream.source()
                val buf = okio.Buffer()
                var remaining = length
                while (remaining > 0) {
                    val want = minOf(STREAM_BUFFER, remaining)
                    val read = src.read(buf, want)
                    if (read == -1L) break
                    sink.write(buf, read)
                    remaining -= read
                    onWritten(read)
                }
            }
        }
    }

    /** Skips exactly [count] bytes, since InputStream.skip may stop short. */
    private fun skipFully(stream: InputStream, count: Long) {
        var remaining = count
        while (remaining > 0) {
            val skipped = stream.skip(remaining)
            if (skipped > 0) {
                remaining -= skipped
                continue
            }
            // skip() can legitimately return 0 before EOF; fall back to reading.
            if (stream.read() == -1) break
            remaining -= 1
        }
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

    companion object {
        private const val TUS_VERSION = "1.0.0"
        private const val HANGAR_FILE_ID = "Hangar-File-Id"

        // Split each file across this many parallel connections (web client parity).
        private const val PARALLEL_CONNECTIONS = 8

        // 256 KiB read/write granularity for progress + flow control.
        private const val STREAM_BUFFER = 256L * 1024

        private const val NO_ID_ERROR = "Upload completed but no file id was returned"

        private val OFFSET_OCTET_STREAM = "application/offset+octet-stream".toMediaType()
        private val EMPTY_BODY: RequestBody = ByteArray(0).toRequestBody(null)
    }
}
