/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.conversationlist.viewmodels

import android.app.Application
import android.content.Context
import android.content.res.Resources
import com.nextcloud.talk.arbitrarystorage.ArbitraryStorageManager
import com.nextcloud.talk.contacts.ContactsRepository
import com.nextcloud.talk.conversationlist.data.OfflineConversationsRepository
import com.nextcloud.talk.conversationlist.data.network.ConversationListUpdater
import com.nextcloud.talk.conversationlist.ui.ConversationListEntry
import com.nextcloud.talk.data.user.model.User
import com.nextcloud.talk.invitation.data.InvitationsRepository
import com.nextcloud.talk.jobs.ConversationActionScheduler
import com.nextcloud.talk.logger.Logger
import com.nextcloud.talk.models.domain.ConversationModel
import com.nextcloud.talk.models.domain.SearchMessageEntry
import com.nextcloud.talk.models.json.autocomplete.AutocompleteUserDto
import com.nextcloud.talk.models.json.conversations.ConversationDto
import com.nextcloud.talk.models.json.conversations.ConversationEnums
import com.nextcloud.talk.models.json.participants.ParticipantDto
import com.nextcloud.talk.openconversations.data.OpenConversationsRepository
import com.nextcloud.talk.repositories.unifiedsearch.UnifiedSearchRepository
import com.nextcloud.talk.repositories.unifiedsearch.UnifiedSearchRepository.UnifiedSearchResults
import com.nextcloud.talk.threadsoverview.data.ThreadsRepository
import com.nextcloud.talk.users.UserManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.IOException
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger

/**
 * Tests for [ConversationsListViewModel.getSearchQuery]: local matches are listed without waiting
 * for the server, and the search counts as loading until every server request has answered.
 */
@Suppress("TooManyFunctions")
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [33])
class ConversationsListViewModelSearchTest {

    private val repository: OfflineConversationsRepository = mock()
    private val openConversationsRepository: OpenConversationsRepository = mock()
    private val contactsRepository: ContactsRepository = mock()
    private val resources = mock<Resources> { on { getString(any()) } doReturn HEADER_TITLE }
    private val context = mock<Context> { on { resources } doReturn resources }

    private val openConversations = MutableSharedFlow<List<ConversationDto>>()
    private val contacts = MutableSharedFlow<List<AutocompleteUserDto>>()
    private val messages = CompletableDeferred<UnifiedSearchResults<SearchMessageEntry>>()
    private val moreMessages = CompletableDeferred<UnifiedSearchResults<SearchMessageEntry>>()
    private val moreMessagesRequests = AtomicInteger()
    private val moreMessagesCanceled = CompletableDeferred<Unit>()
    private val unifiedSearchRepository = object : UnifiedSearchRepository {
        override suspend fun searchMessages(
            credentials: String?,
            url: String,
            searchTerm: String,
            cursor: Int,
            limit: Int
        ) = if (cursor == 0) messages.await() else awaitMoreMessages()

        private suspend fun awaitMoreMessages(): UnifiedSearchResults<SearchMessageEntry> {
            moreMessagesRequests.incrementAndGet()
            return try {
                moreMessages.await()
            } catch (e: CancellationException) {
                moreMessagesCanceled.complete(Unit)
                throw e
            }
        }

        override suspend fun searchInRoom(
            credentials: String?,
            url: String,
            roomToken: String,
            searchTerm: String,
            cursor: Int,
            limit: Int
        ) = messages.await()
    }

    private val mainDispatcher = UnconfinedTestDispatcher()

    private lateinit var viewModel: ConversationsListViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(mainDispatcher)
        whenever(repository.roomListFlow(any())).thenReturn(MutableStateFlow(listOf(CONVERSATION)))
        whenever(repository.syncErrorFlow).thenReturn(emptyFlow())
        whenever(openConversationsRepository.fetchOpenConversationsFlow(any(), any())).thenReturn(openConversations)
        whenever(contactsRepository.getContactsFlow(any(), anyOrNull())).thenReturn(contacts)

