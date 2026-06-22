package eu.r3pr3ss10n.hangar.data.repository

import eu.r3pr3ss10n.hangar.data.remote.dto.FileDto
import eu.r3pr3ss10n.hangar.data.remote.dto.TelegramStateDto
import eu.r3pr3ss10n.hangar.data.remote.dto.UserDto
import eu.r3pr3ss10n.hangar.domain.Role
import eu.r3pr3ss10n.hangar.domain.TelegramStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MappersTest {

    @Test
    fun mapsAdminRole() {
        val u = UserDto(id = "1", username = "a", role = "admin", createdAt = "t").toDomain()
        assertEquals(Role.ADMIN, u.role)
        assertTrue(u.isAdmin)
    }

    @Test
    fun unknownRoleFallsBackToUser() {
        val u = UserDto(id = "1", username = "a", role = "weird", createdAt = "t").toDomain()
        assertEquals(Role.USER, u.role)
        assertFalse(u.isAdmin)
    }

    @Test
    fun fileImageDetection() {
        val img = FileDto("1", "u", null, "p.png", 1, "image/png", "s", true, "t").toDomain()
        val doc = FileDto("2", "u", null, "d.pdf", 1, "application/pdf", "s", false, "t").toDomain()
        assertTrue(img.isImage)
        assertFalse(doc.isImage)
    }

    @Test
    fun telegramStatusParsing() {
        assertEquals(TelegramStatus.LINKED, TelegramStateDto(status = "linked").toDomain().status)
        assertEquals(TelegramStatus.LINKING, TelegramStateDto(status = "linking").toDomain().status)
        assertEquals(TelegramStatus.NOT_LINKED, TelegramStateDto(status = "not_linked").toDomain().status)
        assertEquals(TelegramStatus.NOT_LINKED, TelegramStateDto(status = "").toDomain().status)
    }
}
