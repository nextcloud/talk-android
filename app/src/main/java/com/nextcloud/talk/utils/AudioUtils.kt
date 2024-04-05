/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2023 Julius Linus <julius.linus@nextcloud.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
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
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
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
object AudioUtils : DefaultLifecycleObserver {
    private val TAG = AudioUtils::class.java.simpleName
    private const val VALUE_10 = 10
    private const val TIME_LIMIT = 3000
    private const val DEFAULT_SIZE = 500
    private enum class LifeCycleFlag {
        PAUSED,
        RESUMED
    }
    private lateinit var currentLifeCycleFlag: LifeCycleFlag

    override fun onResume(owner: LifecycleOwner) {
        super.onResume(owner)
        currentLifeCycleFlag = LifeCycleFlag.RESUMED
    }

    override fun onPause(owner: LifecycleOwner) {
        super.onPause(owner)
        currentLifeCycleFlag = LifeCycleFlag.PAUSED
    }

    /**
     * Suspension function, returns a FloatArray of size 500, containing the values of an audio file squeezed between
     * [0,1)
     */
    @Throws(IOException::class)
    suspend fun audioFileToFloatArray(file: File): FloatArray {
        return suspendCoroutine {
            // Used to keep track of the time it took to process the audio file
            val startTime = SystemClock.elapsedRealtime()

            // Always a FloatArray of Size 500
            var result: MutableList<Float>? = mutableListOf()

            // Setting the file path to the audio file
            val path = file.path
            val mediaExtractor = MediaExtractor()
            mediaExtractor.setDataSource(path)

            // Basically just boilerplate to set up meta data for the audio file
            val mediaFormat = mediaExtractor.getTrackFormat(0)
            // Frame rate is required for encoders, optional for decoders. So we set it to null here.
            mediaFormat.setString(MediaFormat.KEY_FRAME_RATE, null)
            mediaFormat.setInteger(MediaFormat.KEY_FRAME_RATE, 0)

            mediaExtractor.release()

            // More Boiler plate to set up the codec
            val mediaCodecList = MediaCodecList(MediaCodecList.ALL_CODECS)
            val codecName = mediaCodecList.findDecoderForFormat(mediaFormat)
            val mediaCodec = MediaCodec.createByCodecName(codecName)

            /**
             ************************************ Media Codec *******************************************
             *                                        │
             *                      INPUT BUFFERS     │            OUTPUT BUFFERS
             *                                        │
             * ┌────────────────┐             ┌───────┴────────┐              ┌─────────────────┐
             * │                │ Empty Buffer│                │ Filled Buffer│                 │
             * │                │    [][][]   │                │ [-][-][-]    │                 │
             * │                │ ◄───────────┤                ├────────────► │                 │
             * │     Client     │             │     Codec      │              │      Client     │
             * │                │             │                │              │                 │
             * │                ├───────────► │                │ ◄────────────┤                 │
             * │                │ [-][-][-]   │                │   [][][]     │                 │
             * └────────────────┘Filled Buffer└───────┬────────┘Empty Buffer  └─────────────────┘
             *                                        │
             *   Client provides                      │                         Client consumes
             *   input Data                           │                         output data
             *
             ********************************************************************************************
             */
            mediaCodec.setCallback(object : MediaCodec.Callback() {
                private var extractor: MediaExtractor? = null
                val tempList = mutableListOf<Float>()
                init {
                    // Setting up the extractor to be guaranteed not null
                    extractor = MediaExtractor()
                    try {
                        extractor!!.setDataSource(path)
                        extractor!!.selectTrack(0)
                    } catch (e: IOException) {
                        e.printStackTrace()
                    }
                }

                override fun onInputBufferAvailable(codec: MediaCodec, index: Int) {
                    // Boiler plate, Extracts a buffer of encoded audio data to be sent to the codec for processing
                    val byteBuffer = codec.getInputBuffer(index)
                    if (byteBuffer != null && extractor != null) {
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
                    // Boiler plate to get the audio data in a usable form
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

                    // Cancels the process if it ends, exceeds the time limit, or the activity falls out of view
                    val currTime = SystemClock.elapsedRealtime() - startTime
                    if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM > 0 ||
                        currTime > TIME_LIMIT ||
                        currentLifeCycleFlag == LifeCycleFlag.PAUSED
                    ) {
                        Log.d(
                            TAG,
                            "Processing ended with time: $currTime \n" +
                                "Is finished: ${info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM > 0} \n" +
                                "Lifecycle state: $currentLifeCycleFlag"
                        )
                        codec.stop()
                        codec.release()
                        extractor!!.release()
                        extractor = null
                        result = if (currTime < TIME_LIMIT) {
                            tempList
                        } else {
                            Log.e(TAG, "Error in MediaCodec Callback:\n\tonOutputBufferAvailable: Time limit exceeded")
                            null
                        }
                    }
                }

                override fun onError(codec: MediaCodec, e: CodecException) {
                    Log.e(TAG, "Error in MediaCodec Callback: \n$e")
                    codec.stop()
                    codec.release()
                    extractor!!.release()
                    extractor = null
                    result = null
                }

                override fun onOutputFormatChanged(codec: MediaCodec, format: MediaFormat) {
                    // unused atm
                }
            })

            // More Boiler plate to start the codec
            mediaFormat.setInteger(MediaFormat.KEY_PCM_ENCODING, AudioFormat.ENCODING_PCM_16BIT)
            mediaCodec.configure(mediaFormat, null, null, 0)
            mediaCodec.start()

            // This runs until the codec finishes, the time limit is exceeded, or an error occurs
            // If the time limit is exceed or an error occurs, the result should be null or empty
            var currTime = SystemClock.elapsedRealtime() - startTime
            while (result != null &&
                result!!.size <= 0 &&
                currTime < TIME_LIMIT // Guarantees Execution stops after 3 seconds
            ) {
                currTime = SystemClock.elapsedRealtime() - startTime
                continue
            }

            if (result != null && result!!.size > DEFAULT_SIZE) {
                it.resume(shrinkFloatArray(result!!.toFloatArray(), DEFAULT_SIZE))
            } else {
                it.resume(FloatArray(DEFAULT_SIZE))
            }
        }
    }

    fun shrinkFloatArray(data: FloatArray, size: Int): FloatArray {
        val result = FloatArray(size)
        val scale = data.size / size
        var begin = 0
        var end = scale
        for (i in 0 until size) {
            val arr = data.copyOfRange(begin, end)
            result[i] = arr.average().toFloat()
            begin += scale
            end += scale
        }

        return result
    }
}
