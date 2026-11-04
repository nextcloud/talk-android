/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.activities

import android.app.Application
import android.content.Intent
import android.net.Uri
import com.nextcloud.talk.activities.DeepLinkRoutingViewModel.Request
import com.nextcloud.talk.conversationlist.ConversationsListActivity
import com.nextcloud.talk.utils.InternalEntryPoint
import com.nextcloud.talk.utils.bundle.BundleKeys
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * DeepLinkActivity is exported: of the intent of another app, only its uri, type and read grant reach the conversation
 * list.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [33])
class DeepLinkActivityTest {

    private val context = RuntimeEnvironment.getApplication()

    private fun passOn(intent: Intent): Intent {
        val activity = Robolectric.buildActivity(DeepLinkActivity::class.java, intent).create().get()
        assertTrue(activity.isFinishing)
        return checkNotNull(shadowOf(context).nextStartedActivity)
    }

    private fun startedBy(intent: Intent): Intent? {
        val activity = Robolectric.buildActivity(DeepLinkActivity::class.java, intent).create().get()
        assertTrue(activity.isFinishing)
        return shadowOf(context).nextStartedActivity
    }

    private fun intentOfOtherApp(action: String, uri: Uri) =
        Intent(action, uri)
            .setClass(context, DeepLinkActivity::class.java)
            .putExtra(BundleKeys.KEY_INTERNAL_USER_ID, 1L)
            .putExtra(BundleKeys.KEY_ROOM_TOKEN, "abcd1234")
            .putExtra(BundleKeys.KEY_FORWARD_MSG_FLAG, true)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)

    @Test
    fun `only the uri, type and read grant of a deep link are passed on`() {
        val uri = Uri.parse("nextcloudtalk://alice@cloud.example.com/call/abcd1234")

        val passedOn = passOn(intentOfOtherApp(Intent.ACTION_VIEW, uri).setDataAndType(uri, "text/plain"))

        assertEquals(InternalEntryPoint.nameOf(ConversationsListActivity::class.java), passedOn.component?.className)
        assertEquals(Intent.ACTION_VIEW, passedOn.action)
        assertEquals(uri, passedOn.data)
        assertEquals("text/plain", passedOn.type)
        assertTrue(passedOn.extras?.isEmpty ?: true)
        assertTrue(passedOn.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
        assertEquals(0, passedOn.flags and Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        assertTrue(Request.of(passedOn) is Request.DeepLink)
    }

    @Test
    fun `the uri of an intent other than VIEW is not passed on`() {
        val uri = Uri.parse("nextcloudtalk://alice@cloud.example.com/call/abcd1234")

        val passedOn = passOn(intentOfOtherApp(Intent.ACTION_EDIT, uri))

        assertNull(passedOn.data)
        assertTrue(passedOn.extras?.isEmpty ?: true)
        // only opens the app
        assertNull(Request.of(passedOn))
    }

    @Test
    fun `a link that opens no conversation does nothing`() {
        val links = listOf(
            "nextcloudtalk://alice@cloud.example.com/call/",
            "nextcloudtalk://alice@cloud.example.com/call/not-a-token!",
            "nextcloudtalk:///call/abcd1234",
            "https://cloud.example.com/call/abcd1234",
            "content://com.android.contacts/data/1"
        )
        links.forEach { link ->
            assertNull(link, startedBy(intentOfOtherApp(Intent.ACTION_VIEW, Uri.parse(link))))
        }
        assertNull("without uri", startedBy(Intent(Intent.ACTION_VIEW).setClass(context, DeepLinkActivity::class.java)))
    }

    @Test
    fun `the chat action of a phone book contact is passed on`() {
        val uri = Uri.parse("content://com.android.contacts/data/1")
        val intent = intentOfOtherApp(Intent.ACTION_VIEW, uri)
            .setDataAndType(uri, "vnd.android.cursor.item/vnd.com.nextcloud.talk2.chat")

        assertEquals(Request.ContactChat(uri), Request.of(passOn(intent)))
    }
}
