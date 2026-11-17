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
import com.nextcloud.talk.models.json.autocomplete.AutocompleteUserDto
import com.nextcloud.talk.models.json.conversations.ConversationEnums
import com.nextcloud.talk.models.json.participants.ParticipantDto
import com.nextcloud.talk.openconversations.data.OpenConversationsRepository
import com.nextcloud.talk.repositories.unifiedsearch.UnifiedSearchRepository
import com.nextcloud.talk.repositories.unifiedsearch.UnifiedSearchRepository.UnifiedSearchResults
import com.nextcloud.talk.threadsoverview.data.ThreadsRepository
import com.nextcloud.talk.users.UserManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.mockito.kotlin.wheneverBlocking
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The search for picking the conversation to share or forward to, which only searches the joined conversations
 * that can be picked, compared to the normal search of the conversation list.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [33])
class ConversationsListViewModelPickingSearchTest {

    private val repository: OfflineConversationsRepository = mock()
    private val openConversationsRepository: OpenConversationsRepository = mock()
    private val contactsRepository: ContactsRepository = mock()
    private val unifiedSearchRepository: UnifiedSearchRepository = mock()
    private val rooms = MutableStateFlow(listOf(TEAM, TEAM_IN_LOBBY, OTHER))
    private val stringResources: Resources = mock { on { getString(any()) } doReturn "header" }
    private val context: Context = mock { on { resources } doReturn stringResources }

    private val mainDispatcher = UnconfinedTestDispatcher()

    private lateinit var viewModel: ConversationsListViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(mainDispatcher)
        whenever(repository.roomListFlow(any())).thenReturn(rooms)
        whenever(repository.syncErrorFlow).thenReturn(emptyFlow())
        whenever(openConversationsRepository.fetchOpenConversationsFlow(any(), any())).thenReturn(flowOf(emptyList()))
        whenever(contactsRepository.getContactsFlow(any(), anyOrNull())).thenReturn(
            flowOf(listOf(AutocompleteUserDto(id = "team-lead", label = "Team lead", source = "users")))
        )
        wheneverBlocking { unifiedSearchRepository.searchMessages(anyOrNull(), any(), any(), any(), any()) }
            .thenReturn(UnifiedSearchResults(cursor = 0, hasMore = false, entries = emptyList()))

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
    fun `picking a conversation only searches the joined ones that can be picked`() {
        search("team", conversationsOnly = true)

        assertEquals(listOf(TEAM.token), awaitListedTokens(listOf(TEAM.token)))
        assertFalse(viewModel.conversationListEntriesFlow.value.any { it is ConversationListEntry.ContactEntry })
        verify(openConversationsRepository, never()).fetchOpenConversationsFlow(any(), any())
        verify(contactsRepository, never()).getContactsFlow(any(), anyOrNull())
    }

    @Test
    fun `the conversation a message is forwarded from is not found`() {
        viewModel.setHideRoomToken(TEAM.token)

        search("team", conversationsOnly = true)

        assertEquals(emptyList<String>(), listedTokens())
    }

    @Test
    fun `the normal search also finds contacts`() {
        search("team", conversationsOnly = false)

        val expected = listOf(TEAM.token, TEAM_IN_LOBBY.token)
        assertEquals(expected, awaitListedTokens(expected))
        assertEquals(1, viewModel.conversationListEntriesFlow.value.count { it is ConversationListEntry.ContactEntry })
    }

    @Test
    fun `a search runs again when picking a conversation starts`() {
        search("team", conversationsOnly = false)

        search("team", conversationsOnly = true)

        assertEquals(listOf(TEAM.token), awaitListedTokens(listOf(TEAM.token)))
    }

    private fun search(query: String, conversationsOnly: Boolean) {
        viewModel.setSearchQuery(query)
        viewModel.getSearchQuery(context, query, conversationsOnly)
        mainDispatcher.scheduler.advanceTimeBy(TYPING_PAUSE_MS)
        mainDispatcher.scheduler.runCurrent()
        runBlocking { withTimeout(TIMEOUT_MS) { viewModel.isSearchLoadingFlow.first { !it } } }
    }

    // the message search answers on another thread, so the list may follow shortly after the search is done
    private fun awaitListedTokens(expected: List<String>): List<String> {
        runBlocking {
            withTimeout(TIMEOUT_MS) { viewModel.conversationListEntriesFlow.first { tokensOf(it) == expected } }
        }
        return listedTokens()
    }

    private fun tokensOf(entries: List<ConversationListEntry>): List<String> =
        entries.filterIsInstance<ConversationListEntry.ConversationEntry>().map { it.model.token }

    private fun listedTokens(): List<String> = tokensOf(viewModel.conversationListEntriesFlow.value)

    companion object {
        private const val TIMEOUT_MS = 5_000L
        private const val TYPING_PAUSE_MS = 600L

        private val USER = User(
            id = 1,
            userId = "me",
            username = "me",
            token = "app-password",
            baseUrl = "https://server.example.com"
        )

        private val TEAM = conversation(token = "team", displayName = "Team")

        private val TEAM_IN_LOBBY = conversation(token = "team-lobby", displayName = "Team waiting room").copy(
            objectType = ConversationEnums.ObjectType.ROOM,
            lobbyState = ConversationEnums.LobbyState.LOBBY_STATE_MODERATORS_ONLY
        )

        private val OTHER = conversation(token = "other", displayName = "Other")

        private fun conversation(token: String, displayName: String) =
            ConversationModel(
                internalId = "1@$token",
                accountId = 1L,
                token = token,
                name = token,
                displayName = displayName,
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
    }
}
