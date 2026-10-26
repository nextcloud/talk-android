/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.webrtc

import com.nextcloud.talk.call.e2ee.FrameKeyRing
import com.nextcloud.talk.models.json.signaling.NCSignalingMessageDto
import com.nextcloud.talk.signaling.SignalingMessageReceiver
import com.nextcloud.talk.signaling.SignalingMessageSender
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.anyString
import org.mockito.ArgumentMatchers.eq
import org.mockito.Mockito
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.webrtc.DataChannel
import org.webrtc.FrameDecryptor
import org.webrtc.FrameEncryptor
import org.webrtc.MediaConstraints
import org.webrtc.MediaStream
import org.webrtc.PeerConnection
import org.webrtc.PeerConnectionFactory
import org.webrtc.RtpReceiver
import org.webrtc.RtpSender
import org.webrtc.SdpObserver
import org.webrtc.SessionDescription
import org.webrtc.VideoTrack

class PeerConnectionWrapperScreenSharingTest {

    private class FakeKeyRing : FrameKeyRing {
        val encryptors = ArrayList<FrameEncryptor>()

        override fun setKey(key: ByteArray, keyIndex: Int): Boolean = true

        override fun attachEncryptor(sender: RtpSender): Boolean {
            sender.setFrameEncryptor(FrameEncryptor { 0 }.also { encryptors.add(it) })
            return true
        }

        override fun attachDecryptor(receiver: RtpReceiver): Boolean {
            receiver.setFrameDecryptor(FrameDecryptor { 0 })
            return true
        }

        override fun dispose() {
            // unused atm
        }
    }

    private lateinit var mockedPeerConnection: PeerConnection
    private lateinit var mockedPeerConnectionFactory: PeerConnectionFactory
    private lateinit var mockedSignalingMessageReceiver: SignalingMessageReceiver
    private lateinit var mockedSignalingMessageSender: SignalingMessageSender

    @Before
    fun setUp() {
        mockedPeerConnection = Mockito.mock(PeerConnection::class.java)
        mockedPeerConnectionFactory = Mockito.mock(PeerConnectionFactory::class.java)
        mockedSignalingMessageReceiver = Mockito.mock(SignalingMessageReceiver::class.java)
        mockedSignalingMessageSender = Mockito.mock(SignalingMessageSender::class.java)
        Mockito.`when`(
            mockedPeerConnectionFactory.createPeerConnection(
                any(PeerConnection.RTCConfiguration::class.java),
                any(PeerConnection.Observer::class.java)
            )
        ).thenReturn(mockedPeerConnection)
        val mockedStatusDataChannel = Mockito.mock(DataChannel::class.java)
        Mockito.`when`(mockedStatusDataChannel.label()).thenReturn("status")
        Mockito.`when`(mockedPeerConnection.createDataChannel(eq("status"), any())).thenReturn(mockedStatusDataChannel)
    }

    /**
     * The local session is lower than the remote one, so the connection would not send the offer if it did not
     * share the screen.
     */
    private fun createWrapper(
        isScreenSharer: Boolean,
        hasMCU: Boolean = false,
        localStream: MediaStream? = createScreenStream(),
        videoStreamType: String = "screen",
        encryptionRequired: Boolean = false,
        senderKeyRing: FrameKeyRing? = null
    ): PeerConnectionWrapper =
        PeerConnectionWrapper(
            mockedPeerConnectionFactory,
            ArrayList(),
            MediaConstraints(),
            if (hasMCU) LOCAL_SESSION_ID else REMOTE_SESSION_ID,
            LOCAL_SESSION_ID,
            localStream,
            hasMCU,
            hasMCU,
            videoStreamType,
            mockedSignalingMessageReceiver,
            mockedSignalingMessageSender,
            encryptionRequired,
            senderKeyRing,
            null,
            isScreenSharer
        )

    // The tracks are public fields initialized in the constructor, which a plain mock would skip.
    private fun createScreenStream(): MediaStream {
        val stream = Mockito.mock(MediaStream::class.java, Mockito.withSettings().useConstructor(0L))
        Mockito.`when`(stream.id).thenReturn("NCSS")
        stream.videoTracks.add(Mockito.mock(VideoTrack::class.java))
        return stream
    }

