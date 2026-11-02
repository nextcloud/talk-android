/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.chat.audio

import android.app.Application
import android.content.Intent
import com.nextcloud.talk.chat.ChatActivity
import com.nextcloud.talk.conversationlist.ConversationsListActivity
import com.nextcloud.talk.utils.InternalEntryPoint
import com.nextcloud.talk.utils.bundle.BundleKeys
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** The chat of an audio message opens directly, not through the routing of the conversation list. */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [33])
class ChatAudioIntentsTest {

    private val context = RuntimeEnvironment.getApplication()

    private val key = ChatAudioKey(internalUserId = 2L, roomToken = "abcd1234", messageId = 42)

    private fun assertOpensMessage(intent: Intent) {
        assertEquals(2L, intent.getLongExtra(BundleKeys.KEY_INTERNAL_USER_ID, 0L))
        assertEquals("abcd1234", intent.getStringExtra(BundleKeys.KEY_ROOM_TOKEN))
        assertEquals("42", intent.getStringExtra(BundleKeys.KEY_MESSAGE_ID))
        assertEquals(7L, intent.getLongExtra(BundleKeys.KEY_THREAD_ID, 0L))
    }

    @Test
    fun `a screen of the app opens the chat of the message directly`() {
        val intent = key.openMessageIntent(context, threadId = 7L)

        assertEquals(ChatActivity::class.java.name, intent.component?.className)
        assertOpensMessage(intent)
    }

    @Test
    fun `the media notification opens the chat of the message above the conversation list of its account`() {
        val (list, chat) = key.openMessageBackStack(context, threadId = 7L)

        assertEquals(InternalEntryPoint.nameOf(ConversationsListActivity::class.java), list.component?.className)
        assertEquals(2L, list.getLongExtra(BundleKeys.KEY_INTERNAL_USER_ID, 0L))
        assertEquals(ChatActivity::class.java.name, chat.component?.className)
        assertOpensMessage(chat)
    }
}
