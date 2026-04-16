/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2022 Álvaro Brey <alvaro@alvarobrey.com>
 * SPDX-FileCopyrightText: 2022 Nextcloud GmbH
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.repositories.unifiedsearch

import com.nextcloud.talk.api.NcApi
import com.nextcloud.talk.models.domain.SearchMessageEntry
import com.nextcloud.talk.models.json.unifiedsearch.UnifiedSearchEntry
import com.nextcloud.talk.models.json.unifiedsearch.UnifiedSearchResponseData
import io.reactivex.Observable

class UnifiedSearchRepositoryImpl(private val api: NcApi) : UnifiedSearchRepository {

    override fun searchMessages(
        credentials: String?,
        url: String,
        searchTerm: String,
        cursor: Int,
        limit: Int
    ): Observable<UnifiedSearchRepository.UnifiedSearchResults<SearchMessageEntry>> {
        val apiObservable = api.performUnifiedSearch(
            credentials,
            url,
            searchTerm,
            null,
            limit,
            cursor
        )
        return apiObservable.map { mapToMessageResults(it.ocs?.data!!, searchTerm, limit) }
    }

    override fun searchInRoom(
        credentials: String?,
        url: String,
        roomToken: String,
        searchTerm: String,
        cursor: Int,
        limit: Int
    ): Observable<UnifiedSearchRepository.UnifiedSearchResults<SearchMessageEntry>> {
        val apiObservable = api.performUnifiedSearch(
            credentials,
            url,
            searchTerm,
            fromUrlForRoom(roomToken),
            limit,
            cursor
        )
        return apiObservable.map { mapToMessageResults(it.ocs?.data!!, searchTerm, limit) }
    }

    private fun fromUrlForRoom(roomToken: String) = "/call/$roomToken"

    companion object {
        const val PROVIDER_TALK_MESSAGE = "talk-message"
        const val PROVIDER_TALK_MESSAGE_CURRENT = "talk-message-current"

        private const val ATTRIBUTE_CONVERSATION = "conversation"
        private const val ATTRIBUTE_MESSAGE_ID = "messageId"
        private const val ATTRIBUTE_THREAD_ID = "threadId"

        private fun mapToMessageResults(
            data: UnifiedSearchResponseData,
            searchTerm: String,
            limit: Int
        ): UnifiedSearchRepository.UnifiedSearchResults<SearchMessageEntry> {
            val entries = data.entries?.map { it -> mapToMessage(it, searchTerm) }
            val cursor = data.cursor ?: 0
            val hasMore = entries?.size == limit
            return UnifiedSearchRepository.UnifiedSearchResults(cursor, hasMore, entries ?: emptyList())
        }

        private fun mapToMessage(unifiedSearchEntry: UnifiedSearchEntry, searchTerm: String): SearchMessageEntry {
            val conversation = unifiedSearchEntry.attributes?.get(ATTRIBUTE_CONVERSATION)!!
            val messageId = unifiedSearchEntry.attributes?.get(ATTRIBUTE_MESSAGE_ID)
            val threadId = unifiedSearchEntry.attributes?.get(ATTRIBUTE_THREAD_ID)
            return SearchMessageEntry(
                searchTerm = searchTerm,
                thumbnailURL = unifiedSearchEntry.thumbnailUrl,
                title = unifiedSearchEntry.title!!,
                messageExcerpt = unifiedSearchEntry.subline!!,
                conversationToken = conversation,
                threadId = threadId,
                messageId = messageId
            )
        }
    }
}
