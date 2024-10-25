/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2024 Daniel Calviño Sánchez <danxuliu@gmail.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.call

import com.nextcloud.talk.models.json.signaling.DataChannelMessage
import com.nextcloud.talk.webrtc.PeerConnectionWrapper
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito

class MessageSenderNoMcuTest {

    private var peerConnectionWrappers: MutableList<PeerConnectionWrapper?>? = null
    private var peerConnectionWrapper1: PeerConnectionWrapper? = null
    private var peerConnectionWrapper2: PeerConnectionWrapper? = null

    private var messageSender: MessageSenderNoMcu? = null

    @Before
    fun setUp() {
        peerConnectionWrappers = ArrayList()

        peerConnectionWrapper1 = Mockito.mock(PeerConnectionWrapper::class.java)
        Mockito.`when`(peerConnectionWrapper1!!.sessionId).thenReturn("theSessionId1")
        Mockito.`when`(peerConnectionWrapper1!!.videoStreamType).thenReturn("video")
        peerConnectionWrappers!!.add(peerConnectionWrapper1)

        peerConnectionWrapper2 = Mockito.mock(PeerConnectionWrapper::class.java)
        Mockito.`when`(peerConnectionWrapper2!!.sessionId).thenReturn("theSessionId2")
        Mockito.`when`(peerConnectionWrapper2!!.videoStreamType).thenReturn("video")
        peerConnectionWrappers!!.add(peerConnectionWrapper2)

        messageSender = MessageSenderNoMcu(peerConnectionWrappers)
    }

    @Test
    fun testSendDataChannelMessageToAll() {
        val message = DataChannelMessage()
        messageSender!!.sendToAll(message)

        Mockito.verify(peerConnectionWrapper1!!).send(message)
        Mockito.verify(peerConnectionWrapper2!!).send(message)
    }
}
