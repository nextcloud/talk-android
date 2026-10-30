/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.utils

import android.app.Application
import android.net.Uri
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.nio.file.Files

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [33])
class FileUtilsShareUriTest {

    private lateinit var cacheDir: File

    @Before
    fun setUp() {
        cacheDir = Files.createTempDirectory("cache").toFile()
    }

    @After
    fun tearDown() {
        cacheDir.deleteRecursively()
    }

    private fun isTrusted(uri: String?) = FileUtils.isTrustedShareUri(uri?.let { Uri.parse(it) }, PACKAGE, cacheDir)

    @Test
    fun `accepts content uris of other apps`() {
        assertTrue(isTrusted("content://media/external/images/media/1"))
    }

    @Test
    fun `accepts own uris inside the shared attachments cache`() {
        assertTrue(isTrusted("content://$PACKAGE/shared_attachments/image.jpg"))
    }

    @Test
    fun `accepts own uris inside the copies of shared files`() {
        assertTrue(isTrusted("content://$PACKAGE/shared_file_copies/share1/0/image.jpg"))
    }

    @Test
    fun `rejects own uris outside the shared attachments cache`() {
        assertFalse(isTrusted("content://$PACKAGE/logs/log.txt"))
        assertFalse(isTrusted("content://$PACKAGE/shared_attachments/../databases/talk.db"))
        assertFalse(isTrusted("content://$PACKAGE/shared_file_copies/../databases/talk.db"))
    }

    @Test
    fun `rejects non content uris`() {
        assertFalse(isTrusted("file:///data/data/$PACKAGE/databases/talk.db"))
        assertFalse(isTrusted("https://example.com/file.txt"))
        assertFalse(isTrusted(null))
    }

    companion object {
        private const val PACKAGE = "com.nextcloud.talk2"
    }
}
