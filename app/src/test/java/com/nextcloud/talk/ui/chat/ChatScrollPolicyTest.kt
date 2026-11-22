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

class ChatScrollPolicyTest {
    @Test
    fun `receiving a message must not cancel a drag or fling near the bottom`() {
        assertFalse(shouldFollowNewMessage(isNearNewest = true, isScrolling = true))
    }

    @Test
    fun `follow a new message when resting at the bottom`() {
        assertTrue(shouldFollowNewMessage(isNearNewest = true, isScrolling = false))
    }

    @Test
    fun `keep the reading position when browsing older messages`() {
        assertFalse(shouldFollowNewMessage(isNearNewest = false, isScrolling = false))
        assertFalse(shouldFollowNewMessage(isNearNewest = false, isScrolling = true))
    }

    @Test
    fun `removing the newest message must not count as an incoming message`() {
        assertFalse(isNewerMessage(previousId = 120, newestId = 119))
    }

    @Test
    fun `an unsent local message must not trigger incoming auto scroll`() {
        assertFalse(isNewerMessage(previousId = 120, newestId = -1))
    }

    @Test
    fun `initial load and read receipt updates must not trigger incoming auto scroll`() {
        assertFalse(isNewerMessage(previousId = null, newestId = 120))
        assertFalse(isNewerMessage(previousId = 120, newestId = null))
        assertFalse(isNewerMessage(previousId = 120, newestId = 120))
        assertTrue(isNewerMessage(previousId = 120, newestId = 121))
    }
}
