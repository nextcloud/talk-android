/*
 * Nextcloud Talk application
 *
 * @author Andy Scherzinger
 * @author Marcel Hibbe
 * Copyright (C) 2022 Andy Scherzinger <info@andy-scherzinger.de>
 * Copyright (C) 2021 Marcel Hibbe <dev@mhibbe.de>
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

import android.net.Uri

class UriUtils {
    companion object {
        fun hasHttpProtocolPrefixed(uri: String): Boolean {
            return uri.startsWith("http://") || uri.startsWith("https://")
        }

        fun extractInstanceInternalFileFileId(url: String): String {
            // https://cloud.nextcloud.com/apps/files/?dir=/Engineering&fileid=41
            return Uri.parse(url).getQueryParameter("fileid").toString()
        }

        fun isInstanceInternalFileShareUrl(baseUrl: String, url: String): Boolean {
            // https://cloud.nextcloud.com/f/41
            return url.startsWith("$baseUrl/f/") || url.startsWith("$baseUrl/index.php/f/") &&
                Regex(".*/f/d*").matches(url)
        }

        fun extractInstanceInternalFileShareFileId(url: String): String {
            // https://cloud.nextcloud.com/f/41
            return Uri.parse(url).lastPathSegment ?: ""
        }

        fun isInstanceInternalFileUrl(baseUrl: String, url: String): Boolean {
            // https://cloud.nextcloud.com/apps/files/?dir=/Engineering&fileid=41
            return (url.startsWith("$baseUrl/apps/files/") || url.startsWith("$baseUrl/index.php/apps/files/")) &&
                Uri.parse(url).queryParameterNames.contains("fileid") && Regex(""".*fileid=\d*""").matches(url)
        }

        fun isInstanceInternalFileUrlNew(baseUrl: String, url: String): Boolean {
            // https://cloud.nextcloud.com/apps/files/files/41?dir=/
            return url.startsWith("$baseUrl/apps/files/files/") || url.startsWith("$baseUrl/index.php/apps/files/files/")
        }

        fun extractInstanceInternalFileFileIdNew(url: String): String {
            // https://cloud.nextcloud.com/apps/files/files/41?dir=/
            return Uri.parse(url).lastPathSegment ?: ""
        }
    }
}
