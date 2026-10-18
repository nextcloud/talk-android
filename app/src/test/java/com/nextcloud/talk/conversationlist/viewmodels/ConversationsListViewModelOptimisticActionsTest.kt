/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.conversationlist.viewmodels

import android.app.Application
import com.nextcloud.talk.arbitrarystorage.ArbitraryStorageManager
import com.nextcloud.talk.contacts.ContactsRepository
import com.nextcloud.talk.conversationlist.data.OfflineConversationsRepository
import com.nextcloud.talk.conversationlist.data.network.ConversationListUpdater
import com.nextcloud.talk.conversationlist.viewmodels.ConversationsListViewModel.ArchiveUiState
import com.nextcloud.talk.conversationlist.viewmodels.ConversationsListViewModel.ConversationReadUnreadUiState
import com.nextcloud.talk.conversationlist.viewmodels.ConversationsListViewModel.FavoriteUiState
import com.nextcloud.talk.data.user.model.User
import com.nextcloud.talk.invitation.data.InvitationsRepository
import com.nextcloud.talk.logger.Logger
import com.nextcloud.talk.models.domain.ConversationModel
import com.nextcloud.talk.models.json.capabilities.CapabilitiesDto
import com.nextcloud.talk.models.json.capabilities.SpreedCapabilityDto
import com.nextcloud.talk.models.json.conversations.ConversationEnums
import com.nextcloud.talk.models.json.generic.GenericOverall
import com.nextcloud.talk.models.json.participants.ParticipantDto
import com.nextcloud.talk.openconversations.data.OpenConversationsRepository
import com.nextcloud.talk.repositories.conversations.ConversationsRepository
import com.nextcloud.talk.repositories.unifiedsearch.UnifiedSearchRepository
import com.nextcloud.talk.threadsoverview.data.ThreadsRepository
import com.nextcloud.talk.users.UserManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
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
import org.mockito.kotlin.doSuspendableAnswer
import org.mockito.kotlin.eq
import org.mockito.kotlin.inOrder
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.stub
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyBlocking
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import retrofit2.HttpException
import retrofit2.Response

