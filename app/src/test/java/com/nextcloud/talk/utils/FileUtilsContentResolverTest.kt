/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.utils

import android.app.Application
import android.content.ContentResolver
import android.content.Context
import android.database.MatrixCursor
import android.net.Uri
import android.provider.OpenableColumns
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.eq
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream

// Regression coverage for a crash: the system Photo Picker's ephemeral content:// uris can have
// their read grant revoked at any point after being handed to us (e.g. while a batch upload is
// still chained behind an earlier, slower one), turning a plain metadata query into an uncaught
// SecurityException that took the whole app down.
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [33])
class FileUtilsContentResolverTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val contentUri = Uri.parse("content://media/picker/0/media/42")

    @Test
    fun `getFileName falls back to the uri path when the content provider query throws`() {
        val uri = Uri.parse("content://media/picker/0/com.android.providers.media.photopicker/media/123")
        val context = mockContextWhoseResolverThrows(uri)

        val name = FileUtils.getFileName(uri, context)

        assertEquals("123", name)
    }

    @Test
    fun `resolveMimeType falls back to the extension guess when the content provider throws`() {
        val uri = Uri.parse("content://media/picker/0/com.android.providers.media.photopicker/media/photo.jpg")
        val context = mock(Context::class.java)
        val resolver = mock(ContentResolver::class.java)
        `when`(context.contentResolver).thenReturn(resolver)
        `when`(resolver.getType(uri)).thenThrow(SecurityException("permission revoked"))

        val mimeType = FileUtils.resolveMimeType(context, uri)

        assertEquals("image/jpeg", mimeType)
    }

    @Test
    fun `copyFileToCache keeps a complete copy`() {
        val context = contextWithStream(ByteArray(COPY_SIZE))

        val copy = FileUtils.copyFileToCache(context, contentUri, "photo.jpg", tempFolder.root)

        assertNotNull(copy)
        assertEquals(COPY_SIZE.toLong(), copy!!.length())
    }

    @Test
    fun `copyFileToCache returns null and leaves no file when the source is empty`() {
        val context = contextWithStream(ByteArray(0))

        assertNull(FileUtils.copyFileToCache(context, contentUri, "photo.jpg", tempFolder.root))
        assertFalse(java.io.File(tempFolder.root, "photo.jpg").exists())
    }

    @Test
    fun `copyFileToCache returns null when the read grant was revoked`() {
        val context = mock(Context::class.java)
        val resolver = mock(ContentResolver::class.java)
        `when`(context.contentResolver).thenReturn(resolver)
        `when`(resolver.openInputStream(contentUri)).thenThrow(SecurityException("revoked"))

        assertNull(FileUtils.copyFileToCache(context, contentUri, "photo.jpg", tempFolder.root))
        assertFalse(java.io.File(tempFolder.root, "photo.jpg").exists())
    }

    @Test
    fun `copyFileToCache keeps a complete copy whose size differs from the one the provider reports`() {
        val context = contextWithStream(ByteArray(COPY_SIZE), reportedSize = COPY_SIZE * 2L)

        val copy = FileUtils.copyFileToCache(context, contentUri, "photo.jpg", tempFolder.root)

        assertEquals(COPY_SIZE.toLong(), copy!!.length())
    }

    @Test
    fun `a copy is complete when it is not empty`() {
        assertTrue(FileUtils.isCompleteCopy(10))
        assertFalse(FileUtils.isCompleteCopy(0))
    }

    private fun contextWithStream(bytes: ByteArray, reportedSize: Long? = null): Context {
        val context = mock(Context::class.java)
        val resolver = mock(ContentResolver::class.java)
        `when`(context.contentResolver).thenReturn(resolver)
        `when`(resolver.openInputStream(contentUri)).thenReturn(ByteArrayInputStream(bytes))
        if (reportedSize != null) {
            val cursor = MatrixCursor(arrayOf(OpenableColumns.SIZE)).apply { addRow(arrayOf<Any>(reportedSize)) }
            `when`(resolver.query(eq(contentUri), any(), anyOrNull(), anyOrNull(), anyOrNull())).thenReturn(cursor)
        }
        return context
    }

    private fun mockContextWhoseResolverThrows(uri: Uri): Context {
        val context = mock(Context::class.java)
        val resolver = mock(ContentResolver::class.java)
        `when`(context.contentResolver).thenReturn(resolver)
        `when`(resolver.query(uri, null, null, null, null))
            .thenThrow(SecurityException("permission revoked"))
        return context
    }

    companion object {
        private const val COPY_SIZE = 64
    }
}
