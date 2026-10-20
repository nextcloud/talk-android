/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.chat.viewmodels

import android.os.Looper
import androidx.lifecycle.viewModelScope
import androidx.work.WorkManager
import androidx.work.impl.WorkManagerImpl
import androidx.work.testing.WorkManagerTestInitHelper
import kotlinx.coroutines.cancel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import java.util.concurrent.TimeUnit

/**
 * The name, type and size of a file to send come from the provider of the app that owns it (Nextcloud Files):
 * a blocking binder call into another process. Sending a file must not make it on the main thread.
 */
class ChatViewModelUploadFileTest : ChatViewModelUploadTestBase() {
    @Test
    fun `sending a file does not ask its provider on the main thread`() {
        sendAndAwaitPlaceholder("document.pdf")

        assertTrue("placeholder message is added", placeholderAdded.count == 0L)
        assertEquals("provider calls on the main thread", 0, SlowProvider.mainThreadCalls.get())
        assertTrue("provider is asked for the type and the name", SlowProvider.otherCalls.get() >= 2)
    }

    @Test
    fun `a provider that rejects the uri still leaves a placeholder and does not crash`() {
        sendAndAwaitPlaceholder("rejected.pdf")

        assertTrue("placeholder message is added", placeholderAdded.count == 0L)
        assertEquals("provider calls on the main thread", 0, SlowProvider.mainThreadCalls.get())
    }

    @Test
    fun `the upload worker is enqueued at once, before the provider has answered`() {
        val viewModel = newViewModel()

        viewModel.send("slow-a.pdf", order = 1)

        assertEquals("uploads queued while the provider is still asked", 1, queuedUploads())
        assertEquals("placeholder is not added yet", 1L, placeholderAdded.count)
    }

    @Test
    fun `a view model cleared while the provider answers still has its upload enqueued`() {
        val viewModel = newViewModel()

        viewModel.send("slow-a.pdf", order = 1)
        viewModel.viewModelScope.cancel()

        assertEquals("uploads queued", 1, queuedUploads())
    }

    @Test
    fun `files are enqueued in the order they were sent whatever speed their providers answer with`() {
        val viewModel = newViewModel()

        viewModel.send("slow-a.pdf", order = 1)
        viewModel.send("b.pdf", order = 2)

        val workIds = viewModel.uploadWorkIds()
        val first = workIds.getValue(referenceId(1))
        val second = workIds.getValue(referenceId(2))
        val dependents = (WorkManager.getInstance(RuntimeEnvironment.getApplication()) as WorkManagerImpl)
            .workDatabase
            .dependencyDao()
            .getDependentWorkIds(first.toString())
        assertEquals("the second file waits for the first one", listOf(second.toString()), dependents)
    }

    @Test
    fun `a worker that fails before the placeholder exists does not leave the placeholder pending`() {
        val viewModel = newViewModel()

        viewModel.send("slow-a.pdf", order = 1)
        val workId = viewModel.uploadWorkIds().getValue(referenceId(1))
        WorkManagerTestInitHelper.getTestDriver(RuntimeEnvironment.getApplication())!!.setAllConstraintsMet(workId)

        val deadline = System.currentTimeMillis() + TIMEOUT_MS
        while (placeholderAdded.count > 0 && System.currentTimeMillis() < deadline) {
            shadowOf(Looper.getMainLooper()).idle()
            placeholderAdded.await(POLL_MS, TimeUnit.MILLISECONDS)
        }
        while (placeholderMarkedFailed.count > 0 && System.currentTimeMillis() < deadline) {
            shadowOf(Looper.getMainLooper()).idle()
            placeholderMarkedFailed.await(POLL_MS, TimeUnit.MILLISECONDS)
        }

        assertEquals("the placeholder is marked as failed", 0L, placeholderMarkedFailed.count)
    }
}
