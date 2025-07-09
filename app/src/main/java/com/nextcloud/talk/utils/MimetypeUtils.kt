/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2022 Andy Scherzinger <info@andy-scherzinger.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.utils

object MimetypeUtils {
    fun isGif(mimetype: String): Boolean = Mimetype.IMAGE_GIF == mimetype

    fun isMarkdown(mimetype: String): Boolean = Mimetype.TEXT_MARKDOWN == mimetype

    fun isAudioOnly(mimetype: String): Boolean = mimetype.startsWith(Mimetype.AUDIO_PREFIX)
}
