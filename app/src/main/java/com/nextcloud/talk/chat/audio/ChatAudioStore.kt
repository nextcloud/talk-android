/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.chat.audio

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Remembers [ChatAudioMetadata] of audio messages for the whole process, so progress, duration and waveform
 * survive leaving a conversation. It is also written to a small file cache, which keeps durations, waveforms and
 * the position of long recordings across app restarts.
 */
@Singleton
class ChatAudioStore internal constructor(private val directory: File, ioDispatcher: CoroutineDispatcher) {

    @Inject
    constructor(context: Context) : this(
        File(context.cacheDir, METADATA_DIRECTORY),
        Dispatchers.IO.limitedParallelism(1)
    )

    private val ioScope = CoroutineScope(SupervisorJob() + ioDispatcher)
    private val loads = ConcurrentHashMap<ChatAudioKey, Deferred<Unit>>()
    private val positionChangedKeys: MutableSet<ChatAudioKey> = ConcurrentHashMap.newKeySet()
    private val removedAccounts: MutableSet<Long> = ConcurrentHashMap.newKeySet()

    private val _metadata = MutableStateFlow<Map<ChatAudioKey, ChatAudioMetadata>>(emptyMap())
    val metadata: StateFlow<Map<ChatAudioKey, ChatAudioMetadata>> = _metadata.asStateFlow()

    private val _accountRemovals = MutableSharedFlow<Long>(extraBufferCapacity = ACCOUNT_REMOVALS_BUFFER)

    /** Emits the internal user id of every account removed by [forgetAccount]. */
    val accountRemovals: SharedFlow<Long> = _accountRemovals.asSharedFlow()

    fun metadataFor(key: ChatAudioKey): ChatAudioMetadata? = _metadata.value[key]

    /** Reads what is persisted about [key], at most once per process. */
    fun load(key: ChatAudioKey) {
        startLoad(key)
    }

    /** Like [load], but waits until what is persisted about [key] is known. */
    suspend fun loadNow(key: ChatAudioKey): ChatAudioMetadata? {
        startLoad(key)?.await()
        return metadataFor(key)
    }

    private fun startLoad(key: ChatAudioKey): Deferred<Unit>? =
        if (isAccountRemoved(key.internalUserId)) {
            null
        } else {
            loads.computeIfAbsent(key) {
                ioScope.async {
                    val persisted = fileFor(key)
                        .takeIf { it.isFile }
                        ?.let { readQuietly(it) }
                        ?.let { ChatAudioMetadataCodec.decode(it) }
                    if (persisted != null && !isAccountRemoved(key.internalUserId)) {
                        _metadata.update { current -> current + (key to merge(key, current[key], persisted)) }
                        // Values changed before the load, like a position that was reset, also replace the file.
                        if (metadataFor(key) != persisted) {
                            persist(key)
                        }
                    }
                }
            }
        }

    fun rememberDuration(key: ChatAudioKey, durationMs: Long) {
        if (durationMs > 0L) {
            change(key) { it.copy(durationMs = durationMs) }
        }
    }

    fun rememberPosition(key: ChatAudioKey, positionMs: Long, durationMs: Long) {
        positionChangedKeys.add(key)
        change(key) {
            it.copy(
                positionMs = positionMs.coerceAtLeast(0L),
                durationMs = durationMs.takeIf { duration -> duration > 0L } ?: it.durationMs
            )
        }
    }

    fun forgetPosition(key: ChatAudioKey) {
        positionChangedKeys.add(key)
        if ((metadataFor(key)?.positionMs ?: 0L) != 0L) {
            change(key) { it.copy(positionMs = 0L) }
        }
    }

    fun rememberWaveform(key: ChatAudioKey, waveform: FloatArray) {
        change(key) { it.copy(waveform = waveform.toList()) }
    }

    fun isAccountRemoved(internalUserId: Long): Boolean = internalUserId in removedAccounts

    /**
     * Deletes everything remembered about the audio messages of a removed account, in memory and on disk, and
     * ignores them from now on. Internal user ids are never reused, so this lasts for the rest of the process.
     */
    fun forgetAccount(internalUserId: Long) {
        removedAccounts.add(internalUserId)
        val belongsToAccount = { key: ChatAudioKey -> key.internalUserId == internalUserId }
        _metadata.update { current -> current.filterKeys { !belongsToAccount(it) } }
        loads.keys.removeIf(belongsToAccount)
        positionChangedKeys.removeIf(belongsToAccount)
        _accountRemovals.tryEmit(internalUserId)
        ioScope.launch {
            val prefix = fileNameOf(ChatAudioKey.mediaIdPrefixOf(internalUserId))
            directory.listFiles { file -> file.name.startsWith(prefix) }
                ?.forEach { file ->
                    if (!file.delete()) {
                        Log.w(TAG, "Failed to delete audio metadata")
                    }
                }
        }
    }

    private fun change(key: ChatAudioKey, transform: (ChatAudioMetadata) -> ChatAudioMetadata) {
        if (isAccountRemoved(key.internalUserId)) {
            return
        }
        val before = _metadata.value[key]
        _metadata.update { current -> current + (key to transform(current[key] ?: ChatAudioMetadata())) }
        if (_metadata.value[key] != before) {
            persist(key)
        }
    }

    private fun merge(key: ChatAudioKey, current: ChatAudioMetadata?, persisted: ChatAudioMetadata) =
        ChatAudioMetadata(
            durationMs = current?.durationMs?.takeIf { it > 0L } ?: persisted.durationMs,
            positionMs = if (key in positionChangedKeys) current?.positionMs ?: 0L else persisted.positionMs,
            waveform = current?.waveform ?: persisted.waveform
        )

    private fun persist(key: ChatAudioKey) {
        ioScope.launch {
            val metadata = _metadata.value[key]
            if (metadata != null) {
                try {
                    directory.mkdirs()
                    fileFor(key).writeBytes(ChatAudioMetadataCodec.encode(metadata))
                } catch (e: IOException) {
                    Log.w(TAG, "Failed to store audio metadata", e)
                }
            }
        }
    }

    private fun fileFor(key: ChatAudioKey): File = File(directory, fileNameOf(key.mediaId))

    companion object {
        private const val METADATA_DIRECTORY = "chat_audio_metadata"
        private const val ACCOUNT_REMOVALS_BUFFER = 8
    }
}

private val TAG = ChatAudioStore::class.java.simpleName
private val unsafeFileNameCharacters = Regex("[^A-Za-z0-9_-]")

private fun fileNameOf(mediaId: String): String = mediaId.replace(unsafeFileNameCharacters, "_")

private fun readQuietly(file: File): ByteArray? =
    try {
        file.readBytes()
    } catch (e: IOException) {
        Log.w(TAG, "Failed to read audio metadata", e)
        null
    }
