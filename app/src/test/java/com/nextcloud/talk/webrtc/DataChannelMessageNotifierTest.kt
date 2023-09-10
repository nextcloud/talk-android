/*
 * Nextcloud Talk application
 *
 * @author Mario Danic
 * Copyright (C) 2017-2019 Mario Danic <mario@lovelyhq.com>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
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
