/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2017-2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.utils

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.Presentation
import androidx.media3.transformer.AudioEncoderSettings
import androidx.media3.transformer.Composition
import androidx.media3.transformer.DefaultEncoderFactory
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import androidx.media3.transformer.VideoEncoderSettings
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Downscales videos to [TARGET_SHORT_SIDE]p and re-encodes them to reduce their upload size.
 */
object VideoCompressor {

    private val TAG = VideoCompressor::class.java.simpleName
    private const val TARGET_SHORT_SIDE = 540
    private const val TARGET_FRAME_RATE_FPS = 24
    private const val TARGET_VIDEO_BITRATE_BPS = 600_000L
    private const val TARGET_AUDIO_BITRATE_BPS = 64_000L
    private const val BITS_PER_BYTE = 8L
    private const val MILLIS_PER_SECOND = 1000L
    private const val MIN_TIMEOUT_MS = 30_000L
    private const val TIMEOUT_MULTIPLIER = 3L
    private const val COMPRESSED_FILE_SUFFIX = "_compressed.mp4"
    private const val PROGRESS_POLL_INTERVAL_MS = 300L

    data class VideoInfo(val width: Int, val height: Int, val durationMs: Long, val sizeBytes: Long)

    fun isCompressible(mimeType: String?): Boolean = mimeType != null && mimeType.startsWith(Mimetype.VIDEO_PREFIX)

