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

class FakeUnifiedSearchRepository : UnifiedSearchRepository {

    lateinit var response: UnifiedSearchRepository.UnifiedSearchResults<SearchMessageEntry>
    var lastRequestedCursor = -1
    val requestedCursors = mutableListOf<Int>()
    val responsesByCursor = mutableMapOf<Int, UnifiedSearchRepository.UnifiedSearchResults<SearchMessageEntry>>()

    private fun responseFor(cursor: Int): UnifiedSearchRepository.UnifiedSearchResults<SearchMessageEntry> =
        responsesByCursor[cursor] ?: response

    override suspend fun searchMessages(
        credentials: String?,
        url: String,
        searchTerm: String,
        cursor: Int,
        limit: Int
    ): UnifiedSearchRepository.UnifiedSearchResults<SearchMessageEntry> {
        lastRequestedCursor = cursor
        requestedCursors += cursor
        return responseFor(cursor)
    }

    override suspend fun searchInRoom(
        credentials: String?,
        url: String,
        roomToken: String,
        searchTerm: String,
        cursor: Int,
        limit: Int
    ): UnifiedSearchRepository.UnifiedSearchResults<SearchMessageEntry> {
        lastRequestedCursor = cursor
        requestedCursors += cursor
        return responseFor(cursor)
    }
}
