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
import android.net.Uri
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

// Regression coverage for a crash: the system Photo Picker's ephemeral content:// uris can have
// their read grant revoked at any point after being handed to us (e.g. while a batch upload is
// still chained behind an earlier, slower one), turning a plain metadata query into an uncaught
// SecurityException that took the whole app down.
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [33])
class FileUtilsContentResolverTest {

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

    private fun mockContextWhoseResolverThrows(uri: Uri): Context {
        val context = mock(Context::class.java)
        val resolver = mock(ContentResolver::class.java)
        `when`(context.contentResolver).thenReturn(resolver)
        `when`(resolver.query(uri, null, null, null, null))
            .thenThrow(SecurityException("permission revoked"))
        return context
    }
}
