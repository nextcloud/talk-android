/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.conversationlist

import android.app.Application
import android.content.Intent
import android.net.Uri
import com.nextcloud.talk.conversationlist.ConversationsListActivity.Companion.intentOfAccount
import com.nextcloud.talk.conversationlist.ConversationsListActivity.Companion.isShareOfOtherApp
import com.nextcloud.talk.conversationlist.ConversationsListActivity.Companion.isViewOfOtherApp
import com.nextcloud.talk.conversationlist.ConversationsListActivity.Companion.removeExtrasOfApp
import com.nextcloud.talk.utils.AppTasks
import com.nextcloud.talk.utils.InternalEntryPoint
import com.nextcloud.talk.utils.bundle.BundleKeys
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** The conversation list is exported: only the app itself, through its internal entry point, may set its extras. */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [33])
class ConversationsListEntryPointTest {

    private val context = RuntimeEnvironment.getApplication()

    private fun withExtrasOfApp(intent: Intent) =
        intent
            .putExtra(BundleKeys.KEY_INTERNAL_USER_ID, 1L)
            .putExtra(BundleKeys.KEY_ROOM_TOKEN, "abcd1234")
            .putExtra(BundleKeys.KEY_FORWARD_MSG_FLAG, true)
            .putExtra(BundleKeys.KEY_FORWARD_MSG_TEXT, "forwarded")
            .putExtra(BundleKeys.KEY_FORWARD_HIDE_SOURCE_ROOM, "efgh5678")

    @Test
    fun `the extras of the app are removed from an intent of another app`() {
        val intent = withExtrasOfApp(Intent(context, ConversationsListActivity::class.java))
            .putExtra(Intent.EXTRA_TEXT, "shared")

        removeExtrasOfApp(intent)

        assertEquals(setOf(Intent.EXTRA_TEXT), intent.extras?.keySet())
    }

    @Test
    fun `the extras of the app are kept on an intent of the app itself`() {
        val intent = withExtrasOfApp(ConversationsListActivity.createIntent(context))

        removeExtrasOfApp(intent)

        assertEquals(1L, intent.getLongExtra(BundleKeys.KEY_INTERNAL_USER_ID, 0L))
        assertEquals("abcd1234", intent.getStringExtra(BundleKeys.KEY_ROOM_TOKEN))
        assertTrue(intent.getBooleanExtra(BundleKeys.KEY_FORWARD_MSG_FLAG, false))
        assertEquals("forwarded", intent.getStringExtra(BundleKeys.KEY_FORWARD_MSG_TEXT))
        assertEquals("efgh5678", intent.getStringExtra(BundleKeys.KEY_FORWARD_HIDE_SOURCE_ROOM))
    }

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

    @Test
    fun `a new intent of another app keeps the account shown when the list is recreated`() {
        val newIntent = intentOfOtherApp("com.nextcloud.intent.OPEN_ECOSYSTEM_APP")
        removeExtrasOfApp(newIntent)

        val kept = intentOfAccount(context, newIntent, userId = 2L)
        removeExtrasOfApp(kept)

        assertEquals(2L, kept.getLongExtra(BundleKeys.KEY_INTERNAL_USER_ID, 0L))
        assertEquals("com.nextcloud.intent.OPEN_ECOSYSTEM_APP", kept.action)
    }

    @Test
    fun `a deep link of another app to the launcher activity is taken through DeepLinkActivity`() {
        val deepLink = Uri.parse("nextcloudtalk://alice@cloud.example.com/call/abcd1234")
        val pinnedShortcut = Intent(Intent.ACTION_VIEW, deepLink).setClassName(context, AppTasks.LAUNCHER_ACTIVITY)

        assertTrue(isViewOfOtherApp(pinnedShortcut))
    }

    @Test
    fun `a deep link passed on by DeepLinkActivity and a VIEW intent without uri stay in the list`() {
        val passedOn = ConversationsListActivity.createIntent(context)
            .setAction(Intent.ACTION_VIEW)
            .setData(Uri.parse("nextcloudtalk://alice@cloud.example.com/call/abcd1234"))

        assertFalse(isViewOfOtherApp(passedOn))
        assertFalse(isViewOfOtherApp(intentOfOtherApp(Intent.ACTION_VIEW)))
    }

    @Test
    fun `a deep link task relaunched from the recents before Android 10 isn't passed on again`() {
        // before Android 10, the intent of the task names the target of the internal alias it was started through
        val relaunched = Intent(Intent.ACTION_VIEW, Uri.parse("nextcloudtalk://alice@cloud.example.com/call/abcd1234"))
            .setClassName(context, ConversationsListActivity::class.java.name)
            .addFlags(Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY)

        assertFalse(isViewOfOtherApp(relaunched))
    }

    @Test
    fun `the deep link of another app isn't kept as one of the app when the list shows its intent`() {
        val deepLink = Uri.parse("nextcloudtalk://alice@cloud.example.com/call/abcd1234")
        val relaunched = Intent(Intent.ACTION_VIEW, deepLink)
            .setClassName(context, AppTasks.LAUNCHER_ACTIVITY)
            .addFlags(Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY)
        val passedOn = ConversationsListActivity.createIntent(context).setAction(Intent.ACTION_VIEW).setData(deepLink)

        assertNull(intentOfAccount(context, relaunched, userId = 2L).data)
        assertEquals(deepLink, intentOfAccount(context, passedOn, userId = 2L).data)
    }

    @Test
    fun `a new back stack has the list of the account below its screen, which removes the tasks left in the recents`() {
        val invitations = Intent().setClassName(context, "com.nextcloud.talk.invitation.InvitationsActivity")

        val (list, screen) = ConversationsListActivity.createBackStack(context, 2L, invitations)

        assertEquals(InternalEntryPoint.nameOf(ConversationsListActivity::class.java), list.component?.className)
        assertEquals(2L, list.getLongExtra(BundleKeys.KEY_INTERNAL_USER_ID, 0L))
        assertFalse(list.getBooleanExtra(BundleKeys.KEY_TOP_OF_NEW_BACK_STACK, false))
        assertTrue(screen.getBooleanExtra(BundleKeys.KEY_TOP_OF_NEW_BACK_STACK, false))
    }
}
