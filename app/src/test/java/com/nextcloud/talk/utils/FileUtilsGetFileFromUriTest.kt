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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [33])
class FileUtilsGetFileFromUriTest {

    private val context = RuntimeEnvironment.getApplication()
    private val cacheDir: File = context.cacheDir

    @Before
    fun setUp() {
        cacheDir.deleteRecursively()
        cacheDir.mkdirs()
    }

    @After
    fun tearDown() {
        cacheDir.deleteRecursively()
    }

    private fun fileInCache(path: String) =
        File(cacheDir, path).apply {
            parentFile!!.mkdirs()
            writeText("content")
        }

    private fun ownUri(path: String) = Uri.parse("content://${context.packageName}/$path")

    @Test
    fun `a copy of a shared file is used without copying it again`() {
        val copy = fileInCache("shared_file_copies/share1/0/video.mp4")

        val file = FileUtils.getFileFromUri(context, ownUri("shared_file_copies/share1/0/video.mp4"))

        assertEquals(copy.canonicalFile, file?.canonicalFile)
        assertFalse(File(cacheDir, "video.mp4").exists())
    }

    @Test
    fun `a shared attachment is used without copying it again`() {
        val attachment = fileInCache("shared_attachments/image.jpg")

        val file = FileUtils.getFileFromUri(context, ownUri("shared_attachments/image.jpg"))

        assertEquals(attachment.canonicalFile, file?.canonicalFile)
    }

    // read through the content resolver, as from another provider, with other content than the file in the cache
    private fun copyOf(uri: Uri): File? {
        shadowOf(context.contentResolver).registerInputStream(uri, ByteArrayInputStream(PROVIDED.toByteArray()))
        return FileUtils.getFileFromUri(context, uri)
    }

    @Test
    fun `other files of the own provider are copied`() {
        fileInCache("logs/log.txt")
        fileInCache("shared_file_copies/share1/0/image.jpg")

        assertEquals(PROVIDED, copyOf(ownUri("logs/log.txt"))?.readText())
        assertEquals(PROVIDED, copyOf(ownUri("shared_file_copies/../logs/log.txt"))?.readText())
    }

    @Test
    fun `the same path of another provider is copied`() {
        fileInCache("shared_file_copies/share1/0/image.jpg")

        val uri = Uri.parse("content://other.app/shared_file_copies/share1/0/image.jpg")

        assertEquals(PROVIDED, copyOf(uri)?.readText())
    }

    companion object {
        private const val PROVIDED = "provided"
    }
}
