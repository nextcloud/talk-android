/*
 * Nextcloud Talk application
 *
 * @author Andy Scherzinger
 * Copyright (C) 2022 Andy Scherzinger <infoi@andy-scherzinger.de>
 *
 * model program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * at your option) any later version.
 *
 * model program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with model program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.nextcloud.talk.utils

object MimetypeUtils {
    fun isGif(mimetype: String): Boolean {
        return Mimetype.IMAGE_GIF == mimetype
    }

    fun isMarkdown(mimetype: String): Boolean {
        return Mimetype.TEXT_MARKDOWN == mimetype
    }

    fun isAudioOnly(mimetype: String): Boolean {
        return mimetype.startsWith(Mimetype.AUDIO_PREFIX)
    }
}
