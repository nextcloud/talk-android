/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.utils

import android.graphics.Bitmap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class PickImageTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private fun pngBody(): ResponseBody {
        val out = ByteArrayOutputStream()
        Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888).compress(Bitmap.CompressFormat.PNG, 100, out)
        return out.toByteArray().toResponseBody("image/png".toMediaType())
    }

    @Test
    fun nullBodyYieldsNull() {
        assertNull(decodeAvatarToFile(null) { tmp.newFile() })
    }

    @Test
    fun undecodableBodyYieldsNull() {
        val body = "not an image".toResponseBody("image/png".toMediaType())
        assertNull(decodeAvatarToFile(body, decode = { null }) { tmp.newFile() })
    }

    @Test
    fun writeFailureYieldsNull() {
        assertNull(decodeAvatarToFile(pngBody()) { throw IOException("disk full") })
    }

    @Test
    fun unwritableTargetYieldsNull() {
        val dir: File = tmp.newFolder()
        assertNull(decodeAvatarToFile(pngBody()) { dir })
    }

    @Test
    fun validBodyIsSavedAsPng() {
        val target = tmp.newFile()
        val result = decodeAvatarToFile(pngBody()) { target }
        assertNotNull(result)
        assertEquals(target, result)
        assertTrue(target.length() > 0)
    }

    @Test
    fun bodyIsClosedWhenTheScopeIsAlreadyCancelled() {
        val body = mock(ResponseBody::class.java)
        var started = false
        val scope = CoroutineScope(Job().apply { cancel() } + Dispatchers.Unconfined)

        scope.launchClosing(body) { started = true }

        assertTrue(!started)
        verify(body).close()
    }
}
