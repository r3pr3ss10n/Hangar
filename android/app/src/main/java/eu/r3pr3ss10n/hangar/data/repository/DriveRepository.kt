package eu.r3pr3ss10n.hangar.data.repository

import eu.r3pr3ss10n.hangar.data.remote.HangarApi
import eu.r3pr3ss10n.hangar.data.remote.dto.CreateFolderBody
import eu.r3pr3ss10n.hangar.data.remote.dto.PatchFileBody
import eu.r3pr3ss10n.hangar.data.remote.dto.PatchFolderBody
import eu.r3pr3ss10n.hangar.domain.FileItem
import eu.r3pr3ss10n.hangar.domain.Folder
import eu.r3pr3ss10n.hangar.domain.FolderContents
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import javax.inject.Inject
import javax.inject.Singleton

/**
 * DriveRepository covers folder browsing and folder/file mutations (create,
 * rename, move, delete). "root" is the sentinel the backend uses for the owner's
 * top-level folder; a null id maps to it.
 */
@Singleton
class DriveRepository @Inject constructor(
    private val api: HangarApi,
) {
    suspend fun list(parentId: String?): FolderContents = runApi {
        val res = api.listFolder(parentId ?: "root")
        FolderContents(
            folders = res.folders.map { it.toDomain() },
            files = res.files.map { it.toDomain() },
        )
    }

    suspend fun createFolder(name: String, parentId: String?): Folder = runApi {
        api.createFolder(CreateFolderBody(name = name, parentId = parentId)).folder.toDomain()
    }

    suspend fun renameFolder(id: String, name: String) = runApi {
        api.patchFolder(id, PatchFolderBody(name = name))
    }

    suspend fun moveFolder(id: String, parentId: String?) = runApi {
        // Explicit JSON null = move to root (see HangarApi.moveFolder).
        api.moveFolder(id, buildJsonObject { put("parent_id", JsonPrimitive(parentId)) })
    }

    suspend fun deleteFolder(id: String) = runApi { api.deleteFolder(id) }

    suspend fun fileMeta(id: String): FileItem = runApi { api.fileMeta(id).file.toDomain() }

    suspend fun renameFile(id: String, name: String) = runApi {
        api.patchFile(id, PatchFileBody(name = name))
    }

    suspend fun moveFile(id: String, folderId: String?) = runApi {
        // Explicit JSON null = move to root (see HangarApi.moveFile).
        api.moveFile(id, buildJsonObject { put("folder_id", JsonPrimitive(folderId)) })
    }

    suspend fun deleteFile(id: String) = runApi { api.deleteFile(id) }
}
