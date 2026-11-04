/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2023 Julius Linus <julius.linus@nextcloud.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.utils

import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.os.SystemClock
import android.util.Log
import androidx.annotation.WorkerThread
import com.nextcloud.talk.chat.audio.WaveformLevels
import java.io.File
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * AudioUtils are for processing raw audio using android's low level APIs, for more information read here
 * [MediaCodec documentation](https://developer.android.com/reference/android/media/MediaCodec)
 */
object AudioUtils {
    private val TAG = AudioUtils::class.java.simpleName
    private const val AUDIO_MIME_TYPE_PREFIX = "audio/"
    private const val CODEC_TIMEOUT_US = 10_000L
    private const val MAX_DECODING_TIME_MS = 15_000L
    private const val PCM_16_BIT_FULL_SCALE = 32_768.0

    /**
     * Decodes the first audio track of [file] and returns the loudness of each decoded chunk, see
     * [WaveformLevels], or null if the file cannot be decoded completely. Decoding gives up after a few seconds,
     * as levels of only the beginning of a very long file would not match its playback progress.
     */
    @WorkerThread
    fun extractWaveformLevels(file: File): FloatArray? {
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        return try {
            extractor.setDataSource(file.path)
            val track = findAudioTrack(extractor)
            track?.let { (index, format) ->
                extractor.selectTrack(index)
                val decoder = MediaCodec.createDecoderByType(format.getString(MediaFormat.KEY_MIME).orEmpty())
                codec = decoder
                decoder.configure(format, null, null, 0)
                decoder.start()
                decode(extractor, decoder)
            }
        } catch (e: IOException) {
            Log.w(TAG, "Failed to decode audio file for its waveform", e)
            null
        } catch (e: IllegalStateException) {
            Log.w(TAG, "Failed to decode audio file for its waveform", e)
            null
        } catch (e: IllegalArgumentException) {
            Log.w(TAG, "Failed to decode audio file for its waveform", e)
            null
        } finally {
            codec?.release()
            extractor.release()
        }
    }

    private fun findAudioTrack(extractor: MediaExtractor): Pair<Int, MediaFormat>? =
        (0 until extractor.trackCount)
            .map { index -> index to extractor.getTrackFormat(index) }
            .firstOrNull { (_, format) ->
                format.getString(MediaFormat.KEY_MIME)?.startsWith(AUDIO_MIME_TYPE_PREFIX) == true
            }

    private fun decode(extractor: MediaExtractor, codec: MediaCodec): FloatArray? {
        val levels = WaveformLevels()
        val info = MediaCodec.BufferInfo()
        val deadline = SystemClock.elapsedRealtime() + MAX_DECODING_TIME_MS
        var isFloatOutput = false
        var isInputDone = false
        var isOutputDone = false
        while (!isOutputDone && SystemClock.elapsedRealtime() < deadline) {
            if (!isInputDone) {
                isInputDone = queueInput(extractor, codec)
            }
            val outputIndex = codec.dequeueOutputBuffer(info, CODEC_TIMEOUT_US)
            if (outputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                isFloatOutput = codec.outputFormat.isFloatPcm()
            } else if (outputIndex >= 0) {
                codec.getOutputBuffer(outputIndex)?.let { addLevel(it, isFloatOutput, levels) }
                codec.releaseOutputBuffer(outputIndex, false)
                isOutputDone = info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
            }
        }
        return if (isOutputDone) levels.toArray() else null
    }

    /** Feeds the next sample to the decoder and returns true once the end of the input was queued. */
    private fun queueInput(extractor: MediaExtractor, codec: MediaCodec): Boolean {
        val inputIndex = codec.dequeueInputBuffer(CODEC_TIMEOUT_US)
        val buffer = if (inputIndex >= 0) codec.getInputBuffer(inputIndex) else null
        var isEndOfInput = false
        if (buffer != null) {
            val sampleSize = extractor.readSampleData(buffer, 0)
            isEndOfInput = sampleSize < 0
            if (isEndOfInput) {
                codec.queueInputBuffer(inputIndex, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
            } else {
                codec.queueInputBuffer(inputIndex, 0, sampleSize, extractor.sampleTime, 0)
                extractor.advance()
            }
        }
        return isEndOfInput
    }

    private fun addLevel(buffer: ByteBuffer, isFloatOutput: Boolean, levels: WaveformLevels) {
        val pcm = buffer.order(ByteOrder.nativeOrder())
        var sumOfSquares = 0.0
        var sampleCount = 0
        if (isFloatOutput) {
            val samples = pcm.asFloatBuffer()
            while (samples.hasRemaining()) {
                val sample = samples.get().toDouble()
                sumOfSquares += sample * sample
                sampleCount++
            }
        } else {
            val samples = pcm.asShortBuffer()
            while (samples.hasRemaining()) {
                val sample = samples.get() / PCM_16_BIT_FULL_SCALE
                sumOfSquares += sample * sample
                sampleCount++
            }
        }
        levels.addChunk(sumOfSquares, sampleCount)
    }

    private fun MediaFormat.isFloatPcm(): Boolean =
        containsKey(MediaFormat.KEY_PCM_ENCODING) &&
            getInteger(MediaFormat.KEY_PCM_ENCODING) == AudioFormat.ENCODING_PCM_FLOAT
}
