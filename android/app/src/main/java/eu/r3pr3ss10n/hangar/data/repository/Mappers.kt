package eu.r3pr3ss10n.hangar.data.repository

import eu.r3pr3ss10n.hangar.data.remote.dto.FileDto
import eu.r3pr3ss10n.hangar.data.remote.dto.FileHitDto
import eu.r3pr3ss10n.hangar.data.remote.dto.FolderDto
import eu.r3pr3ss10n.hangar.data.remote.dto.FolderHitDto
import eu.r3pr3ss10n.hangar.data.remote.dto.GrantDto
import eu.r3pr3ss10n.hangar.data.remote.dto.LabelsDto
import eu.r3pr3ss10n.hangar.data.remote.dto.PathSegmentDto
import eu.r3pr3ss10n.hangar.data.remote.dto.MyShareDto
import eu.r3pr3ss10n.hangar.data.remote.dto.ShareDto
import eu.r3pr3ss10n.hangar.data.remote.dto.ShareableUserDto
import eu.r3pr3ss10n.hangar.data.remote.dto.SharedFileDto
import eu.r3pr3ss10n.hangar.data.remote.dto.SharedFolderDto
import eu.r3pr3ss10n.hangar.data.remote.dto.TagDto
import eu.r3pr3ss10n.hangar.data.remote.dto.TelegramStateDto
import eu.r3pr3ss10n.hangar.data.remote.dto.UserDto
import eu.r3pr3ss10n.hangar.domain.FileHit
import eu.r3pr3ss10n.hangar.domain.FileItem
import eu.r3pr3ss10n.hangar.domain.Folder
import eu.r3pr3ss10n.hangar.domain.FolderHit
import eu.r3pr3ss10n.hangar.domain.Grant
import eu.r3pr3ss10n.hangar.domain.Labels
import eu.r3pr3ss10n.hangar.domain.MyShare
import eu.r3pr3ss10n.hangar.domain.PathSegment
import eu.r3pr3ss10n.hangar.domain.Role
import eu.r3pr3ss10n.hangar.domain.Share
import eu.r3pr3ss10n.hangar.domain.ShareableUser
import eu.r3pr3ss10n.hangar.domain.SharedFile
import eu.r3pr3ss10n.hangar.domain.SharedFolder
import eu.r3pr3ss10n.hangar.domain.Tag
import eu.r3pr3ss10n.hangar.domain.TelegramState
import eu.r3pr3ss10n.hangar.domain.TelegramStatus
import eu.r3pr3ss10n.hangar.domain.User

// DTO -> domain mappers, kept in one place so the wire shape stays isolated to
// the data layer.

fun UserDto.toDomain() = User(id = id, username = username, role = Role.from(role))

fun FolderDto.toDomain() = Folder(id = id, parentId = parentId, name = name, createdAt = createdAt)

fun FileDto.toDomain() = FileItem(
    id = id, folderId = folderId, name = name, size = size, mime = mime,
    hasThumb = hasThumb, createdAt = createdAt,
)

fun PathSegmentDto.toDomain() = PathSegment(id = id, name = name)

fun FolderHitDto.toDomain() = FolderHit(
    folder = Folder(id = id, parentId = parentId, name = name, createdAt = createdAt),
    path = path.map { it.toDomain() },
)

fun FileHitDto.toDomain() = FileHit(
    file = FileItem(
        id = id, folderId = folderId, name = name, size = size, mime = mime,
        hasThumb = hasThumb, createdAt = createdAt,
    ),
    path = path.map { it.toDomain() },
)

fun SharedFolderDto.toDomain() = SharedFolder(
    folder = Folder(id = id, parentId = parentId, name = name, createdAt = createdAt),
    ownerUsername = ownerUsername,
)

fun SharedFileDto.toDomain() = SharedFile(
    file = FileItem(
        id = id, folderId = folderId, name = name, size = size, mime = mime,
        hasThumb = hasThumb, createdAt = createdAt,
    ),
    ownerUsername = ownerUsername,
)

fun TagDto.toDomain() = Tag(id = id, name = name, color = color, itemCount = itemCount)

fun LabelsDto.toDomain() = Labels(
    tags = tags.map { it.toDomain() },
    fileTags = fileTags,
    folderTags = folderTags,
)

fun ShareDto.toDomain() = Share(token = token, createdAt = createdAt, expiresAt = expiresAt)

fun MyShareDto.toDomain() = MyShare(
    file = file.toDomain(),
    token = token,
    createdAt = createdAt,
    expiresAt = expiresAt,
)

fun GrantDto.toDomain() = Grant(
    recipientId = recipientId, recipientUsername = recipientUsername, permission = permission,
)

fun ShareableUserDto.toDomain() = ShareableUser(id = id, username = username)

fun TelegramStateDto.toDomain() = TelegramState(
    status = TelegramStatus.from(status),
    isPremium = isPremium,
    phone = phone,
    awaitingCode = awaitingCode,
    awaitingPassword = awaitingPassword,
)
