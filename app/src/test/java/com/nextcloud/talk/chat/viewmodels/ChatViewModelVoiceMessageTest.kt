/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.chat.viewmodels

import com.nextcloud.talk.chat.data.model.ChatMessage
import com.nextcloud.talk.data.user.model.User
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.RETURNS_DEEP_STUBS
import org.mockito.kotlin.mock
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The voice message to play is the one tapped last. A download or a waveform of another message that finishes later
 * updates only how that message is shown: it must not become the message whose progress the player reports.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class ChatViewModelVoiceMessageTest {

    @Test
    fun `updating another message does not select it for playback`() {
        val viewModel = newViewModel()
        val tapped = ChatMessage().apply { jsonMessageId = 1 }
        val downloaded = ChatMessage().apply { jsonMessageId = 2 }
        viewModel.currentVoiceMessage = tapped

        viewModel.syncVoiceMessageUiState(downloaded)

        assertSame(tapped, viewModel.currentVoiceMessage)
    }

    private fun newViewModel(): ChatViewModel =
        ChatViewModel(
            logger = mock(),
            appPreferences = mock(defaultAnswer = RETURNS_DEEP_STUBS),
            chatNetworkDataSource = mock(defaultAnswer = RETURNS_DEEP_STUBS),
            chatRepository = mock(defaultAnswer = RETURNS_DEEP_STUBS),
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
}
