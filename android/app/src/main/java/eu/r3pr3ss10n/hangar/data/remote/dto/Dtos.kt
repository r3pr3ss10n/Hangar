package eu.r3pr3ss10n.hangar.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// DTOs mirror the backend's JSON views (backend/internal/api). Field names match
// the Go json tags exactly; only fields the app reads are declared.

@Serializable
data class UserDto(
    val id: String,
    val username: String,
    val role: String,
    @SerialName("created_at") val createdAt: String,
)

@Serializable
data class FolderDto(
    val id: String,
    @SerialName("owner_id") val ownerId: String,
    @SerialName("parent_id") val parentId: String? = null,
    val name: String,
    @SerialName("created_at") val createdAt: String,
)

@Serializable
data class FileDto(
    val id: String,
    @SerialName("owner_id") val ownerId: String,
    @SerialName("folder_id") val folderId: String? = null,
    val name: String,
    val size: Long,
    val mime: String,
    val sha256: String,
    @SerialName("has_thumb") val hasThumb: Boolean,
    @SerialName("created_at") val createdAt: String,
)

@Serializable
data class PathSegmentDto(
    val id: String,
    val name: String,
)

// Search hits embed the resource fields plus a path. The backend promotes the
// folder/file fields to the top level, so the hit DTOs repeat them.
@Serializable
data class FolderHitDto(
    val id: String,
    @SerialName("owner_id") val ownerId: String,
    @SerialName("parent_id") val parentId: String? = null,
    val name: String,
    @SerialName("created_at") val createdAt: String,
    val path: List<PathSegmentDto> = emptyList(),
)

@Serializable
data class FileHitDto(
    val id: String,
    @SerialName("owner_id") val ownerId: String,
    @SerialName("folder_id") val folderId: String? = null,
    val name: String,
    val size: Long,
    val mime: String,
    val sha256: String,
    @SerialName("has_thumb") val hasThumb: Boolean,
    @SerialName("created_at") val createdAt: String,
    val path: List<PathSegmentDto> = emptyList(),
)

// "Shared with me" rows add the owner's username.
@Serializable
data class SharedFolderDto(
    val id: String,
    @SerialName("owner_id") val ownerId: String,
    @SerialName("parent_id") val parentId: String? = null,
    val name: String,
    @SerialName("created_at") val createdAt: String,
    @SerialName("owner_username") val ownerUsername: String,
)

@Serializable
data class SharedFileDto(
    val id: String,
    @SerialName("owner_id") val ownerId: String,
    @SerialName("folder_id") val folderId: String? = null,
    val name: String,
    val size: Long,
    val mime: String,
    val sha256: String,
    @SerialName("has_thumb") val hasThumb: Boolean,
    @SerialName("created_at") val createdAt: String,
    @SerialName("owner_username") val ownerUsername: String,
)

@Serializable
data class TagDto(
    val id: String,
    val name: String,
    val color: String,
    @SerialName("item_count") val itemCount: Long = 0,
)

@Serializable
data class ShareDto(
    val token: String,
    @SerialName("created_at") val createdAt: String,
    @SerialName("expires_at") val expiresAt: String? = null,
)

@Serializable
data class GrantDto(
    @SerialName("recipient_id") val recipientId: String,
    @SerialName("recipient_username") val recipientUsername: String,
    val permission: String,
    @SerialName("created_at") val createdAt: String,
)

@Serializable
data class ShareableUserDto(
    val id: String,
    val username: String,
)

@Serializable
data class TelegramStateDto(
    val status: String,
    @SerialName("is_premium") val isPremium: Boolean = false,
    val phone: String? = null,
    @SerialName("awaiting_code") val awaitingCode: Boolean = false,
    @SerialName("awaiting_password") val awaitingPassword: Boolean = false,
)
