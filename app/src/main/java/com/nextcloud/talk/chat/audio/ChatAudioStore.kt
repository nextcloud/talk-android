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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
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
    private val requestedKeys: MutableSet<ChatAudioKey> = ConcurrentHashMap.newKeySet()
    private val positionChangedKeys: MutableSet<ChatAudioKey> = ConcurrentHashMap.newKeySet()

    private val _metadata = MutableStateFlow<Map<ChatAudioKey, ChatAudioMetadata>>(emptyMap())
    val metadata: StateFlow<Map<ChatAudioKey, ChatAudioMetadata>> = _metadata.asStateFlow()

    fun metadataFor(key: ChatAudioKey): ChatAudioMetadata? = _metadata.value[key]

    /** Reads what is persisted about [key], at most once per process. */
    fun load(key: ChatAudioKey) {
        if (requestedKeys.add(key)) {
            ioScope.launch {
                val persisted = fileFor(key)
                    .takeIf { it.isFile }
                    ?.let { readQuietly(it) }
                    ?.let { ChatAudioMetadataCodec.decode(it) }
                if (persisted != null) {
                    _metadata.update { current -> current + (key to merge(key, current[key], persisted)) }
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

    private fun change(key: ChatAudioKey, transform: (ChatAudioMetadata) -> ChatAudioMetadata) {
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

    private fun fileFor(key: ChatAudioKey): File =
        File(directory, key.mediaId.replace(UNSAFE_FILE_NAME_CHARACTERS, "_"))

    private fun readQuietly(file: File): ByteArray? =
        try {
            file.readBytes()
        } catch (e: IOException) {
            Log.w(TAG, "Failed to read audio metadata", e)
            null
        }

    companion object {
        private val TAG = ChatAudioStore::class.java.simpleName
        private const val METADATA_DIRECTORY = "chat_audio_metadata"
        private val UNSAFE_FILE_NAME_CHARACTERS = Regex("[^A-Za-z0-9_-]")
    }
}
