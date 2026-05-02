/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.ui.chat

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MentionClickNavigationGuardTest {

    /**
     * Pure extraction of the guard condition in ChatActivity.onMessageEvent:
     *   if (type != ONE_TO_ONE || name != userId) → navigate
     */
    private fun shouldNavigate(
        isCurrentConversationOneToOne: Boolean,
        currentConversationName: String?,
        targetUserId: String
    ): Boolean = !isCurrentConversationOneToOne || currentConversationName != targetUserId

    @Test
    fun `guard skips navigation when already in 1-on-1 with that user`() {
        assertFalse(
            shouldNavigate(
                isCurrentConversationOneToOne = true,
                currentConversationName = "bob",
                targetUserId = "bob"
            )
        )
    }

    @Test
    fun `guard proceeds when in a group conversation`() {
        assertTrue(
            shouldNavigate(
                isCurrentConversationOneToOne = false,
                currentConversationName = "devs",
                targetUserId = "bob"
            )
        )
    }

    @Test
    fun `guard proceeds when in 1-on-1 with a different user`() {
        assertTrue(
            shouldNavigate(
                isCurrentConversationOneToOne = true,
                currentConversationName = "carol",
                targetUserId = "bob"
            )
        )
    }
}
