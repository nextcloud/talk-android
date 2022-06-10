/*
 * Nextcloud Talk application
 *
 * @author Andy Scherzinger
 * Copyright (C) 2022 Andy Scherzinger <info@andy-scherzinger.de>
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

    const val VIDEO_MP4 = "video/mp4"
    const val VIDEO_QUICKTIME = "video/quicktime"
    const val VIDEO_OGG = "video/ogg"

    const val TEXT_MARKDOWN = "text/markdown"
    const val TEXT_PLAIN = "text/plain"

    const val AUDIO_MPEG = "audio/mpeg"
    const val AUDIO_WAV = "audio/wav"
    const val AUDIO_OGG = "audio/ogg"
}
