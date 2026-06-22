package eu.r3pr3ss10n.hangar.data.repository

import eu.r3pr3ss10n.hangar.data.remote.HangarApi
import eu.r3pr3ss10n.hangar.domain.SearchResult
import javax.inject.Inject
import javax.inject.Singleton

/** SearchRepository runs the drive-wide fuzzy search. */
@Singleton
class SearchRepository @Inject constructor(
    private val api: HangarApi,
) {
    suspend fun search(query: String): SearchResult = runApi {
        val res = api.search(query)
        SearchResult(
            query = res.query,
            folders = res.folders.map { it.toDomain() },
            files = res.files.map { it.toDomain() },
        )
    }
}
