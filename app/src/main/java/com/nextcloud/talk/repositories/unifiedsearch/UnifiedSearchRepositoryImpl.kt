/*
 * Nextcloud Talk application
 *
 * @author Álvaro Brey
 * Copyright (C) 2022 Álvaro Brey
 * Copyright (C) 2022 Nextcloud GmbH
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

package com.nextcloud.talk.repositories.unifiedsearch

import com.nextcloud.talk.api.NcApi
import com.nextcloud.talk.models.database.UserEntity
import com.nextcloud.talk.models.domain.SearchMessageEntry
import com.nextcloud.talk.models.json.unifiedsearch.UnifiedSearchEntry
import com.nextcloud.talk.models.json.unifiedsearch.UnifiedSearchResponseData
import com.nextcloud.talk.utils.ApiUtils
import io.reactivex.Observable

class UnifiedSearchRepositoryImpl(private val api: NcApi) : UnifiedSearchRepository {

    override fun searchMessages(
        userEntity: UserEntity,
        searchTerm: String,
        cursor: Int,
        limit: Int
    ): Observable<UnifiedSearchRepository.UnifiedSearchResults<SearchMessageEntry>> {
        val apiObservable = api.performUnifiedSearch(
            ApiUtils.getCredentials(userEntity.username, userEntity.token),
            ApiUtils.getUrlForUnifiedSearch(userEntity.baseUrl, PROVIDER_TALK_MESSAGE),
            searchTerm,
            null,
            limit,
            cursor
        )
        return apiObservable.map { mapToMessageResults(it.ocs?.data!!, searchTerm, limit) }
    }

    override fun searchInRoom(text: String, roomId: String): Observable<List<SearchMessageEntry>> {
        TODO()
    }

    companion object {
        private const val PROVIDER_TALK_MESSAGE = "talk-message"
        private const val PROVIDER_TALK_MESSAGE_CURRENT = "talk-message-current"

        private const val ATTRIBUTE_CONVERSATION = "conversation"
        private const val ATTRIBUTE_MESSAGE_ID = "messageId"

        private fun mapToMessageResults(data: UnifiedSearchResponseData, searchTerm: String, limit: Int):
            UnifiedSearchRepository.UnifiedSearchResults<SearchMessageEntry> {
            val entries = data.entries?.map { it -> mapToMessage(it, searchTerm) }
            val cursor = data.cursor ?: 0
            val hasMore = entries?.size == limit
            return UnifiedSearchRepository.UnifiedSearchResults(cursor, hasMore, entries ?: emptyList())
        }

        private fun mapToMessage(unifiedSearchEntry: UnifiedSearchEntry, searchTerm: String): SearchMessageEntry {
            val conversation = unifiedSearchEntry.attributes?.get(ATTRIBUTE_CONVERSATION)!!
            val messageId = unifiedSearchEntry.attributes?.get(ATTRIBUTE_MESSAGE_ID)
            return SearchMessageEntry(
                searchTerm,
                unifiedSearchEntry.thumbnailUrl,
                unifiedSearchEntry.title!!,
                unifiedSearchEntry.subline!!,
                conversation,
                messageId
            )
        }
    }
}
