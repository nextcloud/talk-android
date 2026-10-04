/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.chat

import androidx.lifecycle.Lifecycle
import org.junit.Assert.assertEquals
import org.junit.Test

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
