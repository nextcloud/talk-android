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
import com.nextcloud.talk.jobs.ConversationActionScheduler
import com.nextcloud.talk.jobs.ConversationActionWorker.ConversationAction
import com.nextcloud.talk.logger.Logger
import com.nextcloud.talk.models.domain.ConversationModel
import com.nextcloud.talk.models.json.capabilities.CapabilitiesDto
import com.nextcloud.talk.models.json.capabilities.SpreedCapabilityDto
import com.nextcloud.talk.models.json.chat.ChatMessageDto
import com.nextcloud.talk.models.json.conversations.ConversationEnums
import com.nextcloud.talk.models.json.participants.ParticipantDto
import com.nextcloud.talk.openconversations.data.OpenConversationsRepository
import com.nextcloud.talk.repositories.unifiedsearch.UnifiedSearchRepository
import com.nextcloud.talk.threadsoverview.data.ThreadsRepository
import com.nextcloud.talk.users.UserManager
import kotlinx.coroutines.CompletableDeferred
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
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
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
import org.mockito.kotlin.wheneverBlocking
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.UUID

/**
 * The conversation list's optimistic actions: marking read or unread, favoriting and archiving are
 * shown at once, handed to a worker and taken back when the worker gives up on them, and a
 * conversation being left or deleted stays out of the list until its removal has gone through or failed.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@Suppress("TooManyFunctions")
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [33])
class ConversationsListViewModelOptimisticActionsTest {

    private val repository: OfflineConversationsRepository = mock()
    private val conversationListUpdater: ConversationListUpdater = mock()
    private val conversationActionScheduler: ConversationActionScheduler = mock()
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
            conversationListUpdater,
            conversationActionScheduler,
            mock<Logger>(),
            USER
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `a favorite the worker gets through stays applied`() {
        givenTheWorker(gotThrough = true)

        viewModel.addConversationToFavorites(CONVERSATION)

        assertEquals(FavoriteUiState.Success, awaitOutcome(viewModel.favoriteState, FavoriteUiState.None))
        verifyBlocking(repository) { updateConversation(CONVERSATION.copy(favorite = true)) }
        verifyBlocking(repository, never()) { updateConversation(CONVERSATION) }
        verify(conversationListUpdater, never()).clearPendingFavorite(any(), any())
        verify(conversationActionScheduler)
            .enqueue(USER.id!!, CONVERSATION.token, ConversationAction.Favorite(true))
    }

    @Test
    fun `a change is handed over before it is written locally, so leaving the screen cannot drop it`() {
        givenTheWorker(gotThrough = true)
        val localWrite = CompletableDeferred<Unit>()
        wheneverBlocking { repository.updateConversation(any()) }.doSuspendableAnswer { localWrite.await() }

        viewModel.toggleConversationArchive(CONVERSATION)

        // the guard comes first, so a worker that is done at once releases it rather than finding none
        val order = inOrder(conversationListUpdater, conversationActionScheduler)
        order.verify(conversationListUpdater).markPendingArchived(CONVERSATION.internalId, true)
        order.verify(conversationActionScheduler)
            .enqueue(USER.id!!, CONVERSATION.token, ConversationAction.Archive(true))
        assertEquals(ArchiveUiState.None, viewModel.archiveState.value)

        localWrite.complete(Unit)
        assertEquals(
            ArchiveUiState.Success(true, CONVERSATION.displayName),
            awaitOutcome(viewModel.archiveState, ArchiveUiState.None)
        )
    }

    @Test
    fun `a favorite the worker gives up on is taken back`() {
        givenTheWorker(gotThrough = false)

        viewModel.addConversationToFavorites(CONVERSATION)

        awaitFinal(viewModel.favoriteState, FavoriteUiState.Error)
        assertTakenBack(CONVERSATION.copy(favorite = true))
    }

    @Test
    fun `an unfavorite the worker gives up on is taken back`() {
        val favorite = CONVERSATION.copy(favorite = true)
        givenTheWorker(gotThrough = false)
        wheneverBlocking { repository.getLocallyStoredConversation(any(), any()) }.thenReturn(null)

        viewModel.removeConversationFromFavorites(favorite)

        awaitFinal(viewModel.favoriteState, FavoriteUiState.Error)
        assertTakenBack(CONVERSATION, original = favorite)
        verify(conversationActionScheduler)
            .enqueue(USER.id!!, CONVERSATION.token, ConversationAction.Favorite(false))
    }

    @Test
    fun `marking read that the worker gives up on is taken back`() {
        val unread = CONVERSATION.copy(unreadMessages = UNREAD_MESSAGES)
        givenTheWorker(gotThrough = false)

        viewModel.markConversationAsRead(unread)

        awaitFinal(viewModel.readUnreadState, ConversationReadUnreadUiState.Error)
        assertTakenBack(unread.copy(unreadMessages = 0), original = unread)
        verify(conversationActionScheduler)
            .enqueue(USER.id!!, CONVERSATION.token, ConversationAction.MarkRead(null))
    }

    @Test
    fun `marking read hands the read marker to the worker and releases a pending unread`() {
        val unread = CONVERSATION.copy(
            unreadMessages = UNREAD_MESSAGES,
            lastMessage = ChatMessageDto(id = LAST_MESSAGE)
        )
        givenTheWorker(gotThrough = true)

        viewModel.markConversationAsRead(unread)

        assertEquals(
            ConversationReadUnreadUiState.Success,
            awaitOutcome(viewModel.readUnreadState, ConversationReadUnreadUiState.None)
        )
        verify(conversationListUpdater).switchToPendingMarkAsRead(CONVERSATION.internalId, LAST_MESSAGE.toInt())
        verify(conversationActionScheduler)
            .enqueue(USER.id!!, CONVERSATION.token, ConversationAction.MarkRead(LAST_MESSAGE.toInt()))
    }

    @Test
    fun `marking unread replaces a pending read marker`() {
        givenTheWorker(gotThrough = true)

        viewModel.markConversationAsUnread(CONVERSATION)

        assertEquals(
            ConversationReadUnreadUiState.Success,
            awaitOutcome(viewModel.readUnreadState, ConversationReadUnreadUiState.None)
        )
        verify(conversationListUpdater).switchToPendingMarkAsUnread(CONVERSATION.internalId)
    }

    @Test
    fun `marking unread that the worker gives up on is taken back`() {
        givenTheWorker(gotThrough = false)

        viewModel.markConversationAsUnread(CONVERSATION)

        awaitFinal(viewModel.readUnreadState, ConversationReadUnreadUiState.Error)
        assertTakenBack(CONVERSATION.copy(unreadMessages = 1))
        verify(conversationActionScheduler)
            .enqueue(USER.id!!, CONVERSATION.token, ConversationAction.MarkUnread)
    }

    @Test
    fun `an archive the worker gives up on is taken back`() {
        givenTheWorker(gotThrough = false)

        viewModel.toggleConversationArchive(CONVERSATION)

        awaitFinal(viewModel.archiveState, ArchiveUiState.Error)
        assertTakenBack(CONVERSATION.copy(hasArchived = true))
        verify(conversationActionScheduler)
            .enqueue(USER.id!!, CONVERSATION.token, ConversationAction.Archive(true))
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

    @Test
    fun `a failed favorite does not undo an archive that succeeded meanwhile`() {
        val archivedSinceTheSnapshot = CONVERSATION.copy(favorite = true, hasArchived = true)
        wheneverBlocking { repository.getLocallyStoredConversation(eq(USER), eq(CONVERSATION.token)) }
            .thenReturn(archivedSinceTheSnapshot)

        runBlocking { viewModel.restore(CONVERSATION, ConversationAction.Favorite(true)) }

        verifyBlocking(repository) { updateConversation(CONVERSATION.copy(hasArchived = true)) }
    }

    @Test
    fun `a failed archive puts back only the archive flag`() {
        val unreadSinceTheSnapshot = CONVERSATION.copy(hasArchived = true, unreadMessages = UNREAD_MESSAGES)
        wheneverBlocking { repository.getLocallyStoredConversation(eq(USER), eq(CONVERSATION.token)) }
            .thenReturn(unreadSinceTheSnapshot)

        runBlocking { viewModel.restore(CONVERSATION, ConversationAction.Archive(true)) }

        verifyBlocking(repository) { updateConversation(CONVERSATION.copy(unreadMessages = UNREAD_MESSAGES)) }
    }

    @Test
    fun `a conversation that is no longer stored is put back as it was captured`() {
        wheneverBlocking { repository.getLocallyStoredConversation(eq(USER), eq(CONVERSATION.token)) }
            .thenReturn(null)

        runBlocking { viewModel.restore(CONVERSATION, ConversationAction.MarkUnread) }

        verifyBlocking(repository) { updateConversation(CONVERSATION) }
    }

    /** The worker either got the change through or finally gave up on it; there is nothing in between. */
    private fun givenTheWorker(gotThrough: Boolean) {
        conversationActionScheduler.stub {
            on { enqueue(any(), any(), any()) } doReturn WORK_ID
            onBlocking { awaitWorkerGaveUp(WORK_ID) } doReturn !gotThrough
        }
    }

    /** Waits for the state the worker's answer finally produces, and times out when it never does. */
    private fun <S> awaitFinal(state: StateFlow<S>, wanted: S) {
        runBlocking { withTimeout(TIMEOUT_MS) { state.first { it == wanted } } }
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

    companion object {
        private const val LAST_MESSAGE = 42L
        private val WORK_ID: UUID = UUID.randomUUID()
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
