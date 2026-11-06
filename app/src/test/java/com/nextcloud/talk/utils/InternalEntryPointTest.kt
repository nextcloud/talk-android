/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.utils

import com.nextcloud.talk.conversationlist.ConversationsListActivity
import org.junit.Assert.assertEquals
import org.junit.Test

class InternalEntryPointTest {

    // the activity-aliases declared in the manifest

    @Test
    fun nameOfConversationsListActivity() {
        assertEquals(
            "com.nextcloud.talk.conversationlist.InternalConversationsListActivity",
            InternalEntryPoint.nameOf(ConversationsListActivity::class.java)
        )
    }
}
