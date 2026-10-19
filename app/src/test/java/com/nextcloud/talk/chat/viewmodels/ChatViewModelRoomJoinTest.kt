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
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
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
import io.reactivex.Observable
import io.reactivex.android.plugins.RxAndroidPlugins
import io.reactivex.plugins.RxJavaPlugins
import io.reactivex.schedulers.Schedulers
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
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
import org.junit.runner.RunWith
import org.mockito.Mockito
import org.mockito.kotlin.any
import org.mockito.kotlin.doSuspendableAnswer
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.mockito.kotlin.wheneverBlocking
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The chat joins the room once per resume of its screen and leaves it on pause. A refresh of the conversation
 * must not join the room: it is not tied to the screen being shown.
 */
@Suppress("TooManyFunctions")
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [33])
class ChatViewModelRoomJoinTest {

    @get:Rule
    val instantExecutorRule = InstantTaskExecutorRule()

    private val conversation = MutableStateFlow<ConversationResult>(ConversationResult.Found(conversationModel(1)))
    private val owner = TestOwner()
    private val network: ChatNetworkDataSource = mock()
    private var pendingLeave: CompletableDeferred<Unit>? = null

    private lateinit var viewModel: ChatViewModel
    private var capabilitiesState: ChatViewModel.ViewState? = null
    private var initialLoads = 0
    private var joins = 0
    private var screenResumed = false
    private var initialLoadDone = false
    private val viewModelStore = ViewModelStore()
    private val appScope = CoroutineScope(SupervisorJob() + UnconfinedTestDispatcher())

    private class TestOwner : LifecycleOwner {
        private val registry = LifecycleRegistry(this)
        override val lifecycle: Lifecycle get() = registry

        fun send(vararg events: Lifecycle.Event) = events.forEach { registry.handleLifecycleEvent(it) }
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        RxJavaPlugins.setIoSchedulerHandler { Schedulers.trampoline() }
        RxAndroidPlugins.setMainThreadSchedulerHandler { Schedulers.trampoline() }

        val userManager: UserManager = mock()
        whenever(userManager.userFlow(USER.id!!)).thenReturn(MutableStateFlow(USER))
        val conversations: OfflineConversationsRepository = mockWithEmptyFlows()
        whenever(conversations.observeConversation(USER.id!!, TOKEN)).thenReturn(conversation)
        wheneverBlocking { network.leaveRoom(any(), any()) }.doSuspendableAnswer {
            pendingLeave?.await()
            GenericOverall(GenericOCS(GenericMetaDto(statusCode = HTTP_OK)))
        }
        val chatRepository: ChatMessageRepository = mockWithEmptyFlows()

        val newViewModel = ChatViewModel(
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
            appScope,
            USER,
            TOKEN,
            null
        )
        viewModel = ViewModelProvider(
            viewModelStore,
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T = newViewModel as T
            }
        )[ChatViewModel::class.java]
        owner.lifecycle.addObserver(viewModel)

