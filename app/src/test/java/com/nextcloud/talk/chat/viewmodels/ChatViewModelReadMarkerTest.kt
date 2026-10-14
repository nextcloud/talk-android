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
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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
 * The read marker path of the real [ChatViewModel] when the chat is left: updateRemoteLastReadMessageIfNeeded ->
 * local read state in the repository -> ReadMarkerSyncWorker. The repository is a fake that keeps the stored
 * read marker like Room does: the view model sees it as conversation.lastReadMessage afterwards.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class ChatViewModelReadMarkerTest {

    private val calls = mutableListOf<Pair<String, Any?>>()
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
        viewModel.advanceLocalLastReadMessageIfNeeded(LOCAL_MARKER)
        storedMarker(STORED_MARKER)
    }

    @After
    fun tearDown() {
        sharedApplicationField.set(null, previousApplication)
    }

    @Test
    fun `leaving the chat with an own message queued still updates the local read state`() {
        messages(ownMessage(-77, SendStatus.PENDING), incomingMessage(LOCAL_MARKER))

        viewModel.updateRemoteLastReadMessageIfNeeded()
        shadowOf(Looper.getMainLooper()).idle()

        assertTrue("local read state written: $calls", calls.any { it.first.startsWith("updateLocal") })
        assertEquals("no marker is sent to the server", 0, queuedMarkers())
    }

    @Test
    fun `a marker held back by a queued own message is sent once that message failed`() {
        messages(ownMessage(-77, SendStatus.PENDING), incomingMessage(LOCAL_MARKER))
        viewModel.updateRemoteLastReadMessageIfNeeded()
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(0, queuedMarkers())

        messages(ownMessage(-77, SendStatus.FAILED), incomingMessage(LOCAL_MARKER))
        viewModel.updateRemoteLastReadMessageIfNeeded()
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals("the marker reaches the server after the failure", 1, queuedMarkers())
    }

    @Test
    fun `a confirmed own message moves the marker to the message and sends it`() {
        messages(ownMessage(OWN_MESSAGE_ID, null), incomingMessage(LOCAL_MARKER))

        viewModel.updateRemoteLastReadMessageIfNeeded()
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals("marker is sent", 1, queuedMarkers())
        assertEquals("never below the own message", OWN_MESSAGE_ID, storedMarkerValue)
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
        calls.add(name to invocation.arguments.firstOrNull())
        if (name == "updateLocalReadState") {
            storedMarker(invocation.arguments[0] as Int)
            return Unit
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
    }
}
