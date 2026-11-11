/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Marcel Hibbe <dev@mhibbe.de>
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
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Tests for [ConversationsListViewModel.getSearchQuery]: local matches are listed without waiting
 * for the server, and the search counts as loading until every server request has answered.
 */
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
    private val unifiedSearchRepository = object : UnifiedSearchRepository {
        override suspend fun searchMessages(
            credentials: String?,
            url: String,
            searchTerm: String,
            cursor: Int,
            limit: Int
        ) = messages.await()

        override suspend fun searchInRoom(
            credentials: String?,
            url: String,
            roomToken: String,
            searchTerm: String,
            cursor: Int,
            limit: Int
        ) = messages.await()
    }

    private lateinit var viewModel: ConversationsListViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
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
            messages.complete(UnifiedSearchResults(cursor = 0, hasMore = false, entries = emptyList()))
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
            messages.complete(UnifiedSearchResults(cursor = 0, hasMore = false, entries = emptyList()))
            awaitLoading(false)
        }

        assertTrue(CONVERSATION_ENTRY in viewModel.conversationListEntriesFlow.value)
    }

    private fun search() {
        viewModel.setSearchQuery(QUERY)
        viewModel.getSearchQuery(context, QUERY)
    }

    private suspend fun awaitLoading(isLoading: Boolean) {
        withTimeout(TIMEOUT_MS) { viewModel.isSearchLoadingFlow.first { it == isLoading } }
    }

    companion object {
        private const val QUERY = "conv"
        private const val TIMEOUT_MS = 5_000L
        private const val HEADER_TITLE = "Header"

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
    }
}
