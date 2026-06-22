package eu.r3pr3ss10n.hangar.data.repository

import eu.r3pr3ss10n.hangar.data.remote.HangarApi
import eu.r3pr3ss10n.hangar.data.remote.dto.CreateGrantBody
import eu.r3pr3ss10n.hangar.data.remote.dto.CreateShareBody
import eu.r3pr3ss10n.hangar.domain.Grant
import eu.r3pr3ss10n.hangar.domain.Share
import eu.r3pr3ss10n.hangar.domain.ShareableUser
import eu.r3pr3ss10n.hangar.domain.SharedRoots
import javax.inject.Inject
import javax.inject.Singleton

/**
 * SharingRepository covers public share links, user-to-user grants, and the
 * "shared with me" listings.
 */
@Singleton
class SharingRepository @Inject constructor(
    private val api: HangarApi,
) {
    // ---- public share links ----
    suspend fun listShares(fileId: String): List<Share> = runApi {
        api.listShares(fileId).shares.map { it.toDomain() }
    }

    suspend fun createShare(fileId: String, expiresInSeconds: Long?): Share = runApi {
        api.createShare(fileId, CreateShareBody(expiresInSeconds)).toDomain()
    }

    suspend fun deleteShare(token: String) = runApi { api.deleteShare(token) }

    // ---- user-to-user grants ----
    suspend fun shareableUsers(): List<ShareableUser> = runApi {
        api.listShareableUsers().users.map { it.toDomain() }
    }

    suspend fun fileGrants(fileId: String): List<Grant> = runApi {
        api.listFileGrants(fileId).grants.map { it.toDomain() }
    }

    suspend fun grantFile(fileId: String, recipientId: String): List<Grant> = runApi {
        api.createFileGrant(fileId, CreateGrantBody(recipientId)).grants.map { it.toDomain() }
    }

    suspend fun revokeFileGrant(fileId: String, recipientId: String) = runApi {
        api.deleteFileGrant(fileId, recipientId)
    }

    suspend fun folderGrants(folderId: String): List<Grant> = runApi {
        api.listFolderGrants(folderId).grants.map { it.toDomain() }
    }

    suspend fun grantFolder(folderId: String, recipientId: String): List<Grant> = runApi {
        api.createFolderGrant(folderId, CreateGrantBody(recipientId)).grants.map { it.toDomain() }
    }

    suspend fun revokeFolderGrant(folderId: String, recipientId: String) = runApi {
        api.deleteFolderGrant(folderId, recipientId)
    }

    // ---- shared with me ----
    suspend fun sharedRoots(): SharedRoots = runApi {
        val res = api.sharedRoots()
        SharedRoots(
            folders = res.folders.map { it.toDomain() },
            files = res.files.map { it.toDomain() },
        )
    }

    suspend fun sharedChildren(folderId: String) = runApi {
        val res = api.sharedChildren(folderId)
        eu.r3pr3ss10n.hangar.domain.FolderContents(
            folders = res.folders.map { it.toDomain() },
            files = res.files.map { it.toDomain() },
        )
    }
}
