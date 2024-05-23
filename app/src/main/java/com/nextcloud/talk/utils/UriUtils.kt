/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2022 Andy Scherzinger <info@andy-scherzinger.de>
 * SPDX-FileCopyrightText: 2021 Marcel Hibbe <dev@mhibbe.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
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
            // https://cloud.nextcloud.com/index.php/f/41
            return (url.startsWith("$baseUrl/f/") || url.startsWith("$baseUrl/index.php/f/")) &&
                Regex(".*/f/\\d*").matches(url)
        }

        fun isInstanceInternalTalkUrl(baseUrl: String, url: String): Boolean {
            // https://cloud.nextcloud.com/call/123456789
            return (url.startsWith("$baseUrl/call/") || url.startsWith("$baseUrl/index.php/call/")) &&
                Regex(".*/call/\\d*").matches(url)
        }

        fun extractInstanceInternalFileShareFileId(url: String): String {
            // https://cloud.nextcloud.com/f/41
            return Uri.parse(url).lastPathSegment ?: ""
        }

        fun extractRoomTokenFromTalkUrl(url: String): String {
            // https://cloud.nextcloud.com/call/123456789
            return Uri.parse(url).lastPathSegment ?: ""
        }

        fun isInstanceInternalFileUrl(baseUrl: String, url: String): Boolean {
            // https://cloud.nextcloud.com/apps/files/?dir=/Engineering&fileid=41
            return (
                url.startsWith("$baseUrl/apps/files/") ||
                    url.startsWith("$baseUrl/index.php/apps/files/")
                ) &&
                Uri.parse(url).queryParameterNames.contains("fileid") &&
                Regex(""".*fileid=\d*""").matches(url)
        }

        fun isInstanceInternalFileUrlNew(baseUrl: String, url: String): Boolean {
            // https://cloud.nextcloud.com/apps/files/files/41?dir=/
            return url.startsWith("$baseUrl/apps/files/files/") ||
                url.startsWith("$baseUrl/index.php/apps/files/files/")
        }

        fun extractInstanceInternalFileFileIdNew(url: String): String {
            // https://cloud.nextcloud.com/apps/files/files/41?dir=/
            return Uri.parse(url).lastPathSegment ?: ""
        }
    }
}