    private fun captureOfferObserver(): SdpObserver {
        val captor = ArgumentCaptor.forClass(SdpObserver::class.java)
        verify(mockedPeerConnection).createOffer(captor.capture(), any(MediaConstraints::class.java))
        return captor.value
    }

    private fun captureSentMessage(): NCSignalingMessageDto {
        val captor = ArgumentCaptor.forClass(NCSignalingMessageDto::class.java)
        verify(mockedSignalingMessageSender).send(captor.capture())
        return captor.value
    }

    @Test
    fun testScreenSharerSendsOfferWithoutDataChannel() {
        createWrapper(isScreenSharer = true)

        verify(mockedPeerConnection).addTrack(any(VideoTrack::class.java), eq(listOf("NCSS")))
        verify(mockedPeerConnection).createOffer(any(SdpObserver::class.java), any(MediaConstraints::class.java))
        verify(mockedPeerConnection, never()).createDataChannel(anyString(), any())
    }

    @Test
    fun testNotOffererDoesNotSendOfferWithoutSharingScreen() {
        createWrapper(isScreenSharer = false, localStream = null, videoStreamType = "video")

        verify(mockedPeerConnection, never()).createOffer(
            any(SdpObserver::class.java),
            any(MediaConstraints::class.java)
        )
    }

    @Test
    fun testScreenSharerListensWithScreenSharingRoomType() {
        createWrapper(isScreenSharer = true)
        createWrapper(isScreenSharer = false, localStream = null)

        verify(mockedSignalingMessageReceiver).addListener(
            any(SignalingMessageReceiver.WebRtcMessageListener::class.java),
            eq(REMOTE_SESSION_ID),
            eq(SignalingMessageReceiver.ROOM_TYPE_SCREEN_SHARING)
        )
        verify(mockedSignalingMessageReceiver).addListener(
            any(SignalingMessageReceiver.WebRtcMessageListener::class.java),
            eq(REMOTE_SESSION_ID),
            eq("screen")
        )
    }

    @Test
    fun testScreenSharerSetsItselfAsBroadcaster() {
        createWrapper(isScreenSharer = true)

        captureOfferObserver().onCreateSuccess(SessionDescription(SessionDescription.Type.OFFER, SDP))

        val message = captureSentMessage()
        assertEquals("offer", message.type)
        assertEquals("screen", message.roomType)
        assertEquals(REMOTE_SESSION_ID, message.to)
        assertEquals(LOCAL_SESSION_ID, message.broadcaster)
    }

    @Test
    fun testOtherConnectionsDoNotSetBroadcaster() {
        createWrapper(isScreenSharer = false, hasMCU = true, localStream = null, videoStreamType = "video")

        captureOfferObserver().onCreateSuccess(SessionDescription(SessionDescription.Type.OFFER, SDP))

        assertNull(captureSentMessage().broadcaster)
    }

    @Test
    fun testEncryptedScreenShareIsEncryptedBeforeOffer() {
        val sender = Mockito.mock(RtpSender::class.java)
        Mockito.`when`(mockedPeerConnection.senders).thenReturn(listOf(sender))
        val keyRing = FakeKeyRing()

        createWrapper(isScreenSharer = true, hasMCU = true, encryptionRequired = true, senderKeyRing = keyRing)

        assertEquals(1, keyRing.encryptors.size)
        val inOrder = Mockito.inOrder(sender, mockedPeerConnection)
        inOrder.verify(sender).setFrameEncryptor(keyRing.encryptors[0])
        inOrder.verify(mockedPeerConnection).createOffer(
            any(SdpObserver::class.java),
            any(MediaConstraints::class.java)
        )
    }

    @Test
    fun testEncryptedScreenShareWithoutKeysIsRefused() {
        for (hasMCU in listOf(true, false)) {
            assertThrows(IllegalStateException::class.java) {
                createWrapper(isScreenSharer = true, hasMCU = hasMCU, encryptionRequired = true)
            }
        }

        verify(mockedPeerConnectionFactory, never()).createPeerConnection(
            any(PeerConnection.RTCConfiguration::class.java),
            any(PeerConnection.Observer::class.java)
        )
    }

    companion object {
        private const val LOCAL_SESSION_ID = "a-local-session-id"
        private const val REMOTE_SESSION_ID = "b-remote-session-id"
        private const val SDP = "v=0\r\nm=video 9 UDP/TLS/RTP/SAVPF 96\r\na=rtpmap:96 H264/90000\r\n"
    }
}
