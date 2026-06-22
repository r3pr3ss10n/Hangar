package eu.r3pr3ss10n.hangar.domain

// Clean domain models the UI and repositories speak in. Mappers in
// data/repository project the wire DTOs into these.

enum class Role { ADMIN, USER;
    companion object {
        fun from(raw: String) = if (raw == "admin") ADMIN else USER
    }
}

data class User(
    val id: String,
    val username: String,
    val role: Role,
) {
    val isAdmin: Boolean get() = role == Role.ADMIN
}

data class Folder(
    val id: String,
    val parentId: String?,
    val name: String,
    val createdAt: String,
)

data class FileItem(
    val id: String,
    val folderId: String?,
    val name: String,
    val size: Long,
    val mime: String,
    val hasThumb: Boolean,
    val createdAt: String,
) {
    val isImage: Boolean get() = mime.lowercase().startsWith("image/")
}

/** One ancestor on a search hit's path, root-first. */
data class PathSegment(val id: String, val name: String)

data class FolderHit(val folder: Folder, val path: List<PathSegment>)
data class FileHit(val file: FileItem, val path: List<PathSegment>)

data class SearchResult(
    val query: String,
    val folders: List<FolderHit>,
    val files: List<FileHit>,
)

data class FolderContents(
    val folders: List<Folder>,
    val files: List<FileItem>,
)

/** A folder/file shared with the current user, tagged with the sharer. */
data class SharedFolder(val folder: Folder, val ownerUsername: String)
data class SharedFile(val file: FileItem, val ownerUsername: String)
data class SharedRoots(val folders: List<SharedFolder>, val files: List<SharedFile>)

data class Tag(
    val id: String,
    val name: String,
    val color: String,
    val itemCount: Long,
)

/** Per-user labels bundle: the tag list plus id→tagIds assignments. */
data class Labels(
    val tags: List<Tag>,
    val fileTags: Map<String, List<String>>,
    val folderTags: Map<String, List<String>>,
)

data class Share(
    val token: String,
    val createdAt: String,
    val expiresAt: String?,
)

data class Grant(
    val recipientId: String,
    val recipientUsername: String,
    val permission: String,
)

data class ShareableUser(val id: String, val username: String)

enum class TelegramStatus { NOT_LINKED, LINKING, LINKED;
    companion object {
        fun from(raw: String) = when (raw) {
            "linked" -> LINKED
            "linking" -> LINKING
            else -> NOT_LINKED
        }
    }
}

data class TelegramState(
    val status: TelegramStatus,
    val isPremium: Boolean,
    val phone: String?,
    val awaitingCode: Boolean,
    val awaitingPassword: Boolean,
)

/** The colour palette tags can use, mirroring the web app. */
val TAG_COLORS = listOf(
    "slate", "red", "orange", "amber", "green", "teal", "blue", "violet", "pink",
)
