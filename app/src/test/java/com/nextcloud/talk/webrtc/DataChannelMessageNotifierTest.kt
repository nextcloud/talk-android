/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2023 Samanwith KSN <samanwith21@gmail.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.webrtc

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify

class DataChannelMessageNotifierTest {

    private lateinit var notifier: DataChannelMessageNotifier
    private lateinit var listener: PeerConnectionWrapper.DataChannelMessageListener

    @Before
    fun setUp() {
        notifier = DataChannelMessageNotifier()
        listener = mock(PeerConnectionWrapper.DataChannelMessageListener::class.java)
    }

    @Test
    fun testAddListener() {
        notifier.addListener(listener)
        assertTrue(notifier.dataChannelMessageListeners.contains(listener))
    }

    @Test
    fun testRemoveListener() {
        notifier.addListener(listener)
        notifier.removeListener(listener)
        assertFalse(notifier.dataChannelMessageListeners.contains(listener))
    }

    @Test
    fun testNotifyAudioOn() {
        notifier.addListener(listener)
        notifier.notifyAudioOn()
        verify(listener).onAudioOn()
    }

    @Test
    fun testNotifyAudioOff() {
        notifier.addListener(listener)
        notifier.notifyAudioOff()
        verify(listener).onAudioOff()
    }

    @Test
    fun testNotifyVideoOn() {
        notifier.addListener(listener)
        notifier.notifyVideoOn()
        verify(listener).onVideoOn()
    }

    @Test
    fun testNotifyVideoOff() {
        notifier.addListener(listener)
        notifier.notifyVideoOff()
        verify(listener).onVideoOff()
    }

    @Test
    fun testNotifyNickChanged() {
        notifier.addListener(listener)
        val newNick = "NewNick"
        notifier.notifyNickChanged(newNick)
        verify(listener).onNickChanged(newNick)
    }
}
