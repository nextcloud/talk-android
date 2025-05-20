/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2022 Andy Scherzinger <info@andy-scherzinger.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.utils

object Mimetype {
    const val IMAGE_PREFIX = "image/"
    const val VIDEO_PREFIX = "video/"
    const val TEXT_PREFIX = "text/"
    const val AUDIO_PREFIX = "audio"

    const val IMAGE_PREFIX_GENERIC = "image/*"
    const val VIDEO_PREFIX_GENERIC = "video/*"
    const val TEXT_PREFIX_GENERIC = "text/*"

    const val FOLDER = "inode/directory"

    const val IMAGE_PNG = "image/png"
    const val IMAGE_JPEG = "image/jpeg"
    const val IMAGE_JPG = "image/jpg"
    const val IMAGE_GIF = "image/gif"
    const val IMAGE_HEIC = "image/heic"

    const val VIDEO_MP4 = "video/mp4"
    const val VIDEO_QUICKTIME = "video/quicktime"
    const val VIDEO_OGG = "video/ogg"
    const val VIDEO_WEBM = "video/webm"

    const val TEXT_MARKDOWN = "text/markdown"
    const val TEXT_PLAIN = "text/plain"

    const val AUDIO_MPEG = "audio/mpeg"
    const val AUDIO_WAV = "audio/wav"
    const val AUDIO_OGG = "audio/ogg"
}
