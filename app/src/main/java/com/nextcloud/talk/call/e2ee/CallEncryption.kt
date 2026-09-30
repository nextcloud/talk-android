/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.call.e2ee

import android.util.Log
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.security.SecureRandom
import java.util.Base64
import java.util.UUID

/**
 * Exchanges the frame keys of a call with every other session in the room.
 *
 * Port of spreed src/utils/e2ee/encryption.js, so keys can be exchanged with the web client. Each participant sends
 * its own random key to everyone else, encrypted with a one-to-one Olm session over signaling, so the signaling
 * server and the MCU never see it.
 *
 * All state is handled on a single-threaded dispatcher; [keyRing] can be called from any thread.
 */
@Suppress("TooManyFunctions")
class CallEncryption(
    private val ownSessionId: String,
    private val olm: OlmCrypto,
    private val frameCrypto: FrameCrypto,
    private val sendMessage: (sessionId: String, message: EncryptionMessage) -> Unit,
    dispatcher: CoroutineDispatcher = Dispatchers.Default.limitedParallelism(1),
    /** Debounce period of key updates and timeout of requests, the web client uses the same value for both. */
    private val periodMs: Long = WEB_CLIENT_PERIOD_MS
) {
    /** Our own keys, used by the encryptors of all our senders. */
    val ownKeyRing: FrameKeyRing = frameCrypto.createKeyRing()

    private class SessionData(
        var session: OlmSession? = null,
        var startMessageId: String? = null,
        var lastKey: ByteArray? = null
    )

    private class PendingRequest(val timeout: Job, val completion: CompletableDeferred<Unit>)

    private class RemoteKey(val key: ByteArray, val index: Int)

    private val scope = CoroutineScope(SupervisorJob() + dispatcher)

    private var key = generateKey()
    private var keyIndex = 0
    private var isRotating = false
    private var ratchetJob: Job? = null
    private var rotateJob: Job? = null
    private var isClosed = false

    private val sessions = HashMap<String, SessionData>()
    private val pendingRequests = HashMap<String, PendingRequest>()

    // Read from WebRTC threads when receivers are created, so guarded by its own lock
    private val remoteKeyRings = HashMap<String, FrameKeyRing>()

    init {
        ownKeyRing.setKey(key, keyIndex)
    }

    /** The keys of a remote participant, used by the decryptors of all its streams. */
    fun keyRing(sessionId: String): FrameKeyRing =
        synchronized(remoteKeyRings) {
            remoteKeyRings.getOrPut(sessionId) { frameCrypto.createKeyRing() }
        }

    fun usersJoined(sessionIds: List<String>) {
        scope.launch {
            if (isClosed) return@launch

            // Of every pair of sessions the one with the higher id starts the Olm session, compared like
            // JavaScript compares strings (String.compareTo compares UTF-16 code units as well)
            for (sessionId in sessionIds) {
                if (sessionId < ownSessionId) {
                    startSession(sessionId)
                }
            }

            // A ratcheted key keeps new participants from decrypting what was sent before they joined, while
            // everyone else follows by ratcheting on their own
            ratchetJob = debounce(ratchetJob) { ratchetKey() }
        }
    }

    fun usersLeft(sessionIds: List<String>) {
        scope.launch {
            if (isClosed) return@launch

            synchronized(remoteKeyRings) {
                for (sessionId in sessionIds) {
                    sessions.remove(sessionId)
                    remoteKeyRings.remove(sessionId)
                }
            }

            // A new key keeps participants that left from decrypting what is sent from now on
            rotateJob = debounce(rotateJob) { rotateKey() }
        }
    }

    fun handleMessage(sessionId: String, message: EncryptionMessage) {
        scope.launch {
            if (isClosed) return@launch

            when (message.type) {
                EncryptionMessage.START -> processStartSession(sessionId, message)
                EncryptionMessage.FINISH -> processFinishSession(sessionId, message)
                EncryptionMessage.SET_KEY -> processSetKey(sessionId, message)
                EncryptionMessage.GOT_KEY -> processGotKey(sessionId, message)
                EncryptionMessage.ERROR -> Log.w(TAG, "Received error from $sessionId: ${message.error}")
            }
        }
    }

    fun close() {
        scope.launch {
            isClosed = true
            ratchetJob?.cancel()
            rotateJob?.cancel()
            ratchetJob = null
            rotateJob = null

            for (request in pendingRequests.values) {
                request.timeout.cancel()
                request.completion.complete(Unit)
            }
            pendingRequests.clear()
            sessions.clear()

            synchronized(remoteKeyRings) {
                remoteKeyRings.values.forEach { it.dispose() }
                remoteKeyRings.clear()
            }
            ownKeyRing.dispose()

            scope.cancel()
        }
    }

    private fun startSession(sessionId: String) {
        val sessionData = sessions.getOrPut(sessionId) { SessionData() }
        if (sessionData.session != null || sessionData.startMessageId != null) {
            Log.d(TAG, "Session with $sessionId already exists or is being started")
            return
        }

        val oneTimeKey = olm.createOneTimeKey()
        val messageId = UUID.randomUUID().toString()
        sessionData.startMessageId = messageId

        addRequest(messageId) {
            Log.w(TAG, "Starting session with $sessionId timed out")
            sessions[sessionId]?.startMessageId = null
        }

        sendMessage(
            sessionId,
            EncryptionMessage(EncryptionMessage.START, id = messageId, identity = olm.identityKey, key = oneTimeKey)
        )
    }

    // The other side started a session, the one creating it from the start message is the "outbound" side in Olm
    // terms
    private fun processStartSession(sessionId: String, message: EncryptionMessage) {
        if (sessions[sessionId]?.session != null) {
            Log.w(TAG, "Already has a session with $sessionId")
            sendError(sessionId, "Session already created")
            return
        }

        val messageId = message.id
        val theirIdentityKey = message.identity
        val theirOneTimeKey = message.key as? String
        if (messageId == null || theirIdentityKey == null || theirOneTimeKey == null) {
            Log.w(TAG, "Invalid start message from $sessionId")
            return
        }

        val session = runCatching { olm.createOutboundSession(theirIdentityKey, theirOneTimeKey) }
            .onFailure { Log.w(TAG, "Could not create outbound session with $sessionId", it) }
            .getOrNull() ?: return

        sessions.getOrPut(sessionId) { SessionData() }.session = session
        Log.d(TAG, "Created outbound Olm session with $sessionId")

        sendMessage(sessionId, EncryptionMessage(EncryptionMessage.FINISH, id = messageId, key = encryptKey(session)))
    }

    private fun processFinishSession(sessionId: String, message: EncryptionMessage) {
        val sessionData = sessions[sessionId]
        if (sessionData?.session != null) {
            Log.w(TAG, "Already has a session with $sessionId")
            sendError(sessionId, "Session already created")
            return
        }

        val messageId = message.id
        if (messageId == null || messageId != sessionData?.startMessageId) {
            Log.w(TAG, "Received finish with wrong id from $sessionId")
            sendError(sessionId, "Finish has wrong id")
            return
        }

        val preKeyMessage = message.olmKey
        if (preKeyMessage == null) {
            Log.w(TAG, "Invalid finish message from $sessionId")
            return
        }

        val (session, plaintext) = runCatching { olm.createInboundSession(preKeyMessage) }
            .onFailure { Log.w(TAG, "Could not create inbound session with $sessionId", it) }
            .getOrNull() ?: return

        // The finish message already carries the remote key
        val remoteKey = parseKey(plaintext)

        sessionData.session = session
        sessionData.startMessageId = null
        resolveRequest(messageId)

        Log.d(
            TAG,
            "Created inbound Olm session with $sessionId" + if (remoteKey == null) ", without a readable key" else ""
        )

        if (remoteKey != null) {
            sessionData.lastKey = remoteKey.key
            keyRing(sessionId).setKey(remoteKey.key, remoteKey.index)
        }

        sendKey(sessionId)
    }

    private fun processSetKey(sessionId: String, message: EncryptionMessage) {
        val session = sessions[sessionId]?.session
        if (session == null) {
            Log.w(TAG, "No session with $sessionId for setting key")
            sendError(sessionId, "No session for setting key")
            return
        }

        val messageId = message.id
        val encryptedKey = message.olmKey
        if (messageId == null || encryptedKey == null) {
            Log.w(TAG, "Invalid setkey message from $sessionId")
            return
        }

        val remoteKey = decryptKey(encryptedKey, session)
        if (remoteKey == null) {
            Log.w(TAG, "Could not decrypt setkey from $sessionId, key type ${encryptedKey.type}")
            return
        }

        updateRemoteKey(sessionId, remoteKey)

        // Confirms the key, the sender waits for this before encrypting with it
        sendMessage(sessionId, EncryptionMessage(EncryptionMessage.GOT_KEY, id = messageId, key = encryptKey(session)))
    }

    private fun processGotKey(sessionId: String, message: EncryptionMessage) {
        val session = sessions[sessionId]?.session
        if (session == null) {
            Log.w(TAG, "No session with $sessionId for confirming key")
            sendError(sessionId, "No session for confirming key")
            return
        }

        message.olmKey?.let { decryptKey(it, session) }?.let { updateRemoteKey(sessionId, it) }
        message.id?.let { resolveRequest(it) }
    }

    // Completes once the recipient confirmed the key or the request timed out
    private fun sendKey(sessionId: String): CompletableDeferred<Unit> {
        val completion = CompletableDeferred<Unit>()
        val session = sessions[sessionId]?.session
        if (session == null) {
            completion.complete(Unit)
            return completion
        }

        val messageId = UUID.randomUUID().toString()
        addRequest(messageId, completion) {
            Log.w(TAG, "Sending key to $sessionId timed out")
        }

        sendMessage(sessionId, EncryptionMessage(EncryptionMessage.SET_KEY, id = messageId, key = encryptKey(session)))

        return completion
    }

    private fun updateRemoteKey(sessionId: String, remoteKey: RemoteKey) {
        val sessionData = sessions[sessionId] ?: return
        if (sessionData.lastKey?.contentEquals(remoteKey.key) == true) {
            return
        }

        sessionData.lastKey = remoteKey.key
        keyRing(sessionId).setKey(remoteKey.key, remoteKey.index)
    }

    private fun ratchetKey() {
        if (isRotating) {
            Log.d(TAG, "Not ratcheting key, currently rotating")
            return
        }

        // Not distributed, receivers find the ratcheted key on their own
        key = frameCrypto.ratchetKey(key)
        ownKeyRing.setKey(key, keyIndex)
    }

    private fun rotateKey() {
        isRotating = true
        key = generateKey()
        keyIndex++

        val newKey = key
        val newKeyIndex = keyIndex
        Log.d(TAG, "Rotating own key to index $newKeyIndex")

        val confirmations = sessions.keys.toList().map { sendKey(it) }

        // Only encrypt with the new key once everyone has it or timed out
        scope.launch {
            confirmations.awaitAll()
            if (isClosed) return@launch

            ownKeyRing.setKey(newKey, newKeyIndex)
            isRotating = false
        }
    }

    // The key as the web client sends it, JSON encrypted with the Olm session
    private fun encryptKey(session: OlmSession): Map<String, Any> {
        val json = "{\"key\":\"${Base64.getEncoder().encodeToString(key)}\",\"index\":$keyIndex}"
        return EncryptionMessage.olmKey(session.encrypt(json))
    }

    private fun decryptKey(encryptedKey: OlmMessage, session: OlmSession): RemoteKey? =
        runCatching { session.decrypt(encryptedKey) }.getOrNull()?.let { parseKey(it) }

    // Parses {"key": "<base64>", "index": <int>} without a JSON library, the values can not contain quotes
    private fun parseKey(json: String): RemoteKey? {
        val key = KEY_PATTERN.find(json)?.groupValues?.get(1) ?: return null
        val index = INDEX_PATTERN.find(json)?.groupValues?.get(1)?.toIntOrNull() ?: return null
        val keyBytes = runCatching { Base64.getDecoder().decode(key) }.getOrNull() ?: return null
        return RemoteKey(keyBytes, index)
    }

    private fun sendError(sessionId: String, error: String) {
        sendMessage(sessionId, EncryptionMessage(EncryptionMessage.ERROR, error = error))
    }

    private fun addRequest(
        messageId: String,
        completion: CompletableDeferred<Unit> = CompletableDeferred(),
        onTimeout: () -> Unit
    ) {
        val timeout = scope.launch {
            delay(periodMs)
            val request = pendingRequests.remove(messageId) ?: return@launch
            onTimeout()
            request.completion.complete(Unit)
        }
        pendingRequests[messageId] = PendingRequest(timeout, completion)
    }

    private fun resolveRequest(messageId: String) {
        val request = pendingRequests.remove(messageId) ?: return
        request.timeout.cancel()
        request.completion.complete(Unit)
    }

    // Runs the action once no new call came in for the debounce period, like the web client's debounce
    private fun debounce(job: Job?, action: () -> Unit): Job {
        job?.cancel()
        return scope.launch {
            delay(periodMs)
            if (!isClosed) {
                action()
            }
        }
    }

    companion object {
        private val TAG = CallEncryption::class.java.simpleName

        private const val WEB_CLIENT_PERIOD_MS = 5000L
        private const val KEY_SIZE = 32

        private val KEY_PATTERN = Regex("\"key\"\\s*:\\s*\"([A-Za-z0-9+/=]*)\"")
        private val INDEX_PATTERN = Regex("\"index\"\\s*:\\s*(\\d+)")

        private val random = SecureRandom()

        private fun generateKey(): ByteArray = ByteArray(KEY_SIZE).also { random.nextBytes(it) }
    }
}
