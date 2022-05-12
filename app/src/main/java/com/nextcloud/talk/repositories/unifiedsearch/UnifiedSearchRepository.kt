package com.nextcloud.talk.repositories.unifiedsearch

import com.nextcloud.talk.models.database.UserEntity
import com.nextcloud.talk.models.domain.SearchMessageEntry
import io.reactivex.Observable

interface UnifiedSearchRepository {
    data class UnifiedSearchResults<T>(
        val cursor: Int,
        val hasMore: Boolean,
        val entries: List<T>
    )

    fun searchMessages(
        userEntity: UserEntity,
        searchTerm: String,
        cursor: Int = 0,
        limit: Int = DEFAULT_PAGE_SIZE
    ): Observable<UnifiedSearchResults<SearchMessageEntry>>

    fun searchInRoom(text: String, roomId: String): Observable<List<SearchMessageEntry>>

    companion object {
        private const val DEFAULT_PAGE_SIZE = 5
    }
}
