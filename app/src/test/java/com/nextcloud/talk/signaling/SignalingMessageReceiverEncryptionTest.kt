/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.signaling

import com.nextcloud.talk.call.e2ee.EncryptionMessage
import com.nextcloud.talk.models.json.signaling.NCMessagePayloadDto
import com.nextcloud.talk.models.json.signaling.NCSignalingMessageDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoInteractions

class SignalingMessageReceiverEncryptionTest {

    private lateinit var signalingMessageReceiver: SignalingMessageReceiver

    @Before
    fun setUp() {
        // SignalingMessageReceiver is abstract to prevent direct instantiation without calling the appropriate
        // protected methods.
        signalingMessageReceiver = object : SignalingMessageReceiver() {}
    }

    private fun signalingMessage(from: String?, payloadType: String?): NCSignalingMessageDto =
        NCSignalingMessageDto().apply {
            this.from = from
            type = "message"
            payload = NCMessagePayloadDto().apply {
                type = payloadType
                id = "theId"
                identity = "theIdentity"
                key = mapOf("type" to 1, "body" to "theBody")
                error = "theError"
            }
        }

    @Test
    fun testAddEncryptionMessageListenerWithNullListener() {
        assertThrows(IllegalArgumentException::class.java) {
            signalingMessageReceiver.addListener(null as SignalingMessageReceiver.EncryptionMessageListener?)
        }
    }

    @Test
    fun testEncryptionMessage() {
        val listener = mock(SignalingMessageReceiver.EncryptionMessageListener::class.java)
        signalingMessageReceiver.addListener(listener)

        signalingMessageReceiver.processSignalingMessage(signalingMessage("theSessionId", "encryption.setkey"))

        verify(listener).onEncryptionMessage(
            "theSessionId",
            EncryptionMessage(
                "encryption.setkey",
                id = "theId",
                identity = "theIdentity",
                key = mapOf("type" to 1, "body" to "theBody"),
                error = "theError"
            )
        )
    }

    @Test
    fun testEncryptionMessageWithUnknownPayloadType() {
        val listener = mock(SignalingMessageReceiver.EncryptionMessageListener::class.java)
        signalingMessageReceiver.addListener(listener)

        signalingMessageReceiver.processSignalingMessage(signalingMessage("theSessionId", "encryption.unknown"))
        signalingMessageReceiver.processSignalingMessage(signalingMessage("theSessionId", null))

        verifyNoInteractions(listener)
    }

    @Test
    fun testEncryptionMessageWithoutSender() {
        val listener = mock(SignalingMessageReceiver.EncryptionMessageListener::class.java)
        signalingMessageReceiver.addListener(listener)

        signalingMessageReceiver.processSignalingMessage(signalingMessage(null, "encryption.start"))

        verifyNoInteractions(listener)
    }

    @Test
    fun testEncryptionMessageIsNotPassedToOtherListeners() {
        val callParticipantListener = mock(SignalingMessageReceiver.CallParticipantMessageListener::class.java)
        val webRtcListener = mock(SignalingMessageReceiver.WebRtcMessageListener::class.java)
        signalingMessageReceiver.addListener(callParticipantListener, "theSessionId")
        signalingMessageReceiver.addListener(webRtcListener, "theSessionId", "video")

        signalingMessageReceiver.processSignalingMessage(signalingMessage("theSessionId", "encryption.start"))

        verifyNoInteractions(callParticipantListener)
        verifyNoInteractions(webRtcListener)
    }

    @Test
    fun testEncryptionMessageAfterRemovingListener() {
        val listener = mock(SignalingMessageReceiver.EncryptionMessageListener::class.java)
        signalingMessageReceiver.addListener(listener)
        signalingMessageReceiver.removeListener(listener)

        signalingMessageReceiver.processSignalingMessage(signalingMessage("theSessionId", "encryption.gotkey"))

        verifyNoInteractions(listener)
    }

    @Test
    fun testPayloadRoundTrip() {
        val message =
            EncryptionMessage("encryption.start", id = "theId", identity = "theIdentity", key = "theOneTimeKey")

        assertEquals(message, EncryptionMessage.fromPayload(message.toPayload()))
    }
}