        // what ChatActivity does with the state of the capabilities and with a resume of the screen
        viewModel.getCapabilitiesViewState.observeForever { state ->
            capabilitiesState = state
            if (state is ChatViewModel.GetCapabilitiesInitialLoadState) {
                initialLoads++
                initialLoadDone = true
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
        owner.send(Lifecycle.Event.ON_DESTROY)
        viewModelStore.clear()
        appScope.cancel()
        RxJavaPlugins.reset()
        RxAndroidPlugins.reset()
        Dispatchers.resetMain()
    }

    private fun resumeScreen() {
        owner.send(Lifecycle.Event.ON_START, Lifecycle.Event.ON_RESUME)
        screenResumed = true
        if (initialLoadDone && viewModel.claimRoomJoin()) {
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
        resumeScreen()
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
        resumeScreen()
        viewModel.initData()
        assertEquals(1, joins)

        pauseScreen()
        leaveRoom()
        refreshConversation(lastReadMessage = 2)
        owner.send(Lifecycle.Event.ON_STOP)
        // onSaveInstanceState of a stopped screen that is not rotating does not touch the state of the viewmodel

        resumeScreen()
        refreshConversation(lastReadMessage = 3)
        refreshConversation(lastReadMessage = 4)

        assertEquals(2, joins)
        assertEquals(1, initialLoads)
        assertTrue(capabilitiesState is ChatViewModel.GetCapabilitiesUpdateState)
    }

    @Test
    fun `a configuration change gives the new screen one initial load and one join`() {
        owner.send(Lifecycle.Event.ON_CREATE)
        resumeScreen()
        viewModel.initData()
        assertEquals(1, joins)

        // the old screen is paused without leaving the room and saves its state while it is recreated
        pauseScreen()
        viewModel.handleOrientationChange()
        owner.send(Lifecycle.Event.ON_STOP)

        // the new screen collects the conversation again in onCreate and resumes
        initialLoadDone = false
        viewModel.initData()
        resumeScreen()
        refreshConversation(lastReadMessage = 2)

        assertEquals(2, initialLoads)
        assertEquals(2, joins)
        assertTrue(capabilitiesState is ChatViewModel.GetCapabilitiesUpdateState)
    }

    @Test
    fun `the room is claimed once per resume and the claim is released on pause`() {
        resumeScreen()

        assertTrue(viewModel.claimRoomJoin())
        assertFalse(viewModel.claimRoomJoin())

        pauseScreen()
        resumeScreen()

        assertTrue(viewModel.claimRoomJoin())
    }

    @Test
    fun `a return to the chat during the leave joins only after the leave`() {
        owner.send(Lifecycle.Event.ON_CREATE)
        resumeScreen()
        viewModel.initData()

        pendingLeave = CompletableDeferred()
        pauseScreen()
        leaveRoom()
        resumeScreen()
        viewModel.joinRoom(USER, TOKEN, "")

        verify(network, never()).joinRoom(any(), any(), any())

        pendingLeave!!.complete(Unit)

        verify(network, times(1)).joinRoom(any(), any(), any())
    }

    @Test
    fun `a pause while the join waits for the leave cancels the join`() {
        owner.send(Lifecycle.Event.ON_CREATE)
        resumeScreen()

        pendingLeave = CompletableDeferred()
        pauseScreen()
        leaveRoom()
        resumeScreen()
        viewModel.joinRoom(USER, TOKEN, "")
        pauseScreen()

        pendingLeave!!.complete(Unit)

        verify(network, never()).joinRoom(any(), any(), any())
    }

    @Test
    fun `a failed first load of federated capabilities is retried as the initial load`() {
        whenever(network.getCapabilities(any(), any())).thenReturn(
            Observable.error(RuntimeException("federated server not reachable")),
            Observable.error(RuntimeException("federated server not reachable")),
            Observable.just(SpreedCapabilityDto())
        )
        // the conversation flow ignores a conversation with an unchanged lastReadMessage
        conversation.value = ConversationResult.Found(conversationModel(5, remoteServer = REMOTE_SERVER))

        owner.send(Lifecycle.Event.ON_CREATE)
        resumeScreen()
        viewModel.initData()

        assertTrue(capabilitiesState is ChatViewModel.GetCapabilitiesErrorState)
        assertEquals(0, joins)

        conversation.value = ConversationResult.Found(conversationModel(6, remoteServer = REMOTE_SERVER))

        assertEquals(1, initialLoads)
        assertEquals(1, joins)
    }

    @Test
    fun `a failed refresh of federated capabilities after the initial load is not an initial load`() {
        whenever(network.getCapabilities(any(), any())).thenReturn(
            Observable.just(SpreedCapabilityDto()),
            Observable.just(SpreedCapabilityDto()),
            Observable.error(RuntimeException("federated server not reachable")),
            Observable.just(SpreedCapabilityDto())
        )
        // the conversation flow ignores a conversation with an unchanged lastReadMessage
        conversation.value = ConversationResult.Found(conversationModel(5, remoteServer = REMOTE_SERVER))

        owner.send(Lifecycle.Event.ON_CREATE)
        resumeScreen()
        viewModel.initData()
        assertEquals(1, initialLoads)

        conversation.value = ConversationResult.Found(conversationModel(6, remoteServer = REMOTE_SERVER))
        assertTrue(capabilitiesState is ChatViewModel.GetCapabilitiesErrorState)

        conversation.value = ConversationResult.Found(conversationModel(7, remoteServer = REMOTE_SERVER))

        assertEquals(1, initialLoads)
        assertTrue(capabilitiesState is ChatViewModel.GetCapabilitiesUpdateState)
    }

    private fun conversationModel(lastReadMessage: Int, remoteServer: String = ""): ConversationModel =
        ConversationModel.mapToConversationModel(
            ConversationDto(
                token = TOKEN,
                remoteServer = remoteServer,
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
        private const val REMOTE_SERVER = "https://remote.example.com"
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
