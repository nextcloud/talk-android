/*
 * Nextcloud Talk application
 *
 * @author Julius Linus
 * Copyright (C) 2023 Julius Linus <julius.linus@nextcloud.com>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.nextcloud.talk.utils

import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaCodec.CodecException
import android.media.MediaCodecList
import android.media.MediaExtractor
import android.media.MediaFormat
import android.os.SystemClock
import android.util.Log
import java.io.File
import java.io.IOException
import java.nio.ByteOrder
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import kotlin.math.abs

/**
 * AudioUtils are for processing raw audio using android's low level APIs, for more information read here
 * [MediaCodec documentation](https://developer.android.com/reference/android/media/MediaCodec)
 */
object AudioUtils {
    private val TAG = AudioUtils::class.java.simpleName
    private const val VALUE_10 = 10
    private const val TIME_LIMIT = 5000

    /**
     * Suspension function, returns a FloatArray containing the values of an audio file squeezed between [0,1)
     */
    @Throws(IOException::class)
    suspend fun audioFileToFloatArray(file: File, size: Int): FloatArray {
        return suspendCoroutine {
            val startTime = SystemClock.elapsedRealtime()
            var result = mutableListOf<Float>()
            val path = file.path
            val mediaExtractor = MediaExtractor()
            mediaExtractor.setDataSource(path)

            val mediaFormat = mediaExtractor.getTrackFormat(0)
            mediaFormat.setString(MediaFormat.KEY_FRAME_RATE, null)
            mediaFormat.setInteger(MediaFormat.KEY_FRAME_RATE, 0)

            mediaExtractor.release()

            val mediaCodecList = MediaCodecList(MediaCodecList.ALL_CODECS)
            val codecName = mediaCodecList.findDecoderForFormat(mediaFormat)
            val mediaCodec = MediaCodec.createByCodecName(codecName)
            mediaCodec.setCallback(object : MediaCodec.Callback() {
                private var extractor: MediaExtractor? = null
                val tempList = mutableListOf<Float>()
                override fun onInputBufferAvailable(codec: MediaCodec, index: Int) {
                    if (extractor == null) {
                        extractor = MediaExtractor()
                        try {
                            extractor!!.setDataSource(path)
                            extractor!!.selectTrack(0)
                        } catch (e: IOException) {
                            e.printStackTrace()
                        }
                    }
                    val byteBuffer = codec.getInputBuffer(index)
                    if (byteBuffer != null) {
                        val sampleSize = extractor!!.readSampleData(byteBuffer, 0)
                        if (sampleSize > 0) {
                            val isOver = !extractor!!.advance()
                            codec.queueInputBuffer(
                                index,
                                0,
                                sampleSize,
                                extractor!!.sampleTime,
                                if (isOver) MediaCodec.BUFFER_FLAG_END_OF_STREAM else 0
                            )
                        }
                    }
                }

                override fun onOutputBufferAvailable(codec: MediaCodec, index: Int, info: MediaCodec.BufferInfo) {
                    val outputBuffer = codec.getOutputBuffer(index)
                    val bufferFormat = codec.getOutputFormat(index)
                    val samples = outputBuffer!!.order(ByteOrder.nativeOrder()).asShortBuffer()
                    val numChannels = bufferFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                    if (index < 0 || index >= numChannels) {
                        return
                    }
                    val sampleLength = (samples.remaining() / numChannels)
                    // Squeezes the value of each sample between [0,1) using y = (x-1)/x
                    for (i in 0 until sampleLength) {
                        val x = abs(samples[i * numChannels + index].toInt()) / VALUE_10
                        val y = (if (x > 0) ((x - 1) / x.toFloat()) else x.toFloat())
                        tempList.add(y)
                    }
                    codec.releaseOutputBuffer(index, false)
                    val currTime = SystemClock.elapsedRealtime() - startTime
                    if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM > 0 || currTime > TIME_LIMIT) {
                        codec.stop()
                        codec.release()
                        extractor!!.release()
                        extractor = null
                        if (currTime < TIME_LIMIT) {
                            result = tempList
                        } else {
                            Log.d(TAG, "time limit exceeded")
                        }
                    }
                }

                override fun onError(codec: MediaCodec, e: CodecException) {
                    Log.e(TAG, "Error in MediaCodec Callback: \n$e")
                    codec.stop()
                    codec.release()
                    extractor!!.release()
                    extractor = null
                    result = tempList
                }

                override fun onOutputFormatChanged(codec: MediaCodec, format: MediaFormat) {
                    // unused atm
                }
            })
            mediaFormat.setInteger(MediaFormat.KEY_PCM_ENCODING, AudioFormat.ENCODING_PCM_16BIT)
            mediaCodec.configure(mediaFormat, null, null, 0)
            mediaCodec.start()
            while (result.size <= 0) {
                continue
            }
            it.resume(shrinkFloatArray(result.toFloatArray(), size))
        }
    }

    private fun shrinkFloatArray(data: FloatArray, size: Int): FloatArray {
        val result = FloatArray(size)
        val scale = data.size / size
        var begin = 0
        var end = scale
        for (i in 0 until size) {
            val arr = data.copyOfRange(begin, end)
            var sum = 0f
            for (j in arr.indices) {
                sum += arr[j]
            }
            result[i] = (sum / arr.size)
            begin += scale
            end += scale
        }

        return result
    }
}
