/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.chat.viewmodels

import android.os.Looper
import androidx.work.WorkManager
import androidx.work.testing.WorkManagerTestInitHelper
import com.nextcloud.talk.application.NextcloudTalkApplication
import com.nextcloud.talk.chat.data.ChatMessageRepository
import com.nextcloud.talk.chat.data.model.ChatMessage
import com.nextcloud.talk.data.database.model.SendStatus
import com.nextcloud.talk.data.user.model.User
import com.nextcloud.talk.models.domain.ConversationModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito
import org.mockito.Mockito.RETURNS_DEEP_STUBS
import org.mockito.invocation.InvocationOnMock
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * The read marker path of the real [ChatViewModel] once the newest message was seen: onNewestMessageSeen ->
 * local read state in the repository -> ReadMarkerSyncWorker. The repository is a fake that keeps the stored
 * read marker like Room does: the view model sees it as conversation.lastReadMessage afterwards.
 */
@Suppress("TooManyFunctions")
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class ChatViewModelReadMarkerTest {

    private lateinit var viewModel: ChatViewModel

    // the setter of the singleton is protected: only the real application sets it
    private val sharedApplicationField = NextcloudTalkApplication::class.java
        .getDeclaredField("sharedApplication")
        .apply { isAccessible = true }
    private var previousApplication: NextcloudTalkApplication? = null

    @Before
    fun setUp() {
        val context = RuntimeEnvironment.getApplication()
        WorkManagerTestInitHelper.initializeTestWorkManager(context)
        previousApplication = sharedApplicationField.get(null) as NextcloudTalkApplication?
        val application = mock<NextcloudTalkApplication>()
        whenever(application.applicationContext).thenReturn(context)
        sharedApplicationField.set(null, application)

        viewModel = newViewModel()
        storedMarker(STORED_MARKER)
    }

    @After
    fun tearDown() {
        sharedApplicationField.set(null, previousApplication)
    }

    @Test
    fun `an own message on its way does not hold the marker back`() {
        messages(ownMessage(-77, SendStatus.PENDING), incomingMessage(LOCAL_MARKER))

        viewModel.onNewestMessageSeen(LOCAL_MARKER)
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals("marker is sent", 1, queuedMarkers())
        assertEquals(LOCAL_MARKER, storedMarkerValue)
    }

    @Test
    fun `a confirmed own message moves the marker to the message and sends it`() {
        messages(ownMessage(OWN_MESSAGE_ID, null), incomingMessage(LOCAL_MARKER))

        viewModel.onNewestMessageSeen(LOCAL_MARKER)
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals("marker is sent", 1, queuedMarkers())
        assertEquals("never below the own message", OWN_MESSAGE_ID, storedMarkerValue)
    }

    @Test
    fun `a window around an old message does not make the view model forget the own message just sent`() {
        messages(ownMessage(OWN_MESSAGE_ID, null), incomingMessage(LOCAL_MARKER))
        openOldMessageWindow(incomingMessage(OLD_MESSAGE_ID))

        viewModel.onNewestMessageSeen(LOCAL_MARKER)
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals("marker is sent", 1, queuedMarkers())
        assertEquals("never below the own message", OWN_MESSAGE_ID, storedMarkerValue)
    }

    // the chat shows a window around a quoted or searched message that is far from the newest messages;
    // the database then delivers that window instead of the newest messages
    private fun openOldMessageWindow(vararg window: ChatMessage) {
        fun field(name: String) = ChatViewModel::class.java.getDeclaredField(name).apply { isAccessible = true }

        @Suppress("UNCHECKED_CAST")
        (field("contextAnchorMessageId").get(viewModel) as MutableStateFlow<Long?>).value = OLD_MESSAGE_ID.toLong()
        @Suppress("UNCHECKED_CAST")
        (field("_chatMode").get(viewModel) as MutableStateFlow<ChatViewModel.ChatMode>).value =
            ChatViewModel.ChatMode.OLD_CHATBLOCK_MODE
        ChatViewModel::class.java.getDeclaredMethod("onMessagesChanged", List::class.java)
            .apply { isAccessible = true }
            .invoke(viewModel, window.toList())
    }

    private var storedMarkerValue = STORED_MARKER

    // what Room does: the conversation entry carries the stored marker, the view model observes it
    private fun storedMarker(value: Int) {
        storedMarkerValue = value
        val conversation = mock<ConversationModel>()
        whenever(conversation.lastReadMessage).thenReturn(value)
        @Suppress("UNCHECKED_CAST")
        val state = viewModel.uiState as MutableStateFlow<ChatViewModel.ChatUiState>
        state.value = state.value.copy(conversation = conversation)
    }

    private fun messages(vararg messages: ChatMessage) {
        ChatViewModel::class.java.getDeclaredField("latestMessages")
            .apply { isAccessible = true }
            .set(viewModel, messages.toList())
    }

    private fun queuedMarkers(): Int =
        WorkManager.getInstance(RuntimeEnvironment.getApplication())
            .getWorkInfosForUniqueWork("read-marker-sync-1@room")
            .get()
            .size

    private fun repositoryAnswer(invocation: InvocationOnMock): Any? {
        val name = invocation.method.name
        if (name == "updateLocalReadState") {
            storedMarker(invocation.arguments[0] as Int)
            return Unit
        }
        // a null flow would make the collectors of the view model fail after the test, in viewModelScope
        if (Flow::class.java.isAssignableFrom(invocation.method.returnType)) {
            return emptyFlow<Any>()
        }
        return Mockito.RETURNS_DEFAULTS.answer(invocation)
    }

    private fun newViewModel(): ChatViewModel =
        ChatViewModel(
            logger = mock(),
            appPreferences = mock(defaultAnswer = RETURNS_DEEP_STUBS),
            chatNetworkDataSource = mock(defaultAnswer = RETURNS_DEEP_STUBS),
            chatRepository = Mockito.mock(ChatMessageRepository::class.java) { repositoryAnswer(it) },
            threadsRepository = mock(defaultAnswer = RETURNS_DEEP_STUBS),
            conversationRepository = mock(defaultAnswer = RETURNS_DEEP_STUBS),
            reactionsRepository = mock(defaultAnswer = RETURNS_DEEP_STUBS),
            unifiedSearchRepository = mock(defaultAnswer = RETURNS_DEEP_STUBS),
            mediaRecorderManager = mock(defaultAnswer = RETURNS_DEEP_STUBS),
            audioFocusRequestManager = mock(defaultAnswer = RETURNS_DEEP_STUBS),
            userManager = mock(defaultAnswer = RETURNS_DEEP_STUBS),
            appScope = CoroutineScope(Dispatchers.Unconfined),
            initialUser = User().apply { id = 1L },
            chatRoomToken = "room",
            conversationThreadId = null
        )

    private fun incomingMessage(id: Int): ChatMessage =
        ChatMessage().apply {
            jsonMessageId = id
            incoming = true
        }

    private fun ownMessage(id: Int, status: SendStatus?): ChatMessage =
        ChatMessage().apply {
            jsonMessageId = id
            incoming = false
            isTemporary = id < 0
            sendStatus = status
        }

    companion object {
        private const val STORED_MARKER = 2620
        private const val LOCAL_MARKER = 2623
        private const val OWN_MESSAGE_ID = 2624
        private const val OLD_MESSAGE_ID = 1500
    }
}
