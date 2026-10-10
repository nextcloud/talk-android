/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.upload.normal

import android.app.Application
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.nextcloud.talk.api.NcApi
import com.nextcloud.talk.api.NcApiCoroutines
import com.nextcloud.talk.data.user.model.User
import com.nextcloud.talk.models.json.generic.GenericOverall
import com.nextcloud.talk.upload.UploadRetryPolicy
import io.reactivex.Observable
import io.reactivex.android.plugins.RxAndroidPlugins
import io.reactivex.schedulers.Schedulers
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import retrofit2.HttpException
import retrofit2.Response

/** An error answer of the server is thrown, so [UploadRetryPolicy] can tell an unavailable server from a refusal. */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [33])
class FileUploaderTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Before
    fun setUp() {
        // The upload observes on the main looper, which a test that blocks on the result never lets run.
        RxAndroidPlugins.setMainThreadSchedulerHandler { Schedulers.trampoline() }
    }

    @After
    fun tearDown() {
        RxAndroidPlugins.reset()
    }

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

    @Test
    fun `an unavailable server fails the conversation subfolder upload with a retriable error`() {
        val file = smallFile()
        runBlocking {
            whenever(ncApiCoroutines.uploadFile(any(), any(), any()))
                .thenReturn(Response.error<GenericOverall>(UNAVAILABLE, "".toResponseBody()))
        }

        try {
            runBlocking { uploader(file).uploadToConversationSubfolder(Uri.fromFile(file), "/draft/photo.jpg") }
            fail("expected an HttpException")
        } catch (e: HttpException) {
            assertEquals(UploadRetryPolicy.FailureKind.NETWORK, UploadRetryPolicy.classify(e))
        }
    }

    @Test
    fun `an unavailable server fails the plain upload with a retriable error`() {
        val file = smallFile()
        whenever(ncApi.uploadFile(any(), any(), any()))
            .thenReturn(Observable.just(Response.error<GenericOverall>(UNAVAILABLE, "".toResponseBody())))

        try {
            uploader(file).upload(Uri.fromFile(file), "photo.jpg", "/Talk/photo.jpg", null).blockingFirst()
            fail("expected an HttpException")
        } catch (e: HttpException) {
            assertEquals(UNAVAILABLE, e.code())
        }
    }

    @Test
    fun `a refusal is an error that counts against the limit`() {
        val file = smallFile()
        whenever(ncApi.uploadFile(any(), any(), any()))
            .thenReturn(Observable.just(Response.error<GenericOverall>(FORBIDDEN, "".toResponseBody())))

        try {
            uploader(file).upload(Uri.fromFile(file), "photo.jpg", "/Talk/photo.jpg", null).blockingFirst()
            fail("expected an HttpException")
        } catch (e: HttpException) {
            assertTrue(UploadRetryPolicy.classify(e) == UploadRetryPolicy.FailureKind.SERVER)
        }
    }

    companion object {
        private const val SIZE = 16
        private const val UNAVAILABLE = 503
        private const val FORBIDDEN = 403
    }
}
