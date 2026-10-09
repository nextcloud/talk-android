/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.call.e2ee

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.webrtc.FrameDecryptor
import org.webrtc.FrameEncryptor

/**
 * Two CallEncryption instances exchange keys through a fake signaling connection, with a fake Olm that only
 * tracks message kinds and a fake key ring that records the keys set on it.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CallEncryptionTest {

    private class Message(val from: String, val to: String, val message: EncryptionMessage) {
        val keyType: Int? get() = message.olmKey?.type
    }

    // Delivers messages between the instances and records them, through the payload DTO like on the wire
    private class Signaling {
        private val participants = HashMap<String, CallEncryption>()
        val messages = ArrayList<Message>()

        fun sender(sessionId: String): (String, EncryptionMessage) -> Unit =
            { recipient, message ->
                val wireMessage = EncryptionMessage.fromPayload(message.toPayload())!!
                messages.add(Message(sessionId, recipient, wireMessage))
                participants[recipient]?.handleMessage(sessionId, wireMessage)
            }

        fun add(sessionId: String, callEncryption: CallEncryption) {
            participants[sessionId] = callEncryption
        }

        fun sent(type: String, from: String): List<Message> =
            messages.filter {
                it.message.type == type &&
                    it.from == from
            }
    }

    private class FakeOlmSession(private var nextType: Int) : OlmSession {
        override fun encrypt(plaintext: String): OlmMessage =
            OlmMessage(nextType, plaintext).also { nextType = OlmMessage.TYPE_NORMAL }

        override fun decrypt(message: OlmMessage): String {
            nextType = OlmMessage.TYPE_NORMAL
            return message.body
        }
    }

    // The side creating the outbound session sends a pre-key message first, everything else is normal
    private class FakeOlm(override val identityKey: String) : OlmCrypto {
        override fun createOneTimeKey(): String = "otk-$identityKey"

        override fun createOutboundSession(theirIdentityKey: String, theirOneTimeKey: String): OlmSession {
            require(theirOneTimeKey == "otk-$theirIdentityKey") { "Invalid one-time key" }
            return FakeOlmSession(OlmMessage.TYPE_PRE_KEY)
        }

        override fun createInboundSession(preKeyMessage: OlmMessage): Pair<OlmSession, String> {
            require(preKeyMessage.type == OlmMessage.TYPE_PRE_KEY) { "Not a pre-key message" }
            return FakeOlmSession(OlmMessage.TYPE_NORMAL) to preKeyMessage.body
        }
    }

    private class FakeKeyRing : FrameKeyRing {
        val keys = ArrayList<Pair<ByteArray, Int>>()
        var disposed = false

        override fun setKey(key: ByteArray, keyIndex: Int): Boolean {
            keys.add(key to keyIndex)
            return key.isNotEmpty()
        }

        override fun createFrameEncryptor(): FrameEncryptor = FrameEncryptor { 0 }

        override fun createFrameDecryptor(): FrameDecryptor = FrameDecryptor { 0 }

        override fun dispose() {
            disposed = true
        }
    }

    private class FakeFrameCrypto : FrameCrypto {
        val keyRings = ArrayList<FakeKeyRing>()

        override fun createKeyRing(): FrameKeyRing = FakeKeyRing().also { keyRings.add(it) }

        override fun ratchetKey(key: ByteArray): ByteArray = key.map { (it + 1).toByte() }.toByteArray()
    }

    // "session-b" sorts after "session-a", so it starts the Olm session
    private val higherSessionId = "session-b"
    private val lowerSessionId = "session-a"

    private val signaling = Signaling()
    private val frameCrypto = FakeFrameCrypto()

    private fun TestScope.callEncryption(sessionId: String): CallEncryption =
        CallEncryption(
            ownSessionId = sessionId,
            olm = FakeOlm(sessionId),
            frameCrypto = frameCrypto,
            sendMessage = signaling.sender(sessionId),
            dispatcher = StandardTestDispatcher(testScheduler),
            periodMs = PERIOD_MS
        ).also { signaling.add(sessionId, it) }

    private fun TestScope.exchangeKeys(higher: CallEncryption, lower: CallEncryption) {
        // Both see the same join event, including their own session
        val joined = listOf(lowerSessionId, higherSessionId)
        higher.usersJoined(joined)
        lower.usersJoined(joined)
        advanceUntilIdle()
    }

    private fun ownKeyRing(callEncryption: CallEncryption): FakeKeyRing = callEncryption.ownKeyRing as FakeKeyRing

    @Test
    fun higherSessionStartsAndKeysAreExchanged() =
        runTest {
            val higher = callEncryption(higherSessionId)
            val lower = callEncryption(lowerSessionId)

            exchangeKeys(higher, lower)

            assertEquals(
                listOf(
                    EncryptionMessage.START to higherSessionId,
                    EncryptionMessage.FINISH to lowerSessionId,
                    EncryptionMessage.SET_KEY to higherSessionId,
                    EncryptionMessage.GOT_KEY to lowerSessionId
                ),
                signaling.messages.map { it.message.type to it.from }
            )
            assertTrue(signaling.sent(EncryptionMessage.START, lowerSessionId).isEmpty())
            assertTrue(signaling.sent(EncryptionMessage.ERROR, lowerSessionId).isEmpty())
            assertTrue(signaling.sent(EncryptionMessage.ERROR, higherSessionId).isEmpty())

            // Each side stored the other side's key at index 0
            val higherKey = ownKeyRing(higher).keys.first()
            val lowerKey = ownKeyRing(lower).keys.first()
            assertEquals(0, higherKey.second)
            assertEquals(0, lowerKey.second)
            assertEquals(
                higherKey.first.toList(),
                (lower.keyRing(higherSessionId) as FakeKeyRing).keys.single().first.toList()
            )
            assertEquals(
                lowerKey.first.toList(),
                (higher.keyRing(lowerSessionId) as FakeKeyRing).keys.single().first.toList()
            )
            assertNotEquals(higherKey.first.toList(), lowerKey.first.toList())
        }

    @Test
    fun messagesHaveWebClientFormat() =
        runTest {
            val higher = callEncryption(higherSessionId)
            val lower = callEncryption(lowerSessionId)

            exchangeKeys(higher, lower)

            val start = signaling.sent(EncryptionMessage.START, higherSessionId).first()
            assertNotNull(start.message.id)
            assertEquals(higherSessionId, start.message.identity)
            assertEquals("otk-$higherSessionId", start.message.key)

            // The side answering the start creates the outbound Olm session, so its first message is a pre-key
            // message, everything after the first reply is a normal one
            val finish = signaling.sent(EncryptionMessage.FINISH, lowerSessionId).first()
            assertEquals(start.message.id, finish.message.id)
            assertEquals(OlmMessage.TYPE_PRE_KEY, finish.keyType)

            val setKey = signaling.sent(EncryptionMessage.SET_KEY, higherSessionId).first()
            assertEquals(OlmMessage.TYPE_NORMAL, setKey.keyType)
            assertTrue(setKey.message.olmKey!!.body.matches(Regex("\\{\"key\":\"[A-Za-z0-9+/]{43}=\",\"index\":0}")))

            val gotKey = signaling.sent(EncryptionMessage.GOT_KEY, lowerSessionId).first()
            assertEquals(setKey.message.id, gotKey.message.id)
            assertEquals(OlmMessage.TYPE_NORMAL, gotKey.keyType)
        }

    @Test
    fun distributesNewKeyWhenSomeoneLeaves() =
        runTest {
            val higher = callEncryption(higherSessionId)
            val lower = callEncryption(lowerSessionId)
            exchangeKeys(higher, lower)
            val keysBefore = ownKeyRing(higher).keys.size

            higher.usersLeft(listOf("session-c"))
            advanceTimeBy(PERIOD_MS - 1)
            assertEquals(1, signaling.sent(EncryptionMessage.SET_KEY, higherSessionId).size)

            advanceUntilIdle()

            val setKeys = signaling.sent(EncryptionMessage.SET_KEY, higherSessionId)
            assertEquals(2, setKeys.size)
            assertEquals(2, signaling.sent(EncryptionMessage.GOT_KEY, lowerSessionId).size)
            assertTrue(setKeys.last().message.olmKey!!.body.endsWith("\"index\":1}"))

            // The new key is only used once the recipient confirmed it, at the next index
            val ownKeys = ownKeyRing(higher).keys
            assertEquals(keysBefore + 1, ownKeys.size)
            assertEquals(1, ownKeys.last().second)
            assertEquals(1, (lower.keyRing(higherSessionId) as FakeKeyRing).keys.last().second)
        }

    @Test
    fun ratchetsWithoutDistributingKeyWhenSomeoneJoins() =
        runTest {
            val higher = callEncryption(higherSessionId)
            val lower = callEncryption(lowerSessionId)
            exchangeKeys(higher, lower)
            val messagesBefore = signaling.messages.size
            val ownKeys = ownKeyRing(higher).keys
            val keysBefore = ownKeys.size

            // "session-c" sorts last, so it would start the session with us
            higher.usersJoined(listOf("session-c"))
            advanceUntilIdle()

            assertEquals(messagesBefore, signaling.messages.size)
            assertEquals(keysBefore + 1, ownKeys.size)
            assertEquals(0, ownKeys.last().second)
            assertEquals(frameCrypto.ratchetKey(ownKeys[keysBefore - 1].first).toList(), ownKeys.last().first.toList())
        }

    @Test
    fun answersKeyWithoutSessionWithError() =
        runTest {
            val higher = callEncryption(higherSessionId)

            higher.handleMessage(
                "session-unknown",
                EncryptionMessage(EncryptionMessage.SET_KEY, id = "1", key = mapOf("type" to 1, "body" to "invalid"))
            )
            advanceUntilIdle()

            val error = signaling.sent(EncryptionMessage.ERROR, higherSessionId).single()
            assertEquals("session-unknown", error.to)
            assertEquals("No session for setting key", error.message.error)
        }

    @Test
    fun startTimesOutAndCanBeRetried() =
        runTest {
            val higher = callEncryption(higherSessionId)

            higher.usersJoined(listOf(lowerSessionId))
            advanceTimeBy(PERIOD_MS + 1)
            higher.usersJoined(listOf(lowerSessionId))
            advanceUntilIdle()

            assertEquals(2, signaling.sent(EncryptionMessage.START, higherSessionId).size)
        }

    @Test
    fun releasesKeyRingsOnClose() =
        runTest {
            val higher = callEncryption(higherSessionId)
            val lower = callEncryption(lowerSessionId)
            exchangeKeys(higher, lower)
            val remoteKeyRing = higher.keyRing(lowerSessionId) as FakeKeyRing

            higher.usersLeft(listOf("session-c"))
            higher.close()
            advanceUntilIdle()

            assertTrue(remoteKeyRing.disposed)
            assertTrue(ownKeyRing(higher).disposed)
            // The rotation was cancelled by close
            assertEquals(1, signaling.sent(EncryptionMessage.SET_KEY, higherSessionId).size)
            assertFalse(ownKeyRing(lower).disposed)
        }

    @Test
    fun keyRingIsKeptPerSession() =
        runTest {
            val higher = callEncryption(higherSessionId)

            assertSame(higher.keyRing(lowerSessionId), higher.keyRing(lowerSessionId))
            assertNotSame(higher.keyRing(lowerSessionId), higher.keyRing("session-c"))
        }

    @Test
    fun recognizesEncryptionMessages() {
        assertTrue(EncryptionMessage.isEncryptionMessage("encryption.start"))
        assertTrue(EncryptionMessage.isEncryptionMessage("encryption.gotkey"))
        assertFalse(EncryptionMessage.isEncryptionMessage("offer"))
        assertFalse(EncryptionMessage.isEncryptionMessage(null))
    }

    companion object {
        private const val PERIOD_MS = 100L
    }
}
