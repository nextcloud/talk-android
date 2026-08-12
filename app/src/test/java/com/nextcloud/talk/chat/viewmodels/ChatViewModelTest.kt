/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.chat.viewmodels

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatViewModelTest {

    @Test
    fun `isPlausibleLastReadMessageId returns true when there is no known real message id yet`() {
        assertTrue(ChatViewModel.isPlausibleLastReadMessageId(messageId = 158761, newestKnownRealMessageId = null))
    }

    @Test
    fun `isPlausibleLastReadMessageId returns true for the newest known real message id itself`() {
        assertTrue(ChatViewModel.isPlausibleLastReadMessageId(messageId = 158761, newestKnownRealMessageId = 158761L))
    }

    @Test
    fun `isPlausibleLastReadMessageId returns true for an older message id`() {
        assertTrue(ChatViewModel.isPlausibleLastReadMessageId(messageId = 100, newestKnownRealMessageId = 158761L))
    }

    @Test
    fun `isPlausibleLastReadMessageId returns true within the buffer above the newest known message id`() {
        assertTrue(
            ChatViewModel.isPlausibleLastReadMessageId(messageId = 158761 + 2000, newestKnownRealMessageId = 158761L)
        )
    }

    @Test
    fun `isPlausibleLastReadMessageId returns false just beyond the buffer above the newest known message id`() {
        assertFalse(
            ChatViewModel.isPlausibleLastReadMessageId(
                messageId = 158761 + 2000 + 1,
                newestKnownRealMessageId = 158761L
            )
        )
    }

    @Test
    fun `isPlausibleLastReadMessageId rejects a hash-derived placeholder id far beyond the real message id space`() {
        assertFalse(
            ChatViewModel.isPlausibleLastReadMessageId(messageId = 1_963_726_147, newestKnownRealMessageId = 158761L)
        )
    }
}
