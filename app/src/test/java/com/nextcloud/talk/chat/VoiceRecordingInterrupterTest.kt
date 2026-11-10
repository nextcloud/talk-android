/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.chat

import android.app.Activity
import com.nextcloud.talk.activities.CallActivity
import com.nextcloud.talk.callnotification.CallNotificationActivity
import com.nextcloud.talk.mediaviewer.activities.MediaViewerActivity
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * A voice recording goes on under Talk screens that do not use the microphone, and ends when the app goes to the
 * background or a Talk screen starts that uses the microphone itself. Android does not silence a second capture from
 * the same app, so this holds on every Android version.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class VoiceRecordingInterrupterTest {

    private val chatActivity: ChatActivity = mock()
    private var interruptions = 0
    private val interrupter = VoiceRecordingInterrupter(chatActivity) { interruptions++ }

    @Test
    fun `the media viewer over the chat does not interrupt`() {
        interrupter.onActivityStarted(mock<MediaViewerActivity>())

        assertEquals(0, interruptions)
    }

    @Test
    fun `the chat itself starting again does not interrupt`() {
        interrupter.onActivityStarted(chatActivity)

        assertEquals(0, interruptions)
    }

    @Test
    fun `a call interrupts`() {
        interrupter.onActivityStarted(mock<CallActivity>())

        assertEquals(1, interruptions)
    }

    @Test
    @Config(sdk = [28])
    fun `a call interrupts before Android 10`() {
        interrupter.onActivityStarted(mock<CallActivity>())

        assertEquals(1, interruptions)
    }

    @Test
    fun `an incoming call interrupts`() {
        interrupter.onActivityStarted(mock<CallNotificationActivity>())

        assertEquals(1, interruptions)
    }

    @Test
    fun `another chat interrupts`() {
        interrupter.onActivityStarted(mock<ChatActivity>())

        assertEquals(1, interruptions)
    }

    @Test
    fun `the app going to the background interrupts`() {
        interrupter.onStop(mock())

        assertEquals(1, interruptions)
    }

    @Test
    fun `another kind of screen does not interrupt`() {
        interrupter.onActivityStarted(mock<Activity>())

        assertEquals(0, interruptions)
    }
}
