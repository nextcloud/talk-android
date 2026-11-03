/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.utils

import android.app.Application
import android.net.Uri
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * A share intent hands over uris of any app. The log of a rejected uri must not carry the file name.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [33])
class FileUtilsShareUriLogTest {

    @Test
    fun logDescriptionHasNoPathAndNoQuery() {
        val text = FileUtils.describeShareUriForLog(Uri.parse("content://media/external/Holiday.jpg?name=Secret.pdf"))

        assertEquals("content://media userInfo=false", text)
        assertFalse(text.contains("Holiday"))
        assertFalse(text.contains("Secret"))
    }

    @Test
    fun logDescriptionHasNoUserId() {
        val text = FileUtils.describeShareUriForLog(Uri.parse("content://7@com.example.provider/logs/secret-name.json"))

        assertEquals("content://com.example.provider userInfo=true", text)
        assertFalse(text.contains("secret-name"))
        assertFalse(text.contains("7@"))
    }

    @Test
    fun logDescriptionOfNoUriAndOfEmptyUri() {
        assertEquals("none", FileUtils.describeShareUriForLog(null))
        assertEquals("none://none userInfo=false", FileUtils.describeShareUriForLog(Uri.EMPTY))
    }
}
