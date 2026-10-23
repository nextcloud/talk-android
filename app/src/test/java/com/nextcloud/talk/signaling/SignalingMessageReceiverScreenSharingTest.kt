/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.signaling

import com.nextcloud.talk.models.json.signaling.NCIceCandidateDto
import com.nextcloud.talk.models.json.signaling.NCMessagePayloadDto
import com.nextcloud.talk.models.json.signaling.NCSignalingMessageDto
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.only
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoInteractions

class SignalingMessageReceiverScreenSharingTest {

    private lateinit var signalingMessageReceiver: SignalingMessageReceiver
    private lateinit var receivingListener: SignalingMessageReceiver.WebRtcMessageListener
    private lateinit var sharingListener: SignalingMessageReceiver.WebRtcMessageListener

    @Before
    fun setUp() {
        signalingMessageReceiver = object : SignalingMessageReceiver() {}
        receivingListener = mock(SignalingMessageReceiver.WebRtcMessageListener::class.java)
        sharingListener = mock(SignalingMessageReceiver.WebRtcMessageListener::class.java)
    }

    private fun addBothListeners() {
        signalingMessageReceiver.addListener(receivingListener, REMOTE_SESSION_ID, "screen")
        signalingMessageReceiver.addListener(
            sharingListener,
            REMOTE_SESSION_ID,
            SignalingMessageReceiver.ROOM_TYPE_SCREEN_SHARING
        )
    }

    private fun createMessage(type: String, broadcaster: String? = null): NCSignalingMessageDto {
        val payload = NCMessagePayloadDto()
        payload.type = type
        if (type == "candidate") {
            val iceCandidate = NCIceCandidateDto()
            iceCandidate.sdpMid = "theSdpMid"
            iceCandidate.sdpMLineIndex = 42
            iceCandidate.candidate = "theCandidate"
            payload.iceCandidate = iceCandidate
        } else {
            payload.sdp = "theSdp"
        }

        val message = NCSignalingMessageDto()
        message.from = REMOTE_SESSION_ID
        message.type = type
        message.roomType = "screen"
        message.broadcaster = broadcaster
        message.payload = payload
        return message
    }

    @Test
    fun testOfferIsDeliveredToReceivingConnection() {
        addBothListeners()

        signalingMessageReceiver.processSignalingMessage(createMessage("offer", REMOTE_SESSION_ID))

        verify(receivingListener, only()).onOffer("theSdp", null)
        verifyNoInteractions(sharingListener)
    }

    @Test
    fun testAnswerIsDeliveredToSharingConnection() {
        addBothListeners()

        signalingMessageReceiver.processSignalingMessage(createMessage("answer"))

        verify(sharingListener, only()).onAnswer("theSdp", null)
        verifyNoInteractions(receivingListener)
    }

    @Test
    fun testAnswerWithoutSharingConnectionIsDeliveredToScreenListener() {
        signalingMessageReceiver.addListener(receivingListener, REMOTE_SESSION_ID, "screen")

        signalingMessageReceiver.processSignalingMessage(createMessage("answer"))

        verify(receivingListener, only()).onAnswer("theSdp", null)
    }

    @Test
    fun testCandidateFromBroadcasterIsDeliveredToReceivingConnection() {
        addBothListeners()

        signalingMessageReceiver.processSignalingMessage(createMessage("candidate", REMOTE_SESSION_ID))

        verify(receivingListener, only()).onCandidate("theSdpMid", 42, "theCandidate")
        verifyNoInteractions(sharingListener)
    }

    @Test
    fun testCandidateWithoutBroadcasterIsDeliveredToSharingConnection() {
        addBothListeners()

        signalingMessageReceiver.processSignalingMessage(createMessage("candidate"))

        verify(sharingListener, only()).onCandidate("theSdpMid", 42, "theCandidate")
        verifyNoInteractions(receivingListener)
    }

    @Test
    fun testCandidateWithLocalBroadcasterIsDeliveredToSharingConnection() {
        addBothListeners()

        signalingMessageReceiver.processSignalingMessage(createMessage("candidate", "theLocalSessionId"))

        verify(sharingListener, only()).onCandidate("theSdpMid", 42, "theCandidate")
        verifyNoInteractions(receivingListener)
    }

    @Test
    fun testCandidateWithoutSharingConnectionIsDeliveredToReceivingConnection() {
        signalingMessageReceiver.addListener(receivingListener, REMOTE_SESSION_ID, "screen")

        signalingMessageReceiver.processSignalingMessage(createMessage("candidate"))

        verify(receivingListener, only()).onCandidate("theSdpMid", 42, "theCandidate")
    }

    @Test
    fun testEndOfCandidatesFromBroadcasterIsDeliveredToReceivingConnection() {
        addBothListeners()

        signalingMessageReceiver.processSignalingMessage(createMessage("endOfCandidates", REMOTE_SESSION_ID))

        verify(receivingListener, only()).onEndOfCandidates()
        verifyNoInteractions(sharingListener)
    }

    @Test
    fun testVideoCandidateIsNotDeliveredToSharingConnection() {
        val videoListener = mock(SignalingMessageReceiver.WebRtcMessageListener::class.java)
        signalingMessageReceiver.addListener(videoListener, REMOTE_SESSION_ID, "video")
        addBothListeners()

        val message = createMessage("candidate")
        message.roomType = "video"
        signalingMessageReceiver.processSignalingMessage(message)

        verify(videoListener, only()).onCandidate("theSdpMid", 42, "theCandidate")
        verifyNoInteractions(sharingListener)
        verifyNoInteractions(receivingListener)
    }

    companion object {
        private const val REMOTE_SESSION_ID = "theRemoteSessionId"
    }
}
