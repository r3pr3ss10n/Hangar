package eu.r3pr3ss10n.hangar.data.remote

import eu.r3pr3ss10n.hangar.data.remote.dto.FileEnvelope
import eu.r3pr3ss10n.hangar.data.remote.dto.ListResponseDto
import eu.r3pr3ss10n.hangar.data.remote.dto.SearchResponseDto
import eu.r3pr3ss10n.hangar.data.remote.dto.SetupStatusDto
import eu.r3pr3ss10n.hangar.data.remote.dto.TelegramStateDto
import eu.r3pr3ss10n.hangar.data.remote.dto.UserEnvelope
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins the DTOs to the backend's JSON wire shapes (internal/api views). If the
 * backend contract changes these break, which is the point.
 */
class DtoSerializationTest {

    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }

    @Test
    fun parsesUserEnvelope() {
        val body = """
            {"user":{"id":"u1","username":"alice","role":"admin","created_at":"2026-06-22T17:08:00Z"}}
        """.trimIndent()
        val env = json.decodeFromString<UserEnvelope>(body)
        assertEquals("alice", env.user.username)
        assertEquals("admin", env.user.role)
    }

    @Test
    fun parsesFileEnvelopeWithSnakeCaseAndNullFolder() {
        val body = """
            {"file":{"id":"f1","owner_id":"u1","folder_id":null,"name":"photo.jpg","size":2048,
            "mime":"image/jpeg","sha256":"abc","has_thumb":true,"created_at":"2026-06-22T00:00:00Z"}}
        """.trimIndent()
        val env = json.decodeFromString<FileEnvelope>(body)
        assertEquals("photo.jpg", env.file.name)
        assertEquals(2048L, env.file.size)
        assertTrue(env.file.hasThumb)
        assertNull(env.file.folderId)
    }

    @Test
    fun parsesFolderListing() {
        val body = """
            {"folders":[{"id":"d1","owner_id":"u1","parent_id":null,"name":"Docs","created_at":"2026-06-22T00:00:00Z"}],
             "files":[{"id":"f1","owner_id":"u1","folder_id":"d1","name":"a.pdf","size":10,"mime":"application/pdf",
             "sha256":"x","has_thumb":false,"created_at":"2026-06-22T00:00:00Z"}]}
        """.trimIndent()
        val res = json.decodeFromString<ListResponseDto>(body)
        assertEquals(1, res.folders.size)
        assertEquals("Docs", res.folders[0].name)
        assertEquals(1, res.files.size)
        assertEquals("d1", res.files[0].folderId)
    }

    @Test
    fun parsesSearchHitsWithPath() {
        val body = """
            {"query":"a","folders":[],"files":[{"id":"f1","owner_id":"u1","folder_id":"d1","name":"a.pdf",
             "size":10,"mime":"application/pdf","sha256":"x","has_thumb":false,"created_at":"2026-06-22T00:00:00Z",
             "path":[{"id":"d1","name":"Docs"}]}]}
        """.trimIndent()
        val res = json.decodeFromString<SearchResponseDto>(body)
        assertEquals(1, res.files.size)
        assertEquals("Docs", res.files[0].path.first().name)
    }

    @Test
    fun parsesSetupStatus() {
        assertTrue(json.decodeFromString<SetupStatusDto>("""{"needs_setup":true}""").needsSetup)
        assertFalse(json.decodeFromString<SetupStatusDto>("""{"needs_setup":false}""").needsSetup)
    }

    @Test
    fun parsesTelegramStateWithOmittedOptionalFields() {
        // The backend omits phone/awaiting_* when not linking (omitempty).
        val state = json.decodeFromString<TelegramStateDto>("""{"status":"linked","is_premium":true}""")
        assertEquals("linked", state.status)
        assertTrue(state.isPremium)
        assertNull(state.phone)
        assertFalse(state.awaitingCode)
    }
}
