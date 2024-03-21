/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2022 Nextcloud GmbH
 * SPDX-FileCopyrightText: 2022 Álvaro Brey <alvaro@alvarobrey.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.test.fakes

import com.nextcloud.talk.models.domain.SearchMessageEntry
import com.nextcloud.talk.repositories.unifiedsearch.UnifiedSearchRepository
import io.reactivex.Observable

class FakeUnifiedSearchRepository : UnifiedSearchRepository {

    lateinit var response: UnifiedSearchRepository.UnifiedSearchResults<SearchMessageEntry>
    var lastRequestedCursor = -1

    override fun searchMessages(
        searchTerm: String,
        cursor: Int,
        limit: Int
    ): Observable<UnifiedSearchRepository.UnifiedSearchResults<SearchMessageEntry>> {
        lastRequestedCursor = cursor
        return Observable.just(response)
    }

    override fun searchInRoom(
        roomToken: String,
        searchTerm: String,
        cursor: Int,
        limit: Int
    ): Observable<UnifiedSearchRepository.UnifiedSearchResults<SearchMessageEntry>> {
        lastRequestedCursor = cursor
        return Observable.just(response)
    }
}
