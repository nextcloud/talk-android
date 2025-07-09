/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2022 Álvaro Brey <alvaro@alvarobrey.com>
 * SPDX-FileCopyrightText: 2022 Nextcloud GmbH
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.messagesearch

import android.util.Log
import com.nextcloud.talk.models.domain.SearchMessageEntry
import com.nextcloud.talk.repositories.unifiedsearch.UnifiedSearchRepository
import io.reactivex.Observable
import io.reactivex.disposables.Disposable

class MessageSearchHelper @JvmOverloads constructor(
    private val unifiedSearchRepository: UnifiedSearchRepository,
    private val fromRoom: String? = null
) {

    data class MessageSearchResults(val messages: List<SearchMessageEntry>, val hasMore: Boolean)

    private var unifiedSearchDisposable: Disposable? = null
    private var previousSearch: String? = null
    private var previousCursor: Int = 0
    private var previousResults: List<SearchMessageEntry> = emptyList()

    fun startMessageSearch(search: String): Observable<MessageSearchResults> {
        resetCachedData()
        return doSearch(search)
    }

    fun loadMore(): Observable<MessageSearchResults>? {
        previousSearch?.let {
            return doSearch(it, previousCursor)
        }
        return null
    }

    fun cancelSearch() {
        disposeIfPossible()
    }

    private fun doSearch(search: String, cursor: Int = 0): Observable<MessageSearchResults> {
        disposeIfPossible()
        return searchCall(search, cursor)
            .map { results ->
                previousSearch = search
                previousCursor = results.cursor
                previousResults = previousResults + results.entries
                MessageSearchResults(previousResults, results.hasMore)
            }
            .doOnSubscribe {
                unifiedSearchDisposable = it
            }
            .doOnError { throwable ->
                Log.e(TAG, "message search - ERROR", throwable)
                resetCachedData()
                disposeIfPossible()
            }
            .doOnComplete(this::disposeIfPossible)
    }

    private fun searchCall(
        search: String,
        cursor: Int
    ): Observable<UnifiedSearchRepository.UnifiedSearchResults<SearchMessageEntry>> =
        when {
            fromRoom != null -> {
                unifiedSearchRepository.searchInRoom(
                    roomToken = fromRoom,
                    searchTerm = search,
                    cursor = cursor
                )
            }
            else -> {
                unifiedSearchRepository.searchMessages(
                    searchTerm = search,
                    cursor = cursor
                )
            }
        }

    private fun resetCachedData() {
        previousSearch = null
        previousCursor = 0
        previousResults = emptyList()
    }

    private fun disposeIfPossible() {
        unifiedSearchDisposable?.dispose()
        unifiedSearchDisposable = null
    }

    companion object {
        private val TAG = MessageSearchHelper::class.simpleName
    }
}
