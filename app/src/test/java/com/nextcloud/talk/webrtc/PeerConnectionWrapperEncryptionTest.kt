/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.webrtc

import com.nextcloud.talk.call.e2ee.FrameKeyRing
import com.nextcloud.talk.signaling.SignalingMessageReceiver
import com.nextcloud.talk.signaling.SignalingMessageSender
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.eq
import org.mockito.Mockito
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.kotlin.argThat
import org.webrtc.DataChannel
import org.webrtc.FrameDecryptor
import org.webrtc.FrameEncryptor
import org.webrtc.MediaConstraints
import org.webrtc.PeerConnection
import org.webrtc.PeerConnectionFactory
import org.webrtc.RtpReceiver
import org.webrtc.RtpSender
import org.webrtc.SessionDescription

class PeerConnectionWrapperEncryptionTest {

    private class FakeKeyRing : FrameKeyRing {
        val encryptors = ArrayList<FrameEncryptor>()
        val decryptors = ArrayList<FrameDecryptor>()

        override fun setKey(key: ByteArray, keyIndex: Int): Boolean = true

        override fun createFrameEncryptor(): FrameEncryptor = FrameEncryptor { 0 }.also { encryptors.add(it) }

        override fun createFrameDecryptor(): FrameDecryptor = FrameDecryptor { 0 }.also { decryptors.add(it) }

        override fun dispose() {
            // Not needed in tests
        }
    }

    private lateinit var mockedPeerConnection: PeerConnection
    private lateinit var mockedPeerConnectionFactory: PeerConnectionFactory
    private lateinit var mockedSignalingMessageReceiver: SignalingMessageReceiver
    private lateinit var mockedSignalingMessageSender: SignalingMessageSender
    private lateinit var observerCaptor: ArgumentCaptor<PeerConnection.Observer>

    @Before
    fun setUp() {
        mockedPeerConnection = Mockito.mock(PeerConnection::class.java)
        mockedPeerConnectionFactory = Mockito.mock(PeerConnectionFactory::class.java)
        mockedSignalingMessageReceiver = Mockito.mock(SignalingMessageReceiver::class.java)
        mockedSignalingMessageSender = Mockito.mock(SignalingMessageSender::class.java)
        observerCaptor = ArgumentCaptor.forClass(PeerConnection.Observer::class.java)
        Mockito.`when`(
            mockedPeerConnectionFactory.createPeerConnection(
                any(PeerConnection.RTCConfiguration::class.java),
                observerCaptor.capture()
            )
        ).thenReturn(mockedPeerConnection)
        val mockedStatusDataChannel = Mockito.mock(DataChannel::class.java)
        Mockito.`when`(mockedStatusDataChannel.label()).thenReturn("status")
        Mockito.`when`(mockedPeerConnection.createDataChannel(eq("status"), any())).thenReturn(mockedStatusDataChannel)
    }

    private fun createWrapper(
        senderKeyRing: FrameKeyRing?,
        receiverKeyRing: FrameKeyRing?,
        isMCUPublisher: Boolean = senderKeyRing != null
    ): PeerConnectionWrapper =
        PeerConnectionWrapper(
            mockedPeerConnectionFactory,
            ArrayList(),
            MediaConstraints(),
            "the-session-id",
            "the-local-session-id",
            null,
            isMCUPublisher,
            true,
            "video",
            mockedSignalingMessageReceiver,
            mockedSignalingMessageSender,
            senderKeyRing,
            receiverKeyRing
        )

    @Test
    fun testEncryptorIsAttachedToEverySender() {
        val senders = listOf(Mockito.mock(RtpSender::class.java), Mockito.mock(RtpSender::class.java))
        Mockito.`when`(mockedPeerConnection.senders).thenReturn(senders)
        val keyRing = FakeKeyRing()

        createWrapper(senderKeyRing = keyRing, receiverKeyRing = null)

        assertEquals(2, keyRing.encryptors.size)
        verify(senders[0]).setFrameEncryptor(keyRing.encryptors[0])
        verify(senders[1]).setFrameEncryptor(keyRing.encryptors[1])
    }

    @Test
    fun testDecryptorIsAttachedWhenTrackIsAdded() {
        val keyRing = FakeKeyRing()
        createWrapper(senderKeyRing = null, receiverKeyRing = keyRing)
        val receiver = Mockito.mock(RtpReceiver::class.java)

        observerCaptor.value.onAddTrack(receiver, arrayOf())

        assertEquals(1, keyRing.decryptors.size)
        verify(receiver).setFrameDecryptor(keyRing.decryptors[0])
    }

    @Test
    fun testNothingIsAttachedWithoutKeyRings() {
        val sender = Mockito.mock(RtpSender::class.java)
        Mockito.`when`(mockedPeerConnection.senders).thenReturn(listOf(sender))
        createWrapper(senderKeyRing = null, receiverKeyRing = null, isMCUPublisher = true)
        val receiver = Mockito.mock(RtpReceiver::class.java)

        observerCaptor.value.onAddTrack(receiver, arrayOf())

        verify(sender, never()).setFrameEncryptor(any())
        verify(receiver, never()).setFrameDecryptor(any())
    }

    @Test
    fun testVideoCodecPreference() {
        val sdp = "v=0\r\nm=video 9 UDP/TLS/RTP/SAVPF 96 98\r\na=rtpmap:96 H264/90000\r\na=rtpmap:98 VP8/90000\r\n"

        createWrapper(senderKeyRing = null, receiverKeyRing = FakeKeyRing())
        createWrapper(senderKeyRing = null, receiverKeyRing = null)
        val (encryptedListener, plainListener) = captureWebRtcListeners()

        encryptedListener.onOffer(sdp, null)
        verify(mockedPeerConnection).setRemoteDescription(any(), descriptionWith("m=video 9 UDP/TLS/RTP/SAVPF 98 96"))

        plainListener.onOffer(sdp, null)
        verify(mockedPeerConnection).setRemoteDescription(any(), descriptionWith("m=video 9 UDP/TLS/RTP/SAVPF 96 98"))
    }

    private fun captureWebRtcListeners(): List<SignalingMessageReceiver.WebRtcMessageListener> {
        val captor = ArgumentCaptor.forClass(SignalingMessageReceiver.WebRtcMessageListener::class.java)
        verify(mockedSignalingMessageReceiver, Mockito.times(2))
            .addListener(captor.capture(), eq("the-session-id"), eq("video"))
        return captor.allValues
    }

    private fun descriptionWith(mediaLine: String): SessionDescription = argThat { description.contains(mediaLine) }
}
