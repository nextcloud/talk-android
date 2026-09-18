/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.jobs

import com.nextcloud.talk.jobs.ConversationActionWorker.ConversationAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * What the enqueued work is named decides which change supersedes which, and the action travels to
 * the worker as a string. Both are easy to break by renaming and hard to notice at runtime, because
 * a change that is dropped looks exactly like one the server refused.
 */
class ConversationActionWorkerTest {

    @Test
    fun `favoriting the same conversation twice replaces the older intent`() {
        assertEquals(
            ConversationActionWorker.uniqueWorkName(1, "room1", ConversationAction.FAVORITE),
            ConversationActionWorker.uniqueWorkName(1, "room1", ConversationAction.FAVORITE)
        )
    }

    @Test
    fun `archiving a conversation does not replace marking it unread`() {
        assertNotEquals(
            ConversationActionWorker.uniqueWorkName(1, "room1", ConversationAction.ARCHIVE),
            ConversationActionWorker.uniqueWorkName(1, "room1", ConversationAction.MARK_UNREAD)
        )
    }

    @Test
    fun `the same conversation token on two accounts is two changes`() {
        assertNotEquals(
            ConversationActionWorker.uniqueWorkName(1, "room1", ConversationAction.ARCHIVE),
            ConversationActionWorker.uniqueWorkName(2, "room1", ConversationAction.ARCHIVE)
        )
    }

    @Test
    fun `every action survives the trip through the work input`() {
        ConversationAction.entries.forEach { action ->
            assertEquals(action, ConversationActionWorker.actionOf(action.name))
        }
    }

    @Test
    fun `an action the worker does not know is dropped rather than guessed`() {
        assertNull(ConversationActionWorker.actionOf("SOMETHING_ELSE"))
        assertNull(ConversationActionWorker.actionOf(null))
    }
}
