/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.conversationlist

import android.app.Application
import android.content.Intent
import com.nextcloud.talk.conversationlist.ConversationsListActivity.Companion.isShareOfOtherApp
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** The conversation list is exported: a share of another app is taken through ShareActivity. */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [33])
class ConversationsListEntryPointTest {

    private val context = RuntimeEnvironment.getApplication()

    // started like by another app, with the activity itself and not its internal entry point
    private fun intentOfOtherApp(action: String) =
        Intent(action).setClass(context, ConversationsListActivity::class.java)

    @Test
    fun `a share of another app is taken through ShareActivity`() {
        assertTrue(isShareOfOtherApp(intentOfOtherApp(Intent.ACTION_SEND)))
        assertTrue(isShareOfOtherApp(intentOfOtherApp(Intent.ACTION_SEND_MULTIPLE)))
    }

    @Test
    fun `a share passed on by ShareActivity and other intents stay in the list`() {
        assertFalse(isShareOfOtherApp(ConversationsListActivity.createIntent(context).setAction(Intent.ACTION_SEND)))
        assertFalse(isShareOfOtherApp(intentOfOtherApp(Intent.ACTION_VIEW)))
    }
}