    /**
     * Reads the on-disk size, pixel dimensions and duration of [sourceFile] via metadata only.
     */
    @Suppress("TooGenericExceptionCaught")
    fun readVideoInfo(sourceFile: File): VideoInfo? {
        Log.d(TAG, "reading video info for ${sourceFile.name}")
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(sourceFile.absolutePath)
            val width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull()
            val height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull()
            val durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
            if (width == null || height == null || durationMs == null) {
                null
            } else {
                VideoInfo(width, height, durationMs, sourceFile.length())
            }
        } catch (e: Exception) {
            Log.w(TAG, "could not read video info for ${sourceFile.name}", e)
            null
        } finally {
            retriever.release()
        }
    }

    /**
     * Estimates the dimensions and size [sourceFile] would have after [compress], based on a fixed
     * target bitrate rather than a real (expensive) transcode.
     */
    fun estimateCompression(sourceFile: File): VideoInfo? {
        Log.d(TAG, "estimating compression for ${sourceFile.name}")
        val original = readVideoInfo(sourceFile) ?: return null
        val (targetWidth, targetHeight) = scaledDimensions(original.width, original.height)
        val estimatedBytes = (TARGET_VIDEO_BITRATE_BPS + TARGET_AUDIO_BITRATE_BPS) / BITS_PER_BYTE *
            (original.durationMs / MILLIS_PER_SECOND)
        return VideoInfo(targetWidth, targetHeight, original.durationMs, minOf(estimatedBytes, original.sizeBytes))
    }

    /**
     * Returns a downscaled, re-encoded copy of [sourceFile] in the app cache directory, or null if
     * the video was already small enough, could not be read, or transcoding failed/timed out.
     *
     * [onProgress] is invoked with a 0-100 value from a background thread while transcoding runs.
     */
    @Suppress("ReturnCount")
    fun compress(context: Context, sourceFile: File, onProgress: (Int) -> Unit = {}): File? {
        Log.d(TAG, "compressing ${sourceFile.name}")
        val original = readVideoInfo(sourceFile) ?: return null
        if (minOf(original.width, original.height) <= TARGET_SHORT_SIDE) return null

        val outputFile = File(context.cacheDir, sourceFile.nameWithoutExtension + COMPRESSED_FILE_SUFFIX)
        outputFile.delete()

        val success = runTransform(context, sourceFile, outputFile, original.durationMs, onProgress)
        return if (success && outputFile.exists()) outputFile else null
    }

    private fun scaledDimensions(width: Int, height: Int): Pair<Int, Int> {
        Log.d(TAG, "scaling dimensions for ${width}x$height")
        val shortSide = minOf(width, height)
        if (shortSide <= TARGET_SHORT_SIDE) return width to height
        val scale = TARGET_SHORT_SIDE.toDouble() / shortSide
        return (width * scale).roundToInt() to (height * scale).roundToInt()
    }

    /**
     * Media3's [Transformer] must be created and driven from a thread with a prepared [Looper][android.os.Looper].
     * Using a dedicated, short-lived [HandlerThread] here (instead of the app's main thread) keeps any number of
     * concurrent/queued compressions from ever delaying UI rendering.
     */
    @OptIn(UnstableApi::class)
    @Suppress("ReturnCount")
    private fun runTransform(
        context: Context,
        sourceFile: File,
        outputFile: File,
        durationMs: Long,
        onProgress: (Int) -> Unit
    ): Boolean {
        val handlerThread = HandlerThread("VideoCompressor-${sourceFile.name}").apply { start() }
        try {
            val latch = CountDownLatch(1)
            var success = false
            val transformerRef = AtomicReference<Transformer?>()
            val handler = Handler(handlerThread.looper)

            handler.post {
                Log.d(TAG, "starting video compression for ${sourceFile.name}")
                val encoderFactory = DefaultEncoderFactory.Builder(context)
                    .setRequestedVideoEncoderSettings(
                        VideoEncoderSettings.Builder().setBitrate(TARGET_VIDEO_BITRATE_BPS.toInt()).build()
                    )
                    .setRequestedAudioEncoderSettings(
                        AudioEncoderSettings.Builder().setBitrate(TARGET_AUDIO_BITRATE_BPS.toInt()).build()
                    )
                    .setEnableFallback(true)
                    .build()

                val transformer = Transformer.Builder(context)
                    .setEncoderFactory(encoderFactory)
                    .addListener(object : Transformer.Listener {
                        override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                            success = true
                            Log.d(TAG, "video compression completed for ${sourceFile.name}")
                            latch.countDown()
                        }

                        override fun onError(
                            composition: Composition,
                            exportResult: ExportResult,
                            exportException: ExportException
                        ) {
                            Log.e(TAG, "video compression failed for ${sourceFile.name}", exportException)
                            latch.countDown()
                        }
                    })
                    .build()
                transformerRef.set(transformer)

                // Capping the frame rate (frames are dropped, not sped up) noticeably cuts the total
                // amount of GL/encoder work per second during transcoding. Uncapped, a 30/60fps source
                // keeps its full rate through the whole effects pipeline, which on some devices pegs
                // the GPU hard enough that the entire UI appears frozen for the transcode's duration
                // even though it's all running on a background thread.
                val editedMediaItem = EditedMediaItem.Builder(MediaItem.fromUri(Uri.fromFile(sourceFile)))
                    .setFrameRate(TARGET_FRAME_RATE_FPS)
                    .setEffects(Effects(emptyList(), listOf(Presentation.createForShortSide(TARGET_SHORT_SIDE))))
                    .build()

                transformer.start(editedMediaItem, outputFile.absolutePath)
                pollProgress(handler, transformerRef.get(), latch, onProgress)
            }

            val timeoutMs = max(MIN_TIMEOUT_MS, durationMs * TIMEOUT_MULTIPLIER)
            val completedInTime = latch.await(timeoutMs, TimeUnit.MILLISECONDS)
            if (!completedInTime) {
                Log.w(TAG, "video compression timed out for ${sourceFile.name}")
                handler.post { transformerRef.get()?.cancel() }
            }
            return completedInTime && success
        } finally {
            handlerThread.quitSafely()
        }
    }

    /**
     * Polls [Transformer.getProgress] on its own looper thread every [PROGRESS_POLL_INTERVAL_MS]
     * until [latch] counts down (transcode completed, failed, or was cancelled).
     */
    @OptIn(UnstableApi::class)
    private fun pollProgress(
        handler: Handler,
        transformer: Transformer?,
        latch: CountDownLatch,
        onProgress: (Int) -> Unit
    ) {
        if (transformer == null || latch.count == 0L) return

        val progressHolder = ProgressHolder()
        if (transformer.getProgress(progressHolder) == Transformer.PROGRESS_STATE_AVAILABLE) {
            onProgress(progressHolder.progress)
        }
        handler.postDelayed({ pollProgress(handler, transformer, latch, onProgress) }, PROGRESS_POLL_INTERVAL_MS)
    }
}
