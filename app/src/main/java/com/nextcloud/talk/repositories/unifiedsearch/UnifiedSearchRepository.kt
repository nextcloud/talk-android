/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2022 Álvaro Brey <alvaro@alvarobrey.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.repositories.unifiedsearch

import com.nextcloud.talk.models.domain.SearchMessageEntry
import io.reactivex.Observable

interface UnifiedSearchRepository {
    data class UnifiedSearchResults<T>(val cursor: Int, val hasMore: Boolean, val entries: List<T>)

    fun searchMessages(
        searchTerm: String,
        cursor: Int = 0,
        limit: Int = DEFAULT_PAGE_SIZE
    ): Observable<UnifiedSearchResults<SearchMessageEntry>>

    fun searchInRoom(
        roomToken: String,
        searchTerm: String,
        cursor: Int = 0,
        limit: Int = DEFAULT_PAGE_SIZE
    ): Observable<UnifiedSearchResults<SearchMessageEntry>>

    companion object {
        private const val DEFAULT_PAGE_SIZE = 5
    }
}
