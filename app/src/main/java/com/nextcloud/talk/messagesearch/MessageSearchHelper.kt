/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2022 Álvaro Brey <alvaro@alvarobrey.com>
 * SPDX-FileCopyrightText: 2022 Nextcloud GmbH
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.messagesearch

import android.util.Log
import com.nextcloud.talk.data.user.model.User
import com.nextcloud.talk.models.domain.SearchMessageEntry
import com.nextcloud.talk.repositories.unifiedsearch.UnifiedSearchRepository
import com.nextcloud.talk.repositories.unifiedsearch.UnifiedSearchRepositoryImpl.Companion.PROVIDER_TALK_MESSAGE
import com.nextcloud.talk.repositories.unifiedsearch.UnifiedSearchRepositoryImpl.Companion.PROVIDER_TALK_MESSAGE_CURRENT
import com.nextcloud.talk.utils.ApiUtils
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job

class MessageSearchHelper @JvmOverloads constructor(
    private val unifiedSearchRepository: UnifiedSearchRepository,
    private val currentUser: User?,
    private val fromRoom: String? = null
) {

    data class MessageSearchResults(val messages: List<SearchMessageEntry>, val hasMore: Boolean)

    private var searchJob: Job? = null
    private var previousSearch: String? = null
    private var previousCursor: Int = 0
    private var previousResults: List<SearchMessageEntry> = emptyList()

    suspend fun startMessageSearch(search: String): MessageSearchResults {
        resetCachedData()
        return doSearch(search)
    }

    suspend fun loadMore(): MessageSearchResults? {
        previousSearch?.let {
            return doSearch(it, previousCursor)
        }
        return null
    }

    fun cancelSearch() {
        searchJob?.cancel()
        searchJob = null
    }

    private suspend fun doSearch(search: String, cursor: Int = 0): MessageSearchResults =
        try {
            doSearchSuspend(search, cursor)
        } catch (e: CancellationException) {
            throw e
        } catch (@Suppress("Detekt.TooGenericExceptionCaught") throwable: Throwable) {
            Log.e(TAG, "message search - ERROR", throwable)
            resetCachedData()
            throw throwable
        }

    private suspend fun doSearchSuspend(search: String, cursor: Int): MessageSearchResults {
        val results = searchCallUntilDisplayableResults(search, cursor)
        previousSearch = search
        previousCursor = results.cursor
        previousResults = previousResults + results.entries
        return MessageSearchResults(previousResults, results.hasMore)
    }

    private suspend fun searchCallUntilDisplayableResults(
        search: String,
        cursor: Int,
        accumulatedEntries: List<SearchMessageEntry> = emptyList()
    ): UnifiedSearchRepository.UnifiedSearchResults<SearchMessageEntry> {
        val results = searchCall(search, cursor)
        val displayableEntries = results.entries.filterNot(SearchMessageEntry::isThreadReplyResult)
        val combinedEntries = accumulatedEntries + displayableEntries

        return if (combinedEntries.isNotEmpty() || !results.hasMore) {
            results.copy(entries = combinedEntries)
        } else {
            searchCallUntilDisplayableResults(
                search = search,
                cursor = results.cursor,
                accumulatedEntries = combinedEntries
            )
        }
    }

    private suspend fun searchCall(
        search: String,
        cursor: Int
    ): UnifiedSearchRepository.UnifiedSearchResults<SearchMessageEntry> {
        val credentials = ApiUtils.getCredentials(currentUser?.username, currentUser?.token)
        return when {
            fromRoom != null -> {
                val url = ApiUtils.getUrlForUnifiedSearch(currentUser?.baseUrl!!, PROVIDER_TALK_MESSAGE_CURRENT)
                unifiedSearchRepository.searchInRoom(
                    credentials,
                    url,
                    roomToken = fromRoom,
                    searchTerm = search,
                    cursor = cursor
                )
            }

            else -> {
                val url = ApiUtils.getUrlForUnifiedSearch(currentUser?.baseUrl!!, PROVIDER_TALK_MESSAGE)
                unifiedSearchRepository.searchMessages(
                    credentials,
                    url,
                    searchTerm = search,
                    cursor = cursor
                )
            }
        }
    }

    private fun resetCachedData() {
        previousSearch = null
        previousCursor = 0
        previousResults = emptyList()
    }

    companion object {
        private val TAG = MessageSearchHelper::class.simpleName
    }
}
