package eu.r3pr3ss10n.hangar.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// Response envelopes and request bodies matching the backend's JSON shapes.

@Serializable
data class SetupStatusDto(@SerialName("needs_setup") val needsSetup: Boolean)

@Serializable
data class UserEnvelope(val user: UserDto)

@Serializable
data class StorageDto(@SerialName("used_bytes") val usedBytes: Long)

@Serializable
data class SettingsDto(@SerialName("generate_thumbnails") val generateThumbnails: Boolean)

@Serializable
data class ListResponseDto(
    val folders: List<FolderDto> = emptyList(),
    val files: List<FileDto> = emptyList(),
)

@Serializable
data class SearchResponseDto(
    val query: String = "",
    val folders: List<FolderHitDto> = emptyList(),
    val files: List<FileHitDto> = emptyList(),
)

@Serializable
data class FileEnvelope(val file: FileDto)

@Serializable
data class FolderEnvelope(val folder: FolderDto)

@Serializable
data class SharedRootsDto(
    val folders: List<SharedFolderDto> = emptyList(),
    val files: List<SharedFileDto> = emptyList(),
)

@Serializable
data class TagsEnvelope(val tags: List<TagDto> = emptyList())

@Serializable
data class LabelsDto(
    val tags: List<TagDto> = emptyList(),
    @SerialName("file_tags") val fileTags: Map<String, List<String>> = emptyMap(),
    @SerialName("folder_tags") val folderTags: Map<String, List<String>> = emptyMap(),
)

@Serializable
data class SharesEnvelope(val shares: List<ShareDto> = emptyList())

@Serializable
data class GrantsEnvelope(val grants: List<GrantDto> = emptyList())

@Serializable
data class ShareableUsersEnvelope(val users: List<ShareableUserDto> = emptyList())

@Serializable
data class UsersEnvelope(val users: List<UserDto> = emptyList())

@Serializable
data class LinkIdDto(@SerialName("link_id") val linkId: String)

@Serializable
data class NeedPasswordDto(@SerialName("need_password") val needPassword: Boolean)

@Serializable
data class ErrorDto(val error: String? = null)

// ---- request bodies ----

@Serializable
data class CredentialsBody(val username: String, val password: String)

@Serializable
data class CreateFolderBody(
    val name: String,
    @SerialName("parent_id") val parentId: String? = null,
)

@Serializable
data class PatchFolderBody(
    val name: String? = null,
    @SerialName("parent_id") val parentId: String? = null,
)

@Serializable
data class PatchFileBody(
    val name: String? = null,
    @SerialName("folder_id") val folderId: String? = null,
)

@Serializable
data class SettingsPatchBody(
    @SerialName("generate_thumbnails") val generateThumbnails: Boolean? = null,
)

@Serializable
data class CreateShareBody(
    @SerialName("expires_in_seconds") val expiresInSeconds: Long? = null,
)

@Serializable
data class TagBody(val name: String, val color: String)

@Serializable
data class AssignTagBody(@SerialName("tag_id") val tagId: String)

@Serializable
data class CreateGrantBody(
    @SerialName("recipient_id") val recipientId: String,
    val permission: String = "view",
)

@Serializable
data class CreateUserBody(
    val username: String,
    val password: String,
    val role: String,
)

@Serializable
data class SetPasswordBody(val password: String)

@Serializable
data class LinkStartBody(val phone: String)

@Serializable
data class LinkCodeBody(
    @SerialName("link_id") val linkId: String,
    val code: String,
)

@Serializable
data class LinkPasswordBody(
    @SerialName("link_id") val linkId: String,
    val password: String,
)

@Serializable
data class LinkCancelBody(@SerialName("link_id") val linkId: String)
