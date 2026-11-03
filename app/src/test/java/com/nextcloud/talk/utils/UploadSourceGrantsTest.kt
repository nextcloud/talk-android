/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.utils

import android.app.Application
import android.content.Context
import androidx.core.net.toUri
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.Constraints
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [33])
class UploadSourceGrantsTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    private val picked = "content://media/picker/0/com.android.providers.media.photopicker/media/1"

    @Before
    fun setUp() {
        val configuration = Configuration.Builder().setExecutor(SynchronousExecutor()).build()
        WorkManagerTestInitHelper.initializeTestWorkManager(context, configuration)
    }

    private fun keptUris(): List<String> = context.contentResolver.persistedUriPermissions.map { it.uri.toString() }

    // an upload of [fileUri] that waits for the network, as it does without one
    private fun enqueueUnfinishedUpload(fileUri: String): UUID {
        val upload = OneTimeWorkRequest.Builder(WaitingWorker::class.java)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .addTag(UploadSourceGrants.uploadTagOf(fileUri)!!)
            .build()
        WorkManager.getInstance(context).enqueue(upload).result.get()
        return upload.id
    }

    @Test
    fun `the read access to a picked file is kept for its upload`() {
        UploadSourceGrants.keep(context, picked)

        assertEquals(listOf(picked), keptUris())
    }

    @Test
    fun `only the uploads of content uris are tagged`() {
        assertEquals("upload_source_$picked", UploadSourceGrants.uploadTagOf(picked))
        assertNull(UploadSourceGrants.uploadTagOf("file:///data/voice.mp3"))
        assertNull(UploadSourceGrants.uploadTagOf("/data/voice.mp3"))
    }

    @Test
    fun `the read access is released when its upload ends`() {
        UploadSourceGrants.keep(context, picked)
        val upload = enqueueUnfinishedUpload(picked)

        UploadSourceGrants.release(context, picked, exceptWorkId = upload)

        assertEquals(emptyList<String>(), keptUris())
    }

    @Test
    fun `the read access is kept while another upload of the file is unfinished`() {
        UploadSourceGrants.keep(context, picked)
        val ending = enqueueUnfinishedUpload(picked)
        enqueueUnfinishedUpload(picked)

        UploadSourceGrants.release(context, picked, exceptWorkId = ending)

        assertEquals(listOf(picked), keptUris())
    }

    @Test
    fun `the read access without an unfinished upload is released when the app starts`() {
        val uploaded = "content://media/picker/0/com.android.providers.media.photopicker/media/2"
        UploadSourceGrants.keep(context, picked)
        UploadSourceGrants.keep(context, uploaded)
        enqueueUnfinishedUpload(picked)

        UploadSourceGrants.releaseUnused(context)

        assertEquals(listOf(picked), keptUris())
    }

    @Test
    fun `the access to a file that isn't released stays as it is`() {
        UploadSourceGrants.keep(context, picked)
        val other = picked.toUri().buildUpon().appendPath("other").toString()

        UploadSourceGrants.release(context, other, UUID.randomUUID())

        assertEquals(listOf(picked), keptUris())
    }

    class WaitingWorker(context: Context, parameters: WorkerParameters) : Worker(context, parameters) {
        override fun doWork(): Result = Result.success()
    }
}
