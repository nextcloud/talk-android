/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.attachmentpreview

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.text.format.Formatter
import androidx.core.net.toUri
import androidx.exifinterface.media.ExifInterface
import com.nextcloud.talk.utils.FileUtils
import com.nextcloud.talk.utils.ImageCompressor
import com.nextcloud.talk.utils.VideoCompressor
import java.io.File
import java.io.IOException
import kotlin.math.roundToInt

private const val VIDEO_ROTATION_DEGREES_90 = 90
private const val VIDEO_ROTATION_DEGREES_270 = 270
private const val VIDEO_FRAME_MAX_DIMENSION_PX = 720

internal fun isCompressible(mimeType: String?): Boolean =
    ImageCompressor.isCompressible(mimeType) || VideoCompressor.isCompressible(mimeType)

/**
 * [current] is the detail text for the file's active compress setting, [alternate] is what it
 * would read as under the other setting. Both are computed regardless of [FileDescription]'s own
 * `compress` flag, so the large preview's detail chip can reserve width for whichever is wider and
 * never resize when the HQ toggle flips which one is actually shown.
 */
private data class DetailVariants(val current: String, val alternate: String)

@Suppress("ReturnCount")
internal fun describeFile(context: Context, uriString: String, compress: Boolean): FileDescription {
    val uri = uriString.toUri()
    val name = FileUtils.getFileName(uri, context)
    val mimeType = FileUtils.resolveMimeType(context, uri)
    val kind = mediaKind(mimeType)
    val file = FileUtils.getFileFromUri(context, uri)
        ?: return FileDescription(uriString, name, kind, mimeType, null)
    val sizeOnly = formatSize(context, file.length())

    val variants = when (kind) {
        MediaKind.IMAGE -> describeImageDetail(context, file, compress, sizeOnly)
        MediaKind.VIDEO -> describeVideoDetail(context, file, compress, sizeOnly)
        MediaKind.OTHER -> "$name, $sizeOnly".let { DetailVariants(it, it) }
    }
    val aspectRatio = when (kind) {
        MediaKind.IMAGE -> imageAspectRatio(file)
        MediaKind.VIDEO -> videoAspectRatio(file)
        MediaKind.OTHER -> null
    }
    val videoThumbnail = if (kind == MediaKind.VIDEO) extractVideoFrame(file) else null
    return FileDescription(
        uriString,
        name,
        kind,
        mimeType,
        variants.current,
        variants.alternate,
        aspectRatio,
        videoThumbnail
    )
}

@Suppress("ReturnCount")
private fun imageAspectRatio(file: File): Float? {
    val info = ImageCompressor.readImageInfo(file) ?: return null
    if (info.height <= 0) return null
    val (width, height) = if (isSidewaysExifOrientation(file)) {
        info.height to info.width
    } else {
        info.width to info.height
    }
    if (height <= 0) return null
    return width.toFloat() / height.toFloat()
}

// BitmapFactory's decode bounds are the raw, un-rotated pixel grid, so a photo whose sensor-native
// orientation is landscape (with an EXIF tag marking it as rotated for portrait display) reports
// swapped width/height here compared to how it actually renders. Swap them back so the aspect ratio
// used to size the preview matches what Coil (which does honor EXIF) actually draws.
private fun isSidewaysExifOrientation(file: File): Boolean =
    try {
        val orientation = ExifInterface(file.absolutePath).getAttributeInt(
            ExifInterface.TAG_ORIENTATION,
            ExifInterface.ORIENTATION_NORMAL
        )
        orientation == ExifInterface.ORIENTATION_ROTATE_90 || orientation == ExifInterface.ORIENTATION_ROTATE_270
    } catch (e: IOException) {
        false
    }

@Suppress("ReturnCount")
private fun videoAspectRatio(file: File): Float? {
    val info = VideoCompressor.readVideoInfo(file) ?: return null
    if (info.height <= 0) return null
    val (width, height) = if (isSidewaysVideoRotation(file)) {
        info.height to info.width
    } else {
        info.width to info.height
    }
    if (height <= 0) return null
    return width.toFloat() / height.toFloat()
}

// MediaMetadataRetriever's width/height metadata reflect the raw, un-rotated encoded frame, so a
// video recorded in the sensor's landscape orientation (with a 90/270 rotation flag for portrait
// playback) reports swapped dimensions here compared to how it actually renders. getFrameAtTime()
// (used for the thumbnail in extractVideoFrame) already applies this rotation, so swap back here
// too, matching what's actually drawn.
@Suppress("TooGenericExceptionCaught")
private fun isSidewaysVideoRotation(file: File): Boolean {
    val retriever = MediaMetadataRetriever()
    return try {
        retriever.setDataSource(file.absolutePath)
        val rotation = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
        rotation == VIDEO_ROTATION_DEGREES_90 || rotation == VIDEO_ROTATION_DEGREES_270
    } catch (e: Exception) {
        false
    } finally {
        retriever.release()
    }
}

@Suppress("TooGenericExceptionCaught")
private fun extractVideoFrame(file: File): Bitmap? {
    val retriever = MediaMetadataRetriever()
    return try {
        retriever.setDataSource(file.absolutePath)
        retriever.getFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)?.let(::downscaleIfNeeded)
    } catch (e: Exception) {
        null
    } finally {
        retriever.release()
    }
}

private fun downscaleIfNeeded(bitmap: Bitmap): Bitmap {
    val largestDimension = maxOf(bitmap.width, bitmap.height)
    if (largestDimension <= VIDEO_FRAME_MAX_DIMENSION_PX) return bitmap
    val scale = VIDEO_FRAME_MAX_DIMENSION_PX.toFloat() / largestDimension
    val targetWidth = (bitmap.width * scale).roundToInt().coerceAtLeast(1)
    val targetHeight = (bitmap.height * scale).roundToInt().coerceAtLeast(1)
    return Bitmap.createScaledBitmap(bitmap, targetWidth, targetHeight, true)
}

@Suppress("ReturnCount")
private fun describeImageDetail(context: Context, file: File, compress: Boolean, fallback: String): DetailVariants {
    val original = ImageCompressor.readImageInfo(file) ?: return DetailVariants(fallback, fallback)
    val originalText = describeMedia(context, original.width, original.height, original.sizeBytes)
    val compressed = ImageCompressor.estimateCompression(file)
    val compressedText = compressed?.let { describeMedia(context, it.width, it.height, it.sizeBytes) } ?: originalText
    return if (compress) DetailVariants(compressedText, originalText) else DetailVariants(originalText, compressedText)
}

@Suppress("ReturnCount")
private fun describeVideoDetail(context: Context, file: File, compress: Boolean, fallback: String): DetailVariants {
    val original = VideoCompressor.readVideoInfo(file) ?: return DetailVariants(fallback, fallback)
    val originalText = describeMedia(context, original.width, original.height, original.sizeBytes)
    val compressed = VideoCompressor.estimateCompression(file)
    val compressedText = compressed?.let { describeMedia(context, it.width, it.height, it.sizeBytes) } ?: originalText
    return if (compress) DetailVariants(compressedText, originalText) else DetailVariants(originalText, compressedText)
}

private fun describeMedia(context: Context, width: Int, height: Int, sizeBytes: Long): String =
    "$width×$height, ${formatSize(context, sizeBytes)}"

private fun formatSize(context: Context, sizeBytes: Long): String = Formatter.formatShortFileSize(context, sizeBytes)
