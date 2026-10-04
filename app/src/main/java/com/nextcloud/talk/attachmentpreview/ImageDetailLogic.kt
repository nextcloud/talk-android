/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.attachmentpreview

import androidx.exifinterface.media.ExifInterface
import com.nextcloud.talk.utils.ImageCompressor
import com.nextcloud.talk.utils.ImageCompressor.ImageInfo
import java.io.File

/** True when the EXIF orientation turns the image by 90/270 degrees (also mirrored), swapping width and height. */
internal fun exifSwapsDimensions(orientation: Int): Boolean =
    orientation == ExifInterface.ORIENTATION_ROTATE_90 ||
        orientation == ExifInterface.ORIENTATION_ROTATE_270 ||
        orientation == ExifInterface.ORIENTATION_TRANSPOSE ||
        orientation == ExifInterface.ORIENTATION_TRANSVERSE

/** Pixel size of an image as it is displayed: the decode-bounds size with the EXIF orientation applied. */
internal fun ImageInfo.inDisplayOrientation(orientation: Int): ImageInfo =
    if (exifSwapsDimensions(orientation)) copy(width = height, height = width) else this

/**
 * Detail texts of an image. [original] is the file as it is, in display orientation; [compressed] is what
 * the compressor produces from it (null when it can't be estimated, then the original is shown for both).
 */
internal fun imageDetailVariants(
    original: ImageInfo,
    compressed: ImageInfo?,
    compress: Boolean,
    describe: (ImageInfo) -> String
): DetailVariants {
    val originalText = describe(original)
    val compressedText = compressed?.let(describe) ?: originalText
    return if (compress) DetailVariants(compressedText, originalText) else DetailVariants(originalText, compressedText)
}

/** What the upload worker makes of [file]; null for types it sends untouched (GIF) or when it can't be decoded. */
internal fun compressedImageInfo(
    mimeType: String?,
    file: File,
    estimate: (File) -> ImageInfo? = ImageCompressor::estimateCompression
): ImageInfo? = if (ImageCompressor.isCompressible(mimeType)) estimate(file) else null
