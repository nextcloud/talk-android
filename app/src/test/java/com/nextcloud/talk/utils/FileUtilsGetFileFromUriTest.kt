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
    private val sharesDirectory = File(context.filesDir, "shared_file_copies")

    @Before
    fun setUp() {
        cacheDir.deleteRecursively()
        cacheDir.mkdirs()
        sharesDirectory.deleteRecursively()
    }

    @After
    fun tearDown() {
        cacheDir.deleteRecursively()
        sharesDirectory.deleteRecursively()
    }

    private fun fileIn(directory: File, path: String) =
        File(directory, path).apply {
            parentFile!!.mkdirs()
            writeText("content")
        }

    private fun fileInCache(path: String) = fileIn(cacheDir, path)

    private fun copyOfShare(path: String) = fileIn(sharesDirectory, path)

    private fun ownUri(path: String) = Uri.parse("content://${context.packageName}/$path")

    @Test
    fun `a copy of a shared file is used without copying it again`() {
        val copy = copyOfShare("share1/0/video.mp4")

        val file = FileUtils.getFileFromUri(context, ownUri("shared_file_copies/share1/0/video.mp4"))

        assertEquals(copy.canonicalFile, file?.canonicalFile)
        assertFalse(File(cacheDir, "video.mp4").exists())
    }

    @Test
    fun `a copy of a shared file with the user in its uri is used without copying it again`() {
        val copy = copyOfShare("share1/0/video.mp4")

        val uri = Uri.parse("content://0@${context.packageName}/shared_file_copies/share1/0/video.mp4")

        assertEquals(copy.canonicalFile, FileUtils.getFileFromUri(context, uri)?.canonicalFile)
    }

    @Test
    fun `a shared attachment is used without copying it again`() {
        val attachment = fileInCache("shared_attachments/image.jpg")

        val file = FileUtils.getFileFromUri(context, ownUri("shared_attachments/image.jpg"))

        assertEquals(attachment.canonicalFile, file?.canonicalFile)
    }

    @Test
    fun `a file in the cache named like a copy of a shared file is copied`() {
        fileInCache("shared_file_copies/share1/0/image.jpg")

        assertEquals(PROVIDED, copyOf(ownUri("shared_file_copies/share1/0/image.jpg"))?.readText())
    }

    // read through the content resolver, as from another provider, with other content than the file in the cache
    private fun copyOf(uri: Uri): File? {
        shadowOf(context.contentResolver).registerInputStream(uri, ByteArrayInputStream(PROVIDED.toByteArray()))
        return FileUtils.getFileFromUri(context, uri)
    }

    @Test
    fun `other files of the own provider are copied`() {
        fileInCache("logs/log.txt")
        copyOfShare("share1/0/image.jpg")

        assertEquals(PROVIDED, copyOf(ownUri("logs/log.txt"))?.readText())
        assertEquals(PROVIDED, copyOf(ownUri("shared_file_copies/../logs/log.txt"))?.readText())
    }

    @Test
    fun `the same path of the provider of the app of another user is copied`() {
        copyOfShare("share1/0/image.jpg")

        val uri = Uri.parse("content://10@${context.packageName}/shared_file_copies/share1/0/image.jpg")

        assertEquals(PROVIDED, copyOf(uri)?.readText())
    }

    @Test
    fun `the same path of another provider is copied`() {
        copyOfShare("share1/0/image.jpg")

        val uri = Uri.parse("content://other.app/shared_file_copies/share1/0/image.jpg")

        assertEquals(PROVIDED, copyOf(uri)?.readText())
    }

    companion object {
        private const val PROVIDED = "provided"
    }
}
