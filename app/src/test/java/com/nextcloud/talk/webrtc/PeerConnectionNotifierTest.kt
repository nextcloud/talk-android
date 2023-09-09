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

import com.nextcloud.talk.webrtc.PeerConnectionWrapper.PeerConnectionObserver
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito
import org.webrtc.MediaStream
import org.webrtc.PeerConnection

class PeerConnectionNotifierTest {
    private var notifier: PeerConnectionNotifier? = null
    private var observer1: PeerConnectionObserver? = null
    private var observer2: PeerConnectionObserver? = null
    private var mockStream: MediaStream? = null
    @Before
    fun setUp() {
        notifier = PeerConnectionNotifier()
        observer1 = Mockito.mock(PeerConnectionObserver::class.java)
        observer2 = Mockito.mock(PeerConnectionObserver::class.java)
        mockStream = Mockito.mock(MediaStream::class.java)
    }

    @Test
    fun testAddObserver() {
        notifier!!.addObserver(observer1)
        notifier!!.notifyStreamAdded(mockStream)
        Mockito.verify(observer1)?.onStreamAdded(mockStream)
        Mockito.verify(observer2, Mockito.never())?.onStreamAdded(mockStream)
    }

    @Test
    fun testRemoveObserver() {
        notifier!!.addObserver(observer1)
        notifier!!.addObserver(observer2)
        notifier!!.removeObserver(observer1)
        notifier!!.notifyStreamAdded(mockStream)
        Mockito.verify(observer1, Mockito.never())?.onStreamAdded(mockStream)
        Mockito.verify(observer2)?.onStreamAdded(mockStream)
    }

    @Test
    fun testNotifyStreamAdded() {
        notifier!!.addObserver(observer1)
        notifier!!.notifyStreamAdded(mockStream)
        Mockito.verify(observer1)?.onStreamAdded(mockStream)
    }

    @Test
    fun testNotifyStreamRemoved() {
        notifier!!.addObserver(observer1)
        notifier!!.notifyStreamRemoved(mockStream)
        Mockito.verify(observer1)?.onStreamRemoved(mockStream)
    }

    @Test
    fun testNotifyIceConnectionStateChanged() {
        notifier!!.addObserver(observer1)
        notifier!!.notifyIceConnectionStateChanged(PeerConnection.IceConnectionState.CONNECTED)
        Mockito.verify(observer1)?.onIceConnectionStateChanged(PeerConnection.IceConnectionState.CONNECTED)
    }
}
