/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.utils

import android.app.Application
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [33])
class ShareUtilsTest {

    @Test
    fun `builds pretty URL when capability is available`() {
        val link = ShareUtils.buildConversationLink(BASE_URL, ROOM_TOKEN, canGeneratePrettyURL = true)
        assertEquals("$BASE_URL/call/$ROOM_TOKEN", link)
    }

    @Test
    fun `builds index_php URL when pretty URL capability is missing`() {
        val link = ShareUtils.buildConversationLink(BASE_URL, ROOM_TOKEN, canGeneratePrettyURL = false)
        assertEquals("$BASE_URL/index.php/call/$ROOM_TOKEN", link)
    }

    @Test
    fun `link is the bare URL with no extra text`() {
        val link = ShareUtils.buildConversationLink(BASE_URL, ROOM_TOKEN, canGeneratePrettyURL = true)
        // The shared text must be only the URL — no "Join conversation … at …" wrapper.
        assertEquals("$BASE_URL/call/$ROOM_TOKEN", link)
    }

    @Test
    fun `returns null when baseUrl is blank`() {
        assertNull(ShareUtils.buildConversationLink("", ROOM_TOKEN, canGeneratePrettyURL = true))
        assertNull(ShareUtils.buildConversationLink(null, ROOM_TOKEN, canGeneratePrettyURL = true))
    }

    @Test
    fun `returns null when roomToken is blank`() {
        assertNull(ShareUtils.buildConversationLink(BASE_URL, "", canGeneratePrettyURL = true))
        assertNull(ShareUtils.buildConversationLink(BASE_URL, null, canGeneratePrettyURL = true))
    }

    companion object {
        private const val BASE_URL = "https://server.example.com"
        private const val ROOM_TOKEN = "abc123"
    }
}
