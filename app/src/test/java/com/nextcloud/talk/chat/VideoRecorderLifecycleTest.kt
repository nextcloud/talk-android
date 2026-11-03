/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.chat

import android.app.Application
import androidx.lifecycle.Lifecycle
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

// a lifecycle registry insists on the main thread, which Robolectric gives the test
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [33])
class VideoRecorderLifecycleTest {

    @Test
    fun cameraLifecycleStartsWithTheRecordingAndEndsWithIt() {
        val owner = RecordingLifecycleOwner()
        assertEquals(Lifecycle.State.INITIALIZED, owner.lifecycle.currentState)

        owner.start()
        assertEquals(Lifecycle.State.STARTED, owner.lifecycle.currentState)

        owner.destroy()
        assertEquals(Lifecycle.State.DESTROYED, owner.lifecycle.currentState)
    }

    @Test
    fun destroyedCameraLifecycleCannotBeStartedAgain() {
        val owner = RecordingLifecycleOwner()
        owner.start()
        owner.destroy()

        owner.start()

        assertEquals(Lifecycle.State.DESTROYED, owner.lifecycle.currentState)
    }

    @Test
    fun destroyingTwiceIsHarmless() {
        val owner = RecordingLifecycleOwner()
        owner.destroy()
        owner.destroy()
        assertEquals(Lifecycle.State.DESTROYED, owner.lifecycle.currentState)
    }
}
