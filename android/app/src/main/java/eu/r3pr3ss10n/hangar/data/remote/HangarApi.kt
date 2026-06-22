package eu.r3pr3ss10n.hangar.data.remote

import eu.r3pr3ss10n.hangar.data.remote.dto.AssignTagBody
import eu.r3pr3ss10n.hangar.data.remote.dto.CreateFolderBody
import eu.r3pr3ss10n.hangar.data.remote.dto.CreateGrantBody
import eu.r3pr3ss10n.hangar.data.remote.dto.CreateShareBody
import eu.r3pr3ss10n.hangar.data.remote.dto.CreateUserBody
import eu.r3pr3ss10n.hangar.data.remote.dto.CredentialsBody
import eu.r3pr3ss10n.hangar.data.remote.dto.FileEnvelope
import eu.r3pr3ss10n.hangar.data.remote.dto.FolderEnvelope
import eu.r3pr3ss10n.hangar.data.remote.dto.GrantsEnvelope
import eu.r3pr3ss10n.hangar.data.remote.dto.LabelsDto
import eu.r3pr3ss10n.hangar.data.remote.dto.LinkCancelBody
import eu.r3pr3ss10n.hangar.data.remote.dto.LinkCodeBody
import eu.r3pr3ss10n.hangar.data.remote.dto.LinkIdDto
import eu.r3pr3ss10n.hangar.data.remote.dto.LinkPasswordBody
import eu.r3pr3ss10n.hangar.data.remote.dto.LinkStartBody
import eu.r3pr3ss10n.hangar.data.remote.dto.ListResponseDto
import eu.r3pr3ss10n.hangar.data.remote.dto.NeedPasswordDto
import eu.r3pr3ss10n.hangar.data.remote.dto.PatchFileBody
import eu.r3pr3ss10n.hangar.data.remote.dto.PatchFolderBody
import eu.r3pr3ss10n.hangar.data.remote.dto.SearchResponseDto
import eu.r3pr3ss10n.hangar.data.remote.dto.SetPasswordBody
import eu.r3pr3ss10n.hangar.data.remote.dto.SettingsDto
import eu.r3pr3ss10n.hangar.data.remote.dto.SettingsPatchBody
import eu.r3pr3ss10n.hangar.data.remote.dto.SetupStatusDto
import eu.r3pr3ss10n.hangar.data.remote.dto.ShareDto
import eu.r3pr3ss10n.hangar.data.remote.dto.ShareableUsersEnvelope
import eu.r3pr3ss10n.hangar.data.remote.dto.SharedRootsDto
import eu.r3pr3ss10n.hangar.data.remote.dto.SharesEnvelope
import eu.r3pr3ss10n.hangar.data.remote.dto.StorageDto
import eu.r3pr3ss10n.hangar.data.remote.dto.TagBody
import eu.r3pr3ss10n.hangar.data.remote.dto.TagDto
import eu.r3pr3ss10n.hangar.data.remote.dto.TagsEnvelope
import eu.r3pr3ss10n.hangar.data.remote.dto.TelegramStateDto
import eu.r3pr3ss10n.hangar.data.remote.dto.UserEnvelope
import eu.r3pr3ss10n.hangar.data.remote.dto.UsersEnvelope
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * HangarApi is the typed Retrofit surface over the backend HTTP API. The base
 * URL is host-only and rewritten per request by [ServerUrlInterceptor]; the
 * session cookie is attached by the persistent CookieJar, so no auth headers
 * appear here.
 */
interface HangarApi {

    // ---- setup / auth ----
    @GET("api/setup/status")
    suspend fun setupStatus(): SetupStatusDto

    @POST("api/auth/login")
    suspend fun login(@Body body: CredentialsBody): UserEnvelope

    @POST("api/auth/logout")
    suspend fun logout()

    @GET("api/auth/me")
    suspend fun me(): UserEnvelope

    @GET("api/storage")
    suspend fun storage(): StorageDto

    // ---- settings ----
    @GET("api/settings")
    suspend fun getSettings(): SettingsDto

    @PATCH("api/settings")
    suspend fun patchSettings(@Body body: SettingsPatchBody): SettingsDto

    // ---- folders ----
    @GET("api/folders")
    suspend fun listFolder(@Query("parent_id") parentId: String): ListResponseDto

    @POST("api/folders")
    suspend fun createFolder(@Body body: CreateFolderBody): FolderEnvelope

    @PATCH("api/folders/{id}")
    suspend fun patchFolder(@Path("id") id: String, @Body body: PatchFolderBody)

    // Move uses a raw JSON object so a null parent (= move to root) is sent as an
    // explicit `null`; the typed body would drop it under explicitNulls = false.
    @PATCH("api/folders/{id}")
    suspend fun moveFolder(@Path("id") id: String, @Body body: kotlinx.serialization.json.JsonObject)

    @DELETE("api/folders/{id}")
    suspend fun deleteFolder(@Path("id") id: String)

