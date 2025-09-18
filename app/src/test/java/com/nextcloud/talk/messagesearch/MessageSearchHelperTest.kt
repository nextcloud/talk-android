/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2022 Nextcloud GmbH
 * SPDX-FileCopyrightText: 2022 Álvaro Brey <alvaro@alvarobrey.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.messagesearch

import com.nextcloud.talk.models.domain.SearchMessageEntry
import com.nextcloud.talk.repositories.unifiedsearch.UnifiedSearchRepository
import com.nextcloud.talk.test.fakes.FakeUnifiedSearchRepository
import io.reactivex.Observable
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.mockito.MockitoAnnotations

class MessageSearchHelperTest {

    val repository = FakeUnifiedSearchRepository()

    @Suppress("LongParameterList")
    private fun createMessageEntry(
        searchTerm: String = "foo",
        thumbnailURL: String = "foo",
        title: String = "foo",
        messageExcerpt: String = "foo",
        conversationToken: String = "foo",
        messageId: String? = "foo"
    ) = SearchMessageEntry(searchTerm, thumbnailURL, title, messageExcerpt, conversationToken, messageId)

    @Before
    fun setUp() {
        MockitoAnnotations.openMocks(this)
    }

    @Test
    fun emptySearch() {
        repository.response = UnifiedSearchRepository.UnifiedSearchResults(0, false, emptyList())

        val sut = MessageSearchHelper(repository)

        val testObserver = sut.startMessageSearch("foo").test()
        testObserver.assertComplete()
        testObserver.assertValueCount(1)
        val expected = MessageSearchHelper.MessageSearchResults(emptyList(), false)
        testObserver.assertValue(expected)
    }

    @Test
    fun nonEmptySearch_withMoreResults() {
        val entries = (1..5).map { createMessageEntry() }
        repository.response = UnifiedSearchRepository.UnifiedSearchResults(5, true, entries)

        val sut = MessageSearchHelper(repository)

        val observable = sut.startMessageSearch("foo")
        val expected = MessageSearchHelper.MessageSearchResults(entries, true)
        testCall(observable, expected)
    }

    @Test
    fun nonEmptySearch_withNoMoreResults() {
        val entries = (1..2).map { createMessageEntry() }
        repository.response = UnifiedSearchRepository.UnifiedSearchResults(2, false, entries)

        val sut = MessageSearchHelper(repository)

        val observable = sut.startMessageSearch("foo")
        val expected = MessageSearchHelper.MessageSearchResults(entries, false)
        testCall(observable, expected)
    }

    @Test
    fun nonEmptySearch_consecutiveSearches_sameResult() {
        val entries = (1..2).map { createMessageEntry() }
        repository.response = UnifiedSearchRepository.UnifiedSearchResults(2, false, entries)

        val sut = MessageSearchHelper(repository)

        repeat(5) {
            val observable = sut.startMessageSearch("foo")
            val expected = MessageSearchHelper.MessageSearchResults(entries, false)
            testCall(observable, expected)
        }
    }

    @Test
    fun loadMore_noPreviousResults() {
        val sut = MessageSearchHelper(repository)
        Assert.assertEquals(null, sut.loadMore())
    }

    @Test
    fun loadMore_previousResults_sameSearch() {
        val sut = MessageSearchHelper(repository)

        val firstPageEntries = (1..5).map { createMessageEntry() }
        repository.response = UnifiedSearchRepository.UnifiedSearchResults(5, true, firstPageEntries)

        val firstPageObservable = sut.startMessageSearch("foo")
        Assert.assertEquals(0, repository.lastRequestedCursor)
        val firstPageExpected = MessageSearchHelper.MessageSearchResults(firstPageEntries, true)
        testCall(firstPageObservable, firstPageExpected)

        val secondPageEntries = (1..5).map { createMessageEntry(title = "bar") }
        repository.response = UnifiedSearchRepository.UnifiedSearchResults(10, false, secondPageEntries)

        val secondPageObservable = sut.loadMore()
        Assert.assertEquals(5, repository.lastRequestedCursor)
        Assert.assertNotNull(secondPageObservable)
        val secondPageExpected = MessageSearchHelper.MessageSearchResults(firstPageEntries + secondPageEntries, false)
        testCall(secondPageObservable!!, secondPageExpected)
    }

    private fun testCall(
        searchCall: Observable<MessageSearchHelper.MessageSearchResults>,
        expectedResult: MessageSearchHelper.MessageSearchResults
    ) {
        val testObserver = searchCall.test()
        testObserver.assertComplete()
        testObserver.assertValueCount(1)
        testObserver.assertValue(expectedResult)
        testObserver.dispose()
    }
}
