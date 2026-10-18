/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.chat

import android.app.Application
import androidx.camera.view.PreviewView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class VideoRecordingCopilotFixesTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val context: Application = ApplicationProvider.getApplicationContext()

    private fun recorder() = VideoMessageRecorder(context)

    private fun file(): File = folder.newFile("video.mp4")

    private class TestOwner : LifecycleOwner {
        private val registry = LifecycleRegistry.createUnsafe(this).apply { currentState = Lifecycle.State.STARTED }
        override val lifecycle: Lifecycle get() = registry
    }

    @Test
    fun heldResultIsDeliveredByTheNextAttachOnly() {
        val recorder = recorder()
        val file = file()
        recorder.holdResult(VideoMessageRecorder.Outcome.SEND, file)
        assertTrue(recorder.hasPendingResult)

        var delivered: Pair<VideoMessageRecorder.Outcome, File?>? = null
        recorder.attach(TestOwner(), PreviewView(context), { outcome, f -> delivered = outcome to f })

        assertEquals(VideoMessageRecorder.Outcome.SEND to file, delivered)
        assertFalse(recorder.hasPendingResult)
        assertTrue(file.exists())
    }

    @Test
    fun resultHeldAfterReleaseIsDropped() {
        val recorder = recorder()
        val file = file()
        recorder.release()

        recorder.holdResult(VideoMessageRecorder.Outcome.SEND, file)

        assertFalse(recorder.hasPendingResult)
        assertFalse(file.exists())
    }

    @Test
    fun acceptedSendIsHandedOverWhenTheRecorderWasReleasedBeforeFinalization() {
        val recorder = recorder()
        val file = file()
        var sent: File? = null
        recorder.outputFile = file
        recorder.sendAfterRelease = { sent = it }
        recorder.release()

        recorder.finish(VideoMessageRecorder.Outcome.SEND, file)

        assertEquals(file, sent)
        assertTrue(file.exists())
    }

    @Test
    fun releasedRecorderDeletesTheFileWhenNoSendWasAccepted() {
        val recorder = recorder()
        val file = file()
        recorder.outputFile = file
        recorder.release()

        recorder.finish(VideoMessageRecorder.Outcome.SEND, file)

        assertFalse(file.exists())
    }

    @Test
    fun releasedRecorderDoesNotSendACancelledRecording() {
        val recorder = recorder()
        val file = file()
        var sent: File? = null
        recorder.sendAfterRelease = { sent = it }
        recorder.release()

        recorder.finish(VideoMessageRecorder.Outcome.CANCELLED, null)

        assertNull(sent)
    }
}