    // ---- search ----
    @GET("api/search")
    suspend fun search(@Query("q") query: String): SearchResponseDto

    // ---- files (metadata + mutation) ----
    @GET("api/files/{id}/meta")
    suspend fun fileMeta(@Path("id") id: String): FileEnvelope

    @PATCH("api/files/{id}")
    suspend fun patchFile(@Path("id") id: String, @Body body: PatchFileBody)

    // See moveFolder: a raw JSON object preserves an explicit null folder (root).
    @PATCH("api/files/{id}")
    suspend fun moveFile(@Path("id") id: String, @Body body: kotlinx.serialization.json.JsonObject)

    @DELETE("api/files/{id}")
    suspend fun deleteFile(@Path("id") id: String)

    // ---- share links ----
    @GET("api/files/{id}/shares")
    suspend fun listShares(@Path("id") fileId: String): SharesEnvelope

    @POST("api/files/{id}/shares")
    suspend fun createShare(@Path("id") fileId: String, @Body body: CreateShareBody): ShareDto

    @DELETE("api/shares/{token}")
    suspend fun deleteShare(@Path("token") token: String)

    // ---- internal access grants ----
    @GET("api/users/shareable")
    suspend fun listShareableUsers(): ShareableUsersEnvelope

    @GET("api/files/{id}/grants")
    suspend fun listFileGrants(@Path("id") fileId: String): GrantsEnvelope

    @POST("api/files/{id}/grants")
    suspend fun createFileGrant(@Path("id") fileId: String, @Body body: CreateGrantBody): GrantsEnvelope

    @DELETE("api/files/{id}/grants/{recipientId}")
    suspend fun deleteFileGrant(@Path("id") fileId: String, @Path("recipientId") recipientId: String)

    @GET("api/folders/{id}/grants")
    suspend fun listFolderGrants(@Path("id") folderId: String): GrantsEnvelope

    @POST("api/folders/{id}/grants")
    suspend fun createFolderGrant(@Path("id") folderId: String, @Body body: CreateGrantBody): GrantsEnvelope

    @DELETE("api/folders/{id}/grants/{recipientId}")
    suspend fun deleteFolderGrant(@Path("id") folderId: String, @Path("recipientId") recipientId: String)

    // ---- "shared with me" ----
    @GET("api/shared")
    suspend fun sharedRoots(): SharedRootsDto

    @GET("api/shared/folders/{id}")
    suspend fun sharedChildren(@Path("id") folderId: String): ListResponseDto

    // ---- labels: tags ----
    @GET("api/labels")
    suspend fun getLabels(): LabelsDto

    @GET("api/tags")
    suspend fun listTags(): TagsEnvelope

    @POST("api/tags")
    suspend fun createTag(@Body body: TagBody): TagDto

    @PATCH("api/tags/{id}")
    suspend fun updateTag(@Path("id") id: String, @Body body: TagBody)

    @DELETE("api/tags/{id}")
    suspend fun deleteTag(@Path("id") id: String)

    @GET("api/tags/{id}/items")
    suspend fun tagItems(@Path("id") id: String): ListResponseDto

    @POST("api/files/{id}/tags")
    suspend fun addFileTag(@Path("id") id: String, @Body body: AssignTagBody)

    @DELETE("api/files/{id}/tags/{tagId}")
    suspend fun removeFileTag(@Path("id") id: String, @Path("tagId") tagId: String)

    @POST("api/folders/{id}/tags")
    suspend fun addFolderTag(@Path("id") id: String, @Body body: AssignTagBody)

    @DELETE("api/folders/{id}/tags/{tagId}")
    suspend fun removeFolderTag(@Path("id") id: String, @Path("tagId") tagId: String)

    // ---- users (admin) ----
    @GET("api/users")
    suspend fun listUsers(): UsersEnvelope

    @POST("api/users")
    suspend fun createUser(@Body body: CreateUserBody): UserEnvelope

    @DELETE("api/users/{id}")
    suspend fun deleteUser(@Path("id") id: String)

    @POST("api/users/{id}/password")
    suspend fun setUserPassword(@Path("id") id: String, @Body body: SetPasswordBody)

    // ---- telegram (admin) ----
    @GET("api/telegram/status")
    suspend fun telegramStatus(): TelegramStateDto

    @POST("api/telegram/link/start")
    suspend fun telegramLinkStart(@Body body: LinkStartBody): LinkIdDto

    @POST("api/telegram/link/code")
    suspend fun telegramLinkCode(@Body body: LinkCodeBody): NeedPasswordDto

    @POST("api/telegram/link/password")
    suspend fun telegramLinkPassword(@Body body: LinkPasswordBody)

    @POST("api/telegram/link/cancel")
    suspend fun telegramLinkCancel(@Body body: LinkCancelBody)

    @POST("api/telegram/unlink")
    suspend fun telegramUnlink()
}
