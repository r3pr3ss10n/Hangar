package eu.r3pr3ss10n.hangar.data.upload

import android.net.Uri

/** State of a single upload in the queue. */
enum class UploadStatus { PENDING, UPLOADING, COMPLETED, FAILED }

/**
 * One queued/active upload. [progress] is 0..1 (or indeterminate for unknown
 * size). [folderId] is the destination (null = root). [error] carries the
 * failure reason when [status] is FAILED.
 */
data class UploadItem(
    val id: String,
    val uri: Uri,
    val name: String,
    val size: Long,
    val mime: String,
    val folderId: String?,
    val status: UploadStatus = UploadStatus.PENDING,
    val uploadedBytes: Long = 0,
    val error: String? = null,
) {
    val progress: Float
        get() = if (size > 0) (uploadedBytes.toFloat() / size).coerceIn(0f, 1f) else 0f
}
