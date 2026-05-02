/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2022 Nextcloud GmbH
 * SPDX-FileCopyrightText: 2022 Álvaro Brey <alvaro@alvarobrey.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.messagesearch

import com.nextcloud.talk.data.user.UsersDao
import com.nextcloud.talk.data.user.UsersRepository
import com.nextcloud.talk.data.user.UsersRepositoryImpl
import com.nextcloud.talk.models.domain.SearchMessageEntry
import com.nextcloud.talk.repositories.unifiedsearch.UnifiedSearchRepository
import com.nextcloud.talk.test.fakes.FakeUnifiedSearchRepository
import com.nextcloud.talk.users.UserManager
import com.nextcloud.talk.utils.database.user.CurrentUserProviderOld
import com.nextcloud.talk.utils.database.user.CurrentUserProviderOldImpl
import com.nextcloud.talk.utils.preview.DummyUserDaoImpl
import kotlinx.coroutines.test.runTest
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.mockito.MockitoAnnotations

class MessageSearchHelperTest {

    val repository = FakeUnifiedSearchRepository()

    val usersDao: UsersDao
        get() = DummyUserDaoImpl()

    val userRepository: UsersRepository
        get() = UsersRepositoryImpl(usersDao)

    val userManager: UserManager
        get() = UserManager(userRepository)

    val userProvider: CurrentUserProviderOld
        get() = CurrentUserProviderOldImpl(userManager)

    @Suppress("LongParameterList")
    private fun createMessageEntry(
        searchTerm: String = "foo",
        thumbnailURL: String = "foo",
        title: String = "foo",
        messageExcerpt: String = "foo",
        conversationToken: String = "foo",
        messageId: String? = "foo",
        threadId: String? = "foo"
    ) = SearchMessageEntry(searchTerm, thumbnailURL, title, messageExcerpt, conversationToken, threadId, messageId)

