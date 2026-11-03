/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.utils

import android.app.Application
import android.content.Context
import androidx.work.Configuration
import androidx.work.Constraints
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import com.nextcloud.talk.BuildConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [33])
class SharedFileCopiesTest {

    private val context = RuntimeEnvironment.getApplication()
    private val provider = "content://${BuildConfig.APPLICATION_ID}"
    private val sharesDirectory = File(context.filesDir, "shared_file_copies")

    @Before
    fun setUp() {
        WorkManagerTestInitHelper.initializeTestWorkManager(
            context,
            Configuration.Builder().setExecutor(SynchronousExecutor()).build()
        )
        sharesDirectory.deleteRecursively()
    }

    private fun share(name: String, daysOld: Long = 0): File =
        File(sharesDirectory, "$name/0").let { item ->
            item.mkdirs()
            File(item, "photo.jpg").writeText("photo")
            File(sharesDirectory, name).apply {
                setLastModified(System.currentTimeMillis() - TimeUnit.DAYS.toMillis(daysOld))
            }
        }

    private fun copyUri(share: String) = "$provider/shared_file_copies/$share/0/photo.jpg"

    // an upload that waits for the network, as it does without one
    private fun enqueueUnfinishedUpload(share: String) {
        val upload = OneTimeWorkRequest.Builder(WaitingWorker::class.java)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .addTag(SharedFileCopies.uploadTagOf(copyUri(share))!!)
            .build()
        WorkManager.getInstance(context).enqueue(upload).result.get()
    }

    @Test
    fun copyOfShareIsTaggedWithItsShareDirectory() {
        assertEquals(
            "shared_file_copy_share1",
            SharedFileCopies.uploadTagOf("$provider/shared_file_copies/share1/0/photo.jpg")
        )
    }

    @Test
    fun copyOfShareWithTheUserInItsUriIsTaggedWithItsShareDirectory() {
        val copy = "content://0@${BuildConfig.APPLICATION_ID}/shared_file_copies/share1/0/photo.jpg"

        assertEquals("shared_file_copy_share1", SharedFileCopies.uploadTagOf(copy))
    }

    @Test
    fun otherFilesAreNotTagged() {
        assertNull(SharedFileCopies.uploadTagOf("$provider/shared_attachments/photo.jpg"))
        assertNull(SharedFileCopies.uploadTagOf("$provider/shared_attachments/shares/share1/0/photo.jpg"))
        assertNull(SharedFileCopies.uploadTagOf("$provider/shared_file_copies/share1"))
        assertNull(SharedFileCopies.uploadTagOf("$provider/photos/share1/0/photo.jpg"))
        assertNull(SharedFileCopies.uploadTagOf("content://other.app/shared_file_copies/share1/0/photo.jpg"))
        // a copy of the app of another user, e.g. in another profile
        assertNull(SharedFileCopies.uploadTagOf("content://10@${BuildConfig.APPLICATION_ID}/shared_file_copies/s/0/a"))
        assertNull(SharedFileCopies.uploadTagOf("file:///shared_file_copies/share1/0/photo.jpg"))
    }

    @Test
    fun leftShareIsRemovedWithoutTheOthers() {
        val left = share("left")
        val other = share("other")

        SharedFileCopies.removeSharesOf(context, listOf(copyUri("left"), "content://other.app/photos/photo.jpg"))

        assertFalse(left.exists())
        assertTrue(other.exists())
    }

    @Test
    fun leftShareWithUnfinishedUploadIsKept() {
        val uploading = share("uploading")
        enqueueUnfinishedUpload("uploading")

        SharedFileCopies.removeSharesOf(context, listOf(copyUri("uploading")))

        assertTrue(uploading.exists())
    }

    @Test
    fun uriOutsideOfTheSharesRemovesNothing() {
        val other = share("other")

        SharedFileCopies.removeSharesOf(context, listOf("$provider/shared_file_copies/../0/photo.jpg"))

        assertTrue(other.exists())
        assertTrue(context.filesDir.exists())
    }

    @Test
    fun doneSharesAreRemovedWithoutNewShare() {
        val done = share("done", daysOld = 2)
        val recent = share("recent")
        val uploading = share("uploading", daysOld = 2)
        enqueueUnfinishedUpload("uploading")

        SharedFileCopies.removeDoneShares(context)

        assertFalse(done.exists())
        assertTrue(recent.exists())
        assertTrue(uploading.exists())
    }

    class WaitingWorker(context: Context, parameters: WorkerParameters) : Worker(context, parameters) {
        override fun doWork(): Result = Result.success()
    }
}
