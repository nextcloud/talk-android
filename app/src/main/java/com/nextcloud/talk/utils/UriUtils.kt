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

import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log

class UriUtils {
    companion object {
        fun getFileName(uri: Uri, context: Context?): String {
            var filename: String? = null
            if (uri.scheme == "content" && context != null) {
                val cursor: Cursor? = context.contentResolver.query(uri, null, null, null, null)
                try {
                    if (cursor != null && cursor.moveToFirst()) {
                        filename = cursor.getString(cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME))
                    }
                } finally {
                    cursor?.close()
                }
            }
            if (filename == null) {
                Log.d("UriUtils", "failed to get DISPLAY_NAME from uri. using fallback.")
                filename = uri.path
                val lastIndexOfSlash = filename!!.lastIndexOf('/')
                if (lastIndexOfSlash != -1) {
                    filename = filename.substring(lastIndexOfSlash + 1)
                }
            }
            return filename
        }

        fun hasHttpProtocollPrefixed(uri: String): Boolean {
            return uri.startsWith("http://") || uri.startsWith("https://")
        }
    }
}