    @Before
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        repository.requestedCursors.clear()
        repository.responsesByCursor.clear()
    }

    @Test
    fun emptySearch() =
        runTest {
            repository.response = UnifiedSearchRepository.UnifiedSearchResults(0, false, emptyList())

            val sut = MessageSearchHelper(
                repository,
                currentUser = userProvider.currentUser.blockingGet()
            )

            val result = sut.startMessageSearch("foo")
            val expected = MessageSearchHelper.MessageSearchResults(emptyList(), false)
            Assert.assertEquals(expected, result)
        }

    @Test
    fun nonEmptySearch_withMoreResults() =
        runTest {
            val entries = (1..5).map { createMessageEntry() }
            repository.response = UnifiedSearchRepository.UnifiedSearchResults(5, true, entries)

            val sut = MessageSearchHelper(
                repository,
                currentUser = userProvider.currentUser.blockingGet()
            )

            val result = sut.startMessageSearch("foo")
            val expected = MessageSearchHelper.MessageSearchResults(entries, true)
            Assert.assertEquals(expected, result)
        }

    @Test
    fun nonEmptySearch_withNoMoreResults() =
        runTest {
            val entries = (1..2).map { createMessageEntry() }
            repository.response = UnifiedSearchRepository.UnifiedSearchResults(2, false, entries)

            val sut = MessageSearchHelper(
                repository,
                currentUser = userProvider.currentUser.blockingGet()
            )

            val result = sut.startMessageSearch("foo")
            val expected = MessageSearchHelper.MessageSearchResults(entries, false)
            Assert.assertEquals(expected, result)
        }

    @Test
    fun nonEmptySearch_filtersThreadRepliesButKeepsThreadRoots() =
        runTest {
            val threadRoot = createMessageEntry(messageId = "42", threadId = "42", title = "root")
            val threadReply = createMessageEntry(messageId = "43", threadId = "42", title = "reply")
            val regularMessage = createMessageEntry(messageId = "44", threadId = null, title = "regular")
            val entries = listOf(threadRoot, threadReply, regularMessage)
            repository.response = UnifiedSearchRepository.UnifiedSearchResults(3, false, entries)

            val sut = MessageSearchHelper(
                repository,
                currentUser = userProvider.currentUser.blockingGet()
            )

            val result = sut.startMessageSearch("foo")
            val expected = MessageSearchHelper.MessageSearchResults(listOf(threadRoot, regularMessage), false)
            Assert.assertEquals(expected, result)
        }

    @Test
    fun nonEmptySearch_skipsThreadReplyOnlyPageWhenMoreResultsExist() =
        runTest {
            val filteredPage = listOf(
                createMessageEntry(messageId = "43", threadId = "42", title = "reply-1"),
                createMessageEntry(messageId = "45", threadId = "44", title = "reply-2")
            )
            val visiblePage = listOf(
                createMessageEntry(messageId = "42", threadId = "42", title = "root"),
                createMessageEntry(messageId = "46", threadId = null, title = "regular")
            )
            repository.response = UnifiedSearchRepository.UnifiedSearchResults(2, true, filteredPage)
            repository.responsesByCursor[0] = UnifiedSearchRepository.UnifiedSearchResults(2, true, filteredPage)
            repository.responsesByCursor[2] = UnifiedSearchRepository.UnifiedSearchResults(4, false, visiblePage)

            val sut = MessageSearchHelper(
                repository,
                currentUser = userProvider.currentUser.blockingGet()
            )

            val result = sut.startMessageSearch("foo")
            val expected = MessageSearchHelper.MessageSearchResults(visiblePage, false)
            Assert.assertEquals(expected, result)
            Assert.assertEquals(listOf(0, 2), repository.requestedCursors)
            Assert.assertEquals(2, repository.lastRequestedCursor)
        }

    @Test
    fun nonEmptySearch_consecutiveSearches_sameResult() =
        runTest {
            val entries = (1..2).map { createMessageEntry() }
            repository.response = UnifiedSearchRepository.UnifiedSearchResults(2, false, entries)

            val sut = MessageSearchHelper(
                repository,
                currentUser = userProvider.currentUser.blockingGet()
            )

            repeat(5) {
                val result = sut.startMessageSearch("foo")
                val expected = MessageSearchHelper.MessageSearchResults(entries, false)
                Assert.assertEquals(expected, result)
            }
        }

    @Test
    fun loadMore_noPreviousResults() =
        runTest {
            val sut = MessageSearchHelper(
                repository,
                currentUser = userProvider.currentUser.blockingGet()
            )
            Assert.assertEquals(null, sut.loadMore())
        }

    @Test
    fun loadMore_previousResults_sameSearch() =
        runTest {
            val sut = MessageSearchHelper(
                repository,
                currentUser = userProvider.currentUser.blockingGet()
            )

            val firstPageEntries = (1..5).map { createMessageEntry() }
            repository.response =
                UnifiedSearchRepository.UnifiedSearchResults(5, true, firstPageEntries)

            val firstPageResult = sut.startMessageSearch("foo")
            val firstPageExpected = MessageSearchHelper.MessageSearchResults(firstPageEntries, true)
            Assert.assertEquals(firstPageExpected, firstPageResult)
            Assert.assertEquals(0, repository.lastRequestedCursor)

            val secondPageEntries = (1..5).map { createMessageEntry(title = "bar") }
            repository.response =
                UnifiedSearchRepository.UnifiedSearchResults(10, false, secondPageEntries)

            val secondPageResult = sut.loadMore()
            Assert.assertNotNull(secondPageResult)
            val secondPageExpected = MessageSearchHelper.MessageSearchResults(
                firstPageEntries + secondPageEntries,
                false
            )
            Assert.assertEquals(secondPageExpected, secondPageResult)
            Assert.assertEquals(5, repository.lastRequestedCursor)
        }

    @Test
    fun loadMore_skipsThreadReplyOnlyPageWhenMoreResultsExist() =
        runTest {
            val sut = MessageSearchHelper(
                repository,
                currentUser = userProvider.currentUser.blockingGet()
            )

            val firstPageEntries = listOf(
                createMessageEntry(messageId = "10", threadId = null, title = "first")
            )
            repository.response =
                UnifiedSearchRepository.UnifiedSearchResults(1, true, firstPageEntries)
            repository.responsesByCursor[0] =
                UnifiedSearchRepository.UnifiedSearchResults(1, true, firstPageEntries)

            val firstPageResult = sut.startMessageSearch("foo")
            val firstPageExpected = MessageSearchHelper.MessageSearchResults(firstPageEntries, true)
            Assert.assertEquals(firstPageExpected, firstPageResult)

            val filteredPage = listOf(
                createMessageEntry(messageId = "43", threadId = "42", title = "reply-1")
            )
            val visiblePage = listOf(
                createMessageEntry(messageId = "42", threadId = "42", title = "root")
            )
            repository.responsesByCursor[1] =
                UnifiedSearchRepository.UnifiedSearchResults(2, true, filteredPage)
            repository.responsesByCursor[2] =
                UnifiedSearchRepository.UnifiedSearchResults(3, false, visiblePage)

            val secondPageResult = sut.loadMore()
            Assert.assertNotNull(secondPageResult)
            val secondPageExpected =
                MessageSearchHelper.MessageSearchResults(firstPageEntries + visiblePage, false)
            Assert.assertEquals(secondPageExpected, secondPageResult)
            Assert.assertEquals(listOf(0, 1, 2), repository.requestedCursors)
            Assert.assertEquals(2, repository.lastRequestedCursor)
        }
}