        viewModel = ConversationsListViewModel(
            repository,
            mock<ThreadsRepository>(),
            openConversationsRepository,
            contactsRepository,
            unifiedSearchRepository,
            mock<InvitationsRepository>(),
            mock<ArbitraryStorageManager>(),
            mock<UserManager>(),
            mock<ConversationListUpdater>(),
            mock<ConversationActionScheduler>(),
            mock<Logger>(),
            USER
        )
        viewModel.setIsSearchActive(true)
    }

    @After
    fun tearDown() {
        messages.complete(NO_MESSAGES)
        moreMessages.complete(NO_MESSAGES)
        Dispatchers.resetMain()
    }

    @Test
    fun `a local conversation is listed while the server has not answered yet`() {
        search()

        assertTrue(CONVERSATION_ENTRY in viewModel.conversationListEntriesFlow.value)
        assertTrue(viewModel.isSearchLoadingFlow.value)
    }

    @Test
    fun `the search stops loading once every server request has answered`() {
        search()

        runBlocking {
            openConversations.emit(emptyList())
            contacts.emit(emptyList())
            messages.complete(NO_MESSAGES)
            awaitLoading(false)
        }

        assertEquals(
            listOf(ConversationListEntry.Header(HEADER_TITLE), CONVERSATION_ENTRY),
            viewModel.conversationListEntriesFlow.value
        )
    }

    @Test
    fun `a failed server request does not keep the search loading`() {
        whenever(contactsRepository.getContactsFlow(any(), anyOrNull()))
            .thenReturn(flow { throw IllegalStateException("offline") })
        search()

        runBlocking {
            openConversations.emit(emptyList())
            messages.complete(NO_MESSAGES)
            awaitLoading(false)
        }

        assertTrue(CONVERSATION_ENTRY in viewModel.conversationListEntriesFlow.value)
    }

    @Test
    fun `messages loaded with load more are kept when a later server response arrives`() {
        search()
        runBlocking {
            messages.complete(UnifiedSearchResults(cursor = 1, hasMore = true, entries = listOf(FIRST_MESSAGE)))
            awaitEntries { ConversationListEntry.LoadMore in it }

            viewModel.loadMoreMessages(context)
            moreMessages.complete(UnifiedSearchResults(cursor = 2, hasMore = false, entries = listOf(SECOND_MESSAGE)))
            awaitEntries { ConversationListEntry.MessageResultEntry(SECOND_MESSAGE) in it }

            openConversations.emit(emptyList())
            contacts.emit(emptyList())
            awaitLoading(false)
        }

        val entries = viewModel.conversationListEntriesFlow.value
        assertTrue(ConversationListEntry.MessageResultEntry(FIRST_MESSAGE) in entries)
        assertTrue(ConversationListEntry.MessageResultEntry(SECOND_MESSAGE) in entries)
        assertFalse(ConversationListEntry.LoadMore in entries)
    }

    @Test
    fun `typing quickly sends the server requests only for the last query`() {
        val requestedQueries = CopyOnWriteArrayList<String>()
        whenever(contactsRepository.getContactsFlow(any(), anyOrNull())).thenAnswer { invocation ->
            val query = invocation.getArgument<String>(1)
            flow {
                requestedQueries.add(query)
                emit(emptyList<AutocompleteUserDto>())
            }
        }

        listOf("c", "co", "con").forEach { query ->
            viewModel.setSearchQuery(query)
            viewModel.getSearchQuery(context, query)
        }
        skipTypingPause()

        assertEquals(listOf("con"), requestedQueries)
    }

    @Test
    fun `a failed load more keeps the shown messages`() {
        val uncaught = CopyOnWriteArrayList<Throwable>()
        val previousHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { _, e -> uncaught.add(e) }
        try {
            searchWithMoreMessages()

            viewModel.loadMoreMessages(context)
            moreMessages.completeExceptionally(IOException("offline"))
            runBlocking { delay(SETTLE_MS) }
        } finally {
            Thread.setDefaultUncaughtExceptionHandler(previousHandler)
        }

        assertEquals(emptyList<Throwable>(), uncaught)
        assertTrue(
            ConversationListEntry.MessageResultEntry(FIRST_MESSAGE) in viewModel.conversationListEntriesFlow.value
        )
    }

    @Test
    fun `a failed load more removes the load more entry also when the results are rebuilt`() {
        searchWithMoreMessages()

        viewModel.loadMoreMessages(context)
        moreMessages.completeExceptionally(IOException("offline"))
        runBlocking {
            awaitEntries { ConversationListEntry.LoadMore !in it }
            openConversations.emit(emptyList())
            contacts.emit(emptyList())
            awaitLoading(false)
        }

        val entries = viewModel.conversationListEntriesFlow.value
        assertFalse(ConversationListEntry.LoadMore in entries)
        assertTrue(ConversationListEntry.MessageResultEntry(FIRST_MESSAGE) in entries)
    }

    @Test
    fun `tapping load more again while it loads requests the next page once`() {
        searchWithMoreMessages()

        viewModel.loadMoreMessages(context)
        viewModel.loadMoreMessages(context)
        runBlocking {
            delay(SETTLE_MS)
            moreMessages.complete(UnifiedSearchResults(cursor = 2, hasMore = false, entries = listOf(SECOND_MESSAGE)))
            awaitEntries { ConversationListEntry.MessageResultEntry(SECOND_MESSAGE) in it }
            delay(SETTLE_MS)
        }

        assertEquals(1, moreMessagesRequests.get())
        assertEquals(
            1,
            viewModel.conversationListEntriesFlow.value.count {
                it == ConversationListEntry.MessageResultEntry(SECOND_MESSAGE)
            }
        )
    }

    @Test
    fun `load more of the previous query stops when the query changes`() {
        searchWithMoreMessages()
        viewModel.loadMoreMessages(context)
        runBlocking { withTimeout(TIMEOUT_MS) { while (moreMessagesRequests.get() == 0) delay(POLL_MS) } }

        viewModel.setSearchQuery("$QUERY-changed")

        runBlocking { withTimeout(TIMEOUT_MS) { moreMessagesCanceled.await() } }
    }

    @Test
    fun `repeating the query while its server requests are pending does not start them again`() {
        viewModel.setSearchQuery(QUERY_WITHOUT_LOCAL_MATCH)
        viewModel.getSearchQuery(context, QUERY_WITHOUT_LOCAL_MATCH)

        viewModel.getSearchQuery(context, QUERY_WITHOUT_LOCAL_MATCH)

        verify(openConversationsRepository, times(1)).fetchOpenConversationsFlow(any(), any())
    }

    @Test
    fun `the search of the previous query stops when the query changes`() {
        search()
        runBlocking {
            messages.complete(UnifiedSearchResults(cursor = 1, hasMore = false, entries = listOf(FIRST_MESSAGE)))
            awaitEntries { ConversationListEntry.MessageResultEntry(FIRST_MESSAGE) in it }
        }

        viewModel.setSearchQuery("$QUERY-changed")
        runBlocking {
            openConversations.emit(emptyList())
            contacts.emit(emptyList())
        }

        assertTrue(viewModel.isSearchLoadingFlow.value)
    }

    @Test
    fun `returning to the previous query before the debounce ends searches it again`() {
        search()
        runBlocking {
            openConversations.emit(emptyList())
            contacts.emit(emptyList())
            messages.complete(NO_MESSAGES)
            awaitLoading(false)
        }

        whenever(openConversationsRepository.fetchOpenConversationsFlow(any(), any())).thenReturn(flowOf(emptyList()))
        whenever(contactsRepository.getContactsFlow(any(), anyOrNull())).thenReturn(flowOf(emptyList()))

        viewModel.setSearchQuery("$QUERY-changed")
        search()

        runBlocking { awaitLoading(false) }
    }

    private fun searchWithMoreMessages() {
        search()
        runBlocking {
            messages.complete(UnifiedSearchResults(cursor = 1, hasMore = true, entries = listOf(FIRST_MESSAGE)))
            awaitEntries { ConversationListEntry.LoadMore in it }
        }
    }

    private fun search() {
        viewModel.setSearchQuery(QUERY)
        viewModel.getSearchQuery(context, QUERY)
        skipTypingPause()
    }

    private fun skipTypingPause() {
        mainDispatcher.scheduler.advanceTimeBy(TYPING_PAUSE_MS)
        mainDispatcher.scheduler.runCurrent()
    }

    private suspend fun awaitEntries(predicate: (List<ConversationListEntry>) -> Boolean) {
        withTimeout(TIMEOUT_MS) { viewModel.conversationListEntriesFlow.first(predicate) }
    }

    private suspend fun awaitLoading(isLoading: Boolean) {
        withTimeout(TIMEOUT_MS) { viewModel.isSearchLoadingFlow.first { it == isLoading } }
    }

    companion object {
        private const val QUERY = "conv"
        private const val QUERY_WITHOUT_LOCAL_MATCH = "nomatch"
        private const val TIMEOUT_MS = 5_000L
        private const val SETTLE_MS = 300L
        private const val TYPING_PAUSE_MS = 600L
        private const val POLL_MS = 10L
        private const val HEADER_TITLE = "Header"
        private val NO_MESSAGES =
            UnifiedSearchResults<SearchMessageEntry>(cursor = 0, hasMore = false, entries = emptyList())

        private val USER = User(
            id = 1,
            userId = "me",
            username = "me",
            token = "app-password",
            baseUrl = "https://server.example.com"
        )

        private val CONVERSATION = ConversationModel(
            internalId = "1@token",
            accountId = 1L,
            token = "token",
            name = "conversation",
            displayName = "Conversation",
            description = "",
            type = ConversationEnums.ConversationType.ROOM_GROUP_CALL,
            participantType = ParticipantDto.ParticipantType.USER,
            sessionId = "",
            actorId = "me",
            actorType = "users",
            objectType = ConversationEnums.ObjectType.DEFAULT,
            notificationLevel = ConversationEnums.NotificationLevel.DEFAULT,
            conversationReadOnlyState = ConversationEnums.ConversationReadOnlyState.CONVERSATION_READ_WRITE,
            lobbyState = ConversationEnums.LobbyState.LOBBY_STATE_ALL_PARTICIPANTS,
            lobbyTimer = 0L,
            canLeaveConversation = true,
            canDeleteConversation = true,
            unreadMentionDirect = false,
            notificationCalls = 0,
            avatarVersion = "",
            hasCustomAvatar = false,
            callStartTime = 0L
        )

        private val CONVERSATION_ENTRY = ConversationListEntry.ConversationEntry(CONVERSATION)
        private val FIRST_MESSAGE = searchMessage("1")
        private val SECOND_MESSAGE = searchMessage("2")

        private fun searchMessage(id: String) =
            SearchMessageEntry(
                searchTerm = QUERY,
                thumbnailURL = null,
                title = "Message $id",
                messageExcerpt = "$QUERY message $id",
                conversationToken = CONVERSATION.token,
                threadId = null,
                messageId = id
            )
    }
}