/**
 * The conversation list's optimistic actions: marking read or unread, favoriting and archiving are
 * shown at once and taken back when the server does not accept them, and a conversation being left
 * or deleted stays out of the list until its removal has gone through or failed.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [33])
class ConversationsListViewModelOptimisticActionsTest {

    private val repository: OfflineConversationsRepository = mock()
    private val conversationsRepository: ConversationsRepository = mock()
    private val conversationListUpdater: ConversationListUpdater = mock()
    private val rooms = MutableStateFlow(listOf(CONVERSATION))

    private lateinit var viewModel: ConversationsListViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        whenever(repository.roomListFlow(any())).thenReturn(rooms)
        whenever(repository.syncErrorFlow).thenReturn(emptyFlow())

        viewModel = ConversationsListViewModel(
            repository,
            mock<ThreadsRepository>(),
            mock<OpenConversationsRepository>(),
            mock<ContactsRepository>(),
            mock<UnifiedSearchRepository>(),
            mock<InvitationsRepository>(),
            mock<ArbitraryStorageManager>(),
            mock<UserManager>(),
            conversationsRepository,
            conversationListUpdater,
            mock<Logger>(),
            USER
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `a favorite the server accepts stays applied`() {
        conversationsRepository.stub {
            onBlocking { addConversationToFavorites(any(), any()) } doReturn GenericOverall()
        }

        viewModel.addConversationToFavorites(CONVERSATION)

        assertEquals(FavoriteUiState.Success, awaitOutcome(viewModel.favoriteState, FavoriteUiState.None))
        verifyBlocking(repository) { updateConversation(CONVERSATION.copy(favorite = true)) }
        verifyBlocking(repository, never()) { updateConversation(CONVERSATION) }
        verify(conversationListUpdater, never()).clearPendingFavorite(any(), any())
    }

    @Test
    fun `a favorite the server refuses is taken back`() {
        conversationsRepository.stub {
            onBlocking { addConversationToFavorites(any(), any()) } doSuspendableAnswer { throw forbidden() }
        }

        viewModel.addConversationToFavorites(CONVERSATION)

        assertEquals(FavoriteUiState.Error, awaitOutcome(viewModel.favoriteState, FavoriteUiState.None))
        assertTakenBack(CONVERSATION.copy(favorite = true))
        verify(conversationListUpdater).clearPendingFavorite(CONVERSATION.internalId, favorite = true)
    }

    @Test
    fun `an unfavorite the server refuses is taken back`() {
        val favorite = CONVERSATION.copy(favorite = true)
        conversationsRepository.stub {
            onBlocking { removeConversationFromFavorites(any(), any()) } doSuspendableAnswer { throw forbidden() }
        }

        viewModel.removeConversationFromFavorites(favorite)

        assertEquals(FavoriteUiState.Error, awaitOutcome(viewModel.favoriteState, FavoriteUiState.None))
        assertTakenBack(CONVERSATION, original = favorite)
        verify(conversationListUpdater).clearPendingFavorite(CONVERSATION.internalId, favorite = false)
    }

    @Test
    fun `marking read that the server refuses is taken back`() {
        val unread = CONVERSATION.copy(unreadMessages = UNREAD_MESSAGES)
        conversationsRepository.stub {
            onBlocking { markConversationAsRead(any(), any(), anyOrNull()) } doSuspendableAnswer { throw forbidden() }
        }

        viewModel.markConversationAsRead(unread)

        assertEquals(
            ConversationReadUnreadUiState.Error,
            awaitOutcome(viewModel.readUnreadState, ConversationReadUnreadUiState.None)
        )
        assertTakenBack(unread.copy(unreadMessages = 0), original = unread)
    }

    @Test
    fun `marking unread that the server refuses is taken back`() {
        conversationsRepository.stub {
            onBlocking { markConversationAsUnread(any(), any()) } doSuspendableAnswer { throw forbidden() }
        }

        viewModel.markConversationAsUnread(CONVERSATION)

        assertEquals(
            ConversationReadUnreadUiState.Error,
            awaitOutcome(viewModel.readUnreadState, ConversationReadUnreadUiState.None)
        )
        assertTakenBack(CONVERSATION.copy(unreadMessages = 1))
        verify(conversationListUpdater).clearPendingUnread(CONVERSATION.internalId)
    }

    @Test
    fun `an archive the server refuses is taken back`() {
        conversationsRepository.stub {
            onBlocking { archiveConversation(any(), any()) } doSuspendableAnswer { throw forbidden() }
        }

        viewModel.toggleConversationArchive(CONVERSATION)

        assertEquals(ArchiveUiState.Error, awaitOutcome(viewModel.archiveState, ArchiveUiState.None))
        assertTakenBack(CONVERSATION.copy(hasArchived = true))
        verify(conversationListUpdater).clearPendingArchived(CONVERSATION.internalId, true)
    }

    @Test
    fun `a conversation being removed is hidden until the removal is undone`() {
        viewModel.markConversationPendingRemoval(CONVERSATION.token)
        assertFalse(isListed())

        viewModel.clearConversationPendingRemoval(CONVERSATION.token)
        assertTrue(isListed())
    }

    @Test
    fun `a removed conversation stays hidden until the sync that drops it has finished`() {
        val sync = Job()
        whenever(repository.getRooms(eq(USER), eq(true))).thenReturn(sync)
        viewModel.markConversationPendingRemoval(CONVERSATION.token)

        viewModel.onConversationRemoved(USER, CONVERSATION.token)
        assertFalse(isListed())

        sync.complete()
        assertTrue(isListed())
    }

    private fun isListed(): Boolean = viewModel.visibleRoomsFlow.value.any { it.token == CONVERSATION.token }

    /** Waits for the action launched on the view model scope, whose local writes run on the IO dispatcher. */
    private fun <S> awaitOutcome(state: StateFlow<S>, pending: S): S =
        runBlocking { withTimeout(TIMEOUT_MS) { state.first { it != pending } } }

    private fun assertTakenBack(optimistic: ConversationModel, original: ConversationModel = CONVERSATION) {
        val order = inOrder(repository)
        runBlocking {
            order.verify(repository).updateConversation(optimistic)
            order.verify(repository).updateConversation(original)
        }
    }

    private fun forbidden(): HttpException =
        HttpException(Response.error<Any>(HTTP_FORBIDDEN, "".toResponseBody("text/plain".toMediaType())))

    companion object {
        private const val HTTP_FORBIDDEN = 403
        private const val UNREAD_MESSAGES = 3
        private const val TIMEOUT_MS = 5_000L

        private val USER = User(
            id = 1,
            userId = "me",
            username = "me",
            token = "app-password",
            baseUrl = "https://server.example.com",
            capabilities = CapabilitiesDto().apply {
                spreedCapability = SpreedCapabilityDto().apply {
                    features = listOf("conversation-v4", "chat-v2")
                }
            }
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
    }
}
