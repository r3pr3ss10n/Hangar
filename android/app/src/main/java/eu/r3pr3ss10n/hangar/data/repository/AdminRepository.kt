package eu.r3pr3ss10n.hangar.data.repository

import eu.r3pr3ss10n.hangar.data.remote.HangarApi
import eu.r3pr3ss10n.hangar.data.remote.dto.CreateUserBody
import eu.r3pr3ss10n.hangar.data.remote.dto.LinkCancelBody
import eu.r3pr3ss10n.hangar.data.remote.dto.LinkCodeBody
import eu.r3pr3ss10n.hangar.data.remote.dto.LinkPasswordBody
import eu.r3pr3ss10n.hangar.data.remote.dto.LinkStartBody
import eu.r3pr3ss10n.hangar.data.remote.dto.SetPasswordBody
import eu.r3pr3ss10n.hangar.domain.Role
import eu.r3pr3ss10n.hangar.domain.TelegramState
import eu.r3pr3ss10n.hangar.domain.User
import javax.inject.Inject
import javax.inject.Singleton

/**
 * AdminRepository covers the admin-only surface: user management and the
 * Telegram account link/unlink flow. The backend gates these with RequireAdmin,
 * so a non-admin caller gets a 403 surfaced as an [ApiException].
 */
@Singleton
class AdminRepository @Inject constructor(
    private val api: HangarApi,
) {
    // ---- users ----
    suspend fun listUsers(): List<User> = runApi { api.listUsers().users.map { it.toDomain() } }

    suspend fun createUser(username: String, password: String, role: Role): User = runApi {
        val r = if (role == Role.ADMIN) "admin" else "user"
        api.createUser(CreateUserBody(username, password, r)).user.toDomain()
    }

    suspend fun deleteUser(id: String) = runApi { api.deleteUser(id) }

    suspend fun setUserPassword(id: String, password: String) = runApi {
        api.setUserPassword(id, SetPasswordBody(password))
    }

    // ---- telegram ----
    suspend fun telegramStatus(): TelegramState = runApi { api.telegramStatus().toDomain() }

    suspend fun linkStart(phone: String): String = runApi { api.telegramLinkStart(LinkStartBody(phone)).linkId }

    /** Submits the login code; returns true when 2FA password is also required. */
    suspend fun linkCode(linkId: String, code: String): Boolean = runApi {
        api.telegramLinkCode(LinkCodeBody(linkId, code)).needPassword
    }

    suspend fun linkPassword(linkId: String, password: String) = runApi {
        api.telegramLinkPassword(LinkPasswordBody(linkId, password))
    }

    suspend fun linkCancel(linkId: String) = runApi { api.telegramLinkCancel(LinkCancelBody(linkId)) }

    suspend fun unlink() = runApi { api.telegramUnlink() }
}
