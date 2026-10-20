/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.upload.normal

import android.app.Application
import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import androidx.test.core.app.ApplicationProvider
import com.nextcloud.talk.api.NcApi
import com.nextcloud.talk.api.NcApiCoroutines
import com.nextcloud.talk.data.user.model.User
import com.nextcloud.talk.models.json.generic.GenericOverall
import io.reactivex.Observable
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import retrofit2.Response
import java.io.File
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread

/** A plain upload copies the file to the cache once it is sent; that must not happen on the main thread. */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [33])
class FileUploaderTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val ncApi: NcApi = mock()
    private val ncApiCoroutines: NcApiCoroutines = mock()

    private fun uploader(file: java.io.File) =
        FileUploader(
            OkHttpClient.Builder().build(),
            ApplicationProvider.getApplicationContext(),
            User(id = 1, userId = "alice", username = "alice", token = "token", baseUrl = "https://cloud.example"),
            "room",
            ncApi,
            file,
            ncApiCoroutines
        )

    private fun smallFile() = tempFolder.newFile("photo.jpg").apply { writeBytes(ByteArray(SIZE)) }

    /**
     * The worker blocks its own thread on the upload, so the file must not be copied on the main thread
     * while it waits: the source is the provider of another app, a binder call into its process.
     */
    @Suppress("TooGenericExceptionCaught")
    @Test
    fun `a successful plain upload does not ask the provider on the main thread`() {
        SlowProvider.reset()
        Robolectric.buildContentProvider(SlowProvider::class.java).create(AUTHORITY)
        val file = smallFile()
        SlowProvider.source = file
        whenever(ncApi.uploadFile(any(), any(), any()))
            .thenReturn(Observable.just(Response.success(GenericOverall())))

        val result = AtomicReference<Boolean?>()
        val failure = AtomicReference<Throwable?>()
        val worker = thread {
            try {
                result.set(
                    uploader(file)
                        .upload(
                            Uri.parse("content://" + AUTHORITY + "/photo.jpg"),
                            "photo-copy.jpg",
                            "/Talk/photo.jpg",
                            null
                        )
                        .blockingFirst()
                )
            } catch (throwable: Throwable) {
                failure.set(throwable)
            }
        }
        val deadline = System.currentTimeMillis() + TIMEOUT_MS
        while (worker.isAlive && System.currentTimeMillis() < deadline) {
            shadowOf(Looper.getMainLooper()).idle()
            worker.join(minOf(POLL_MS, maxOf(1L, deadline - System.currentTimeMillis())))
        }

        assertTrue("the upload worker timed out", !worker.isAlive)
        failure.get()?.let { throw it }
        assertEquals("the upload succeeded", true, result.get())
        assertEquals("provider calls on the main thread", 0, SlowProvider.mainThreadCalls.get())
        assertTrue("the file was read from the provider", SlowProvider.otherCalls.get() >= 2)
    }

    class SlowProvider : ContentProvider() {
        override fun onCreate() = true

        override fun getType(uri: Uri): String {
            count()
            return "image/jpeg"
        }

        override fun query(
            uri: Uri,
            projection: Array<out String>?,
            selection: String?,
            selectionArgs: Array<out String>?,
            sortOrder: String?
        ): Cursor {
            count()
            return MatrixCursor(arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE)).apply {
                addRow(arrayOf<Any?>(uri.lastPathSegment, SIZE.toLong()))
            }
        }

        override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
            count()
            return ParcelFileDescriptor.open(source, ParcelFileDescriptor.MODE_READ_ONLY)
        }

        override fun insert(uri: Uri, values: ContentValues?): Uri? = null

        override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?) = 0

        override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?) = 0

        private fun count() {
            val onMain = Looper.myLooper() == Looper.getMainLooper()
            (if (onMain) mainThreadCalls else otherCalls).incrementAndGet()
            Thread.sleep(PROVIDER_DELAY_MS)
        }

        companion object {
            val mainThreadCalls = AtomicInteger()
            val otherCalls = AtomicInteger()
            var source: File? = null

            fun reset() {
                mainThreadCalls.set(0)
                otherCalls.set(0)
            }
        }
    }

    companion object {
        private const val AUTHORITY = "com.nextcloud.client.documentsfileprovider"
        private const val PROVIDER_DELAY_MS = 50L
        private const val TIMEOUT_MS = 20_000L
        private const val POLL_MS = 50L
        private const val SIZE = 16
    }
}
