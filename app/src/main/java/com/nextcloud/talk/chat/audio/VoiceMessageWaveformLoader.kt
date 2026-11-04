/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.chat.audio

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.annotation.OptIn
import androidx.annotation.WorkerThread
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSpec
import com.nextcloud.talk.utils.AudioUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Computes the waveform of voice messages. The file is read through the audio cache, so it is not downloaded a
 * second time once the player loaded it, and the result is kept in [ChatAudioStore].
 */
@OptIn(UnstableApi::class)
@Singleton
class VoiceMessageWaveformLoader @Inject constructor(
    private val context: Context,
    private val dataSources: ChatAudioDataSources,
    private val store: ChatAudioStore
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val loadingKeys: MutableSet<ChatAudioKey> = ConcurrentHashMap.newKeySet()

    fun load(key: ChatAudioKey, uri: Uri, cacheKey: String?) {
        if (store.metadataFor(key)?.waveform == null && loadingKeys.add(key)) {
            scope.launch {
                try {
                    extractWaveform(uri, cacheKey)?.let { store.rememberWaveform(key, it) }
                } finally {
                    loadingKeys.remove(key)
                }
            }
        }
    }

    @WorkerThread
    private fun extractWaveform(uri: Uri, cacheKey: String?): FloatArray? {
        var file: File? = null
        return try {
            file = File.createTempFile(TEMP_FILE_PREFIX, null, context.cacheDir)
            copyFromCache(DataSpec.Builder().setUri(uri).setKey(cacheKey).build(), file)
            // A file that cannot be decoded gets an empty waveform, so it is not decoded again and again.
            AudioUtils.extractWaveformLevels(file)?.let { Waveforms.reduce(it, WAVEFORM_BARS) } ?: FloatArray(0)
        } catch (e: IOException) {
            Log.w(TAG, "Failed to load a voice message for its waveform", e)
            null
        } finally {
            file?.delete()
        }
    }

    private fun copyFromCache(dataSpec: DataSpec, target: File) {
        val dataSource = dataSources.cacheDataSourceFactory.createDataSource()
        try {
            dataSource.open(dataSpec)
            FileOutputStream(target).use { output ->
                val buffer = ByteArray(BUFFER_SIZE)
                var read = dataSource.read(buffer, 0, buffer.size)
                while (read != C.RESULT_END_OF_INPUT) {
                    output.write(buffer, 0, read)
                    read = dataSource.read(buffer, 0, buffer.size)
                }
            }
        } finally {
            dataSource.close()
        }
    }

    companion object {
        private val TAG = VoiceMessageWaveformLoader::class.java.simpleName
        private const val TEMP_FILE_PREFIX = "voice_message_waveform"
        private const val BUFFER_SIZE = 16 * 1024
        const val WAVEFORM_BARS = 100
    }
}
