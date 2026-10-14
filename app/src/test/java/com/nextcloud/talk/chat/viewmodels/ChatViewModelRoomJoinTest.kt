/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.chat.viewmodels

import android.app.Application
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import com.nextcloud.talk.chat.data.ChatMessageRepository
import com.nextcloud.talk.chat.data.io.AudioFocusRequestManager
import com.nextcloud.talk.chat.data.io.MediaRecorderManager
import com.nextcloud.talk.chat.data.network.ChatNetworkDataSource
import com.nextcloud.talk.conversationlist.data.OfflineConversationsRepository
import com.nextcloud.talk.conversationlist.data.network.OfflineFirstConversationsRepository.ConversationResult
import com.nextcloud.talk.data.user.model.User
import com.nextcloud.talk.logger.Logger
import com.nextcloud.talk.models.domain.ConversationModel
import com.nextcloud.talk.models.json.capabilities.CapabilitiesDto
import com.nextcloud.talk.models.json.capabilities.SpreedCapabilityDto
import com.nextcloud.talk.models.json.conversations.ConversationDto
import com.nextcloud.talk.models.json.conversations.ConversationEnums
import com.nextcloud.talk.models.json.generic.GenericMetaDto
import com.nextcloud.talk.models.json.generic.GenericOCS
import com.nextcloud.talk.models.json.generic.GenericOverall
import com.nextcloud.talk.models.json.participants.ParticipantDto
import com.nextcloud.talk.repositories.reactions.ReactionsRepository
import com.nextcloud.talk.repositories.unifiedsearch.UnifiedSearchRepository
import com.nextcloud.talk.threadsoverview.data.ThreadsRepository
import com.nextcloud.talk.users.UserManager
import com.nextcloud.talk.utils.preferences.AppPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.Timeout
import org.junit.runner.RunWith
import org.mockito.Mockito
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.mockito.kotlin.wheneverBlocking
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The chat joins the room once per resume of its screen and leaves it on pause. A refresh of the conversation
 * must not join the room: it is not tied to the screen being shown.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [33])
class ChatViewModelRoomJoinTest {

    @get:Rule
    val instantExecutorRule = InstantTaskExecutorRule()

    @get:Rule
    val timeout: Timeout = Timeout.seconds(TIMEOUT_SECONDS)

    private val conversation = MutableStateFlow<ConversationResult>(ConversationResult.Found(conversationModel(1)))
    private val owner = TestOwner()

    private lateinit var viewModel: ChatViewModel
    private var capabilitiesState: ChatViewModel.ViewState? = null
    private var initialLoads = 0
    private var joins = 0
    private var screenResumed = false

    private class TestOwner : LifecycleOwner {
        private val registry = LifecycleRegistry(this)
        override val lifecycle: Lifecycle get() = registry

        fun send(vararg events: Lifecycle.Event) = events.forEach { registry.handleLifecycleEvent(it) }
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())

        val userManager: UserManager = mock()
        whenever(userManager.userFlow(USER.id!!)).thenReturn(MutableStateFlow(USER))
        val conversations: OfflineConversationsRepository = mockWithEmptyFlows()
        whenever(conversations.observeConversation(USER.id!!, TOKEN)).thenReturn(conversation)
        val network: ChatNetworkDataSource = mock()
        wheneverBlocking { network.leaveRoom(any(), any()) }.thenReturn(
            GenericOverall(GenericOCS(GenericMetaDto(statusCode = HTTP_OK)))
        )
        val chatRepository: ChatMessageRepository = mockWithEmptyFlows()

        viewModel = ChatViewModel(
            mock<Logger>(),
            mock<AppPreferences>(),
            network,
            chatRepository,
            mock<ThreadsRepository>(),
            conversations,
            mock<ReactionsRepository>(),
            mock<UnifiedSearchRepository>(),
            mock<MediaRecorderManager>(),
            mock<AudioFocusRequestManager>(),
            userManager,
            CoroutineScope(UnconfinedTestDispatcher()),
            USER,
            TOKEN,
            null
        )
        owner.lifecycle.addObserver(viewModel)

