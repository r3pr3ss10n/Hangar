package eu.r3pr3ss10n.hangar.data.repository

import eu.r3pr3ss10n.hangar.data.remote.HangarApi
import eu.r3pr3ss10n.hangar.data.remote.dto.AssignTagBody
import eu.r3pr3ss10n.hangar.data.remote.dto.TagBody
import eu.r3pr3ss10n.hangar.domain.FolderContents
import eu.r3pr3ss10n.hangar.domain.Labels
import eu.r3pr3ss10n.hangar.domain.Tag
import javax.inject.Inject
import javax.inject.Singleton

/** TagsRepository covers tag CRUD, the labels bundle, and tag assignment. */
@Singleton
class TagsRepository @Inject constructor(
    private val api: HangarApi,
) {
    suspend fun labels(): Labels = runApi { api.getLabels().toDomain() }

    suspend fun list(): List<Tag> = runApi { api.listTags().tags.map { it.toDomain() } }

    suspend fun create(name: String, color: String): Tag = runApi {
        api.createTag(TagBody(name, color)).toDomain()
    }

    suspend fun update(id: String, name: String, color: String) = runApi {
        api.updateTag(id, TagBody(name, color))
    }

    suspend fun delete(id: String) = runApi { api.deleteTag(id) }

    suspend fun items(id: String): FolderContents = runApi {
        val res = api.tagItems(id)
        FolderContents(
            folders = res.folders.map { it.toDomain() },
            files = res.files.map { it.toDomain() },
        )
    }

    suspend fun setFileTag(fileId: String, tagId: String, on: Boolean) = runApi {
        if (on) api.addFileTag(fileId, AssignTagBody(tagId)) else api.removeFileTag(fileId, tagId)
    }

    suspend fun setFolderTag(folderId: String, tagId: String, on: Boolean) = runApi {
        if (on) api.addFolderTag(folderId, AssignTagBody(tagId)) else api.removeFolderTag(folderId, tagId)
    }
}
