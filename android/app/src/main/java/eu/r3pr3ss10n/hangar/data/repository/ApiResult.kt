package eu.r3pr3ss10n.hangar.data.repository

import eu.r3pr3ss10n.hangar.data.remote.dto.ErrorDto
import kotlinx.serialization.json.Json
import retrofit2.HttpException
import java.io.IOException

/**
 * ApiException carries an HTTP status and the backend's {"error": msg} message
 * so the UI can show a meaningful reason. Network failures surface as
 * [NetworkException].
 */
class ApiException(val status: Int, message: String) : Exception(message)

class NetworkException(message: String) : Exception(message)

private val errorJson = Json { ignoreUnknownKeys = true }

/**
 * runApi executes a suspending API call and normalises failures: an HTTP error
 * becomes an [ApiException] carrying the parsed backend message; an I/O failure
 * (no server, DNS, timeout) becomes a [NetworkException].
 */
suspend fun <T> runApi(block: suspend () -> T): T {
    return try {
        block()
    } catch (e: HttpException) {
        val raw = e.response()?.errorBody()?.string()
        val msg = raw?.let {
            runCatching { errorJson.decodeFromString<ErrorDto>(it).error }.getOrNull()
        } ?: "Request failed (${e.code()})"
        throw ApiException(e.code(), msg ?: "Request failed (${e.code()})")
    } catch (e: IOException) {
        throw NetworkException(e.message ?: "Network error")
    }
}
