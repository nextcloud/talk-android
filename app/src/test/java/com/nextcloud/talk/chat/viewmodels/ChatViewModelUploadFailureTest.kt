/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.chat.viewmodels

import android.content.Context
import android.os.Looper
import androidx.work.Configuration
import androidx.work.Data
import androidx.work.ListenableWorker
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import androidx.work.workDataOf
import com.nextcloud.talk.jobs.UploadAndShareFilesWorker
import org.junit.Assert.assertEquals
import org.junit.Test
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * A failed upload must not leave its placeholder pending, whether the worker fails or only reports the failure.
 */
class ChatViewModelUploadFailureTest : ChatViewModelUploadTestBase() {
    @Test
    fun `an upload that reports its failure before the placeholder exists does not leave the placeholder pending`() {
        // The worker ends a failed upload as SUCCEEDED, so the uploads queued behind it still run, and reports the
        // failure in its output data.
        useUploadWorkerResult(workDataOf(UploadAndShareFilesWorker.KEY_UPLOAD_FAILED to true))

        val workId = sendSlowFileAndRunItsWorker()
        awaitPlaceholderMarkedFailed()

        val workInfo = WorkManager.getInstance(RuntimeEnvironment.getApplication()).getWorkInfoById(workId).get()
        assertEquals(WorkInfo.State.SUCCEEDED, workInfo?.state)
        assertEquals("the placeholder is marked as failed", 0L, placeholderMarkedFailed.count)
    }

    @Test
    fun `an upload that succeeds does not mark its placeholder as failed`() {
        useUploadWorkerResult(Data.EMPTY)

        val workId = sendSlowFileAndRunItsWorker()
        awaitPlaceholderMarkedFailed()

        val workInfo = WorkManager.getInstance(RuntimeEnvironment.getApplication()).getWorkInfoById(workId).get()
        assertEquals(WorkInfo.State.SUCCEEDED, workInfo?.state)
        assertEquals("the placeholder is left alone", 1L, placeholderMarkedFailed.count)
    }

    // Every upload work ends at once with the given output, before the slow provider let the placeholder in.
    private fun useUploadWorkerResult(output: Data) {
        val factory = object : WorkerFactory() {
            override fun createWorker(
                appContext: Context,
                workerClassName: String,
                workerParameters: WorkerParameters
            ): ListenableWorker =
                object : Worker(appContext, workerParameters) {
                    override fun doWork(): Result = Result.success(output)
                }
        }
        val configuration = Configuration.Builder()
            .setExecutor(SynchronousExecutor())
            .setWorkerFactory(factory)
            .build()
        WorkManagerTestInitHelper.initializeTestWorkManager(RuntimeEnvironment.getApplication(), configuration)
    }

    private fun sendSlowFileAndRunItsWorker(): UUID {
        val viewModel = newViewModel()
        viewModel.send("slow-a.pdf", order = 1)
        val workId = viewModel.uploadWorkIds().getValue(referenceId(1))
        WorkManagerTestInitHelper.getTestDriver(RuntimeEnvironment.getApplication())!!.setAllConstraintsMet(workId)
        return workId
    }

    // Waits for the placeholder, then gives the observer the time to mark it.
    private fun awaitPlaceholderMarkedFailed() {
        val deadline = System.currentTimeMillis() + TIMEOUT_MS
        while (placeholderAdded.count > 0 && System.currentTimeMillis() < deadline) {
            shadowOf(Looper.getMainLooper()).idle()
            placeholderAdded.await(POLL_MS, TimeUnit.MILLISECONDS)
        }
        val markDeadline = System.currentTimeMillis() + MARK_WAIT_MS
        while (placeholderMarkedFailed.count > 0 && System.currentTimeMillis() < markDeadline) {
            shadowOf(Looper.getMainLooper()).idle()
            placeholderMarkedFailed.await(POLL_MS, TimeUnit.MILLISECONDS)
        }
    }
}