        // what ChatActivity does with the state of the capabilities and with a resume of the screen
        viewModel.getCapabilitiesViewState.observeForever { state ->
            capabilitiesState = state
            if (state is ChatViewModel.GetCapabilitiesInitialLoadState) {
                initialLoads++
                if (screenResumed && viewModel.claimRoomJoin()) {
                    joins++
                }
            }
        }
    }

    private inline fun <reified T : Any> mockWithEmptyFlows(): T =
        mock(
            defaultAnswer = { invocation ->
                if (invocation.method.returnType == Flow::class.java) {
                    emptyFlow<Any>()
                } else {
                    Mockito.RETURNS_DEFAULTS.answer(invocation)
                }
            }
        )

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun resumeScreen(conversationLoaded: Boolean) {
        owner.send(Lifecycle.Event.ON_START, Lifecycle.Event.ON_RESUME)
        screenResumed = true
        if (conversationLoaded && viewModel.claimRoomJoin()) {
            joins++
        }
    }

    private fun pauseScreen() {
        screenResumed = false
        owner.send(Lifecycle.Event.ON_PAUSE)
    }

    private fun leaveRoom() {
        viewModel.leaveRoom("credentials", "https://server.example.com/leave", null)
    }

    private fun refreshConversation(lastReadMessage: Int) {
        conversation.value = ConversationResult.Found(conversationModel(lastReadMessage))
    }

    @Test
    fun `a refresh of the conversation after leaving the room is not an initial load`() {
        owner.send(Lifecycle.Event.ON_CREATE)
        resumeScreen(conversationLoaded = false)
        viewModel.initData()
        assertEquals(1, joins)

        pauseScreen()
        leaveRoom()
        refreshConversation(lastReadMessage = 2)

        assertFalse(capabilitiesState is ChatViewModel.GetCapabilitiesInitialLoadState)
        assertEquals(1, initialLoads)
        assertEquals(1, joins)
    }

    @Test
    fun `leaving, saving the state without a configuration change and returning joins once`() {
        owner.send(Lifecycle.Event.ON_CREATE)
        resumeScreen(conversationLoaded = false)
        viewModel.initData()
        assertEquals(1, joins)

        pauseScreen()
        leaveRoom()
        refreshConversation(lastReadMessage = 2)
        owner.send(Lifecycle.Event.ON_STOP)
        // onSaveInstanceState of a stopped screen that is not rotating does not touch the state of the viewmodel

        resumeScreen(conversationLoaded = true)
        refreshConversation(lastReadMessage = 3)
        refreshConversation(lastReadMessage = 4)

        assertEquals(2, joins)
        assertEquals(1, initialLoads)
        assertTrue(capabilitiesState is ChatViewModel.GetCapabilitiesUpdateState)
    }

    @Test
    fun `a second claim in the same resume does not join again`() {
        resumeScreen(conversationLoaded = true)

        assertFalse(viewModel.claimRoomJoin())
        assertEquals(1, joins)
    }

    private fun conversationModel(lastReadMessage: Int): ConversationModel =
        ConversationModel.mapToConversationModel(
            ConversationDto(
                token = TOKEN,
                name = "room",
                displayName = "room",
                description = "",
                type = ConversationEnums.ConversationType.ROOM_GROUP_CALL,
                participantType = ParticipantDto.ParticipantType.USER,
                sessionId = "0",
                actorId = "me",
                actorType = "users",
                objectType = ConversationEnums.ObjectType.DEFAULT,
                notificationLevel = ConversationEnums.NotificationLevel.DEFAULT,
                conversationReadOnlyState = ConversationEnums.ConversationReadOnlyState.CONVERSATION_READ_WRITE,
                lobbyState = ConversationEnums.LobbyState.LOBBY_STATE_ALL_PARTICIPANTS,
                lobbyTimer = 0,
                lastReadMessage = lastReadMessage,
                canLeaveConversation = true,
                canDeleteConversation = true,
                unreadMentionDirect = false,
                notificationCalls = 0,
                avatarVersion = "",
                hasCustomAvatar = false,
                callStartTime = 0
            ),
            USER
        )

    companion object {
        private const val TOKEN = "room-token"
        private const val HTTP_OK = 200
        private const val TIMEOUT_SECONDS = 60L
        private val USER = User(
            id = 1,
            userId = "me",
            username = "me",
            token = "app-password",
            baseUrl = "https://server.example.com",
            capabilities = CapabilitiesDto().apply { spreedCapability = SpreedCapabilityDto() }
        )
    }
}
