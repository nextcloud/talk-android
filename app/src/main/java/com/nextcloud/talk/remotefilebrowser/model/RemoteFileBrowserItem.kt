/*
 * Nextcloud Talk application
 *
 * @author Andy Scherzinger
 * @author Mario Danic
 * Copyright (C) 2022 Andy Scherzinger <info@andy-scherzinger.de>
 * Copyright (C) 2017-2018 Mario Danic <mario@lovelyhq.com>
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

package com.nextcloud.talk.remotefilebrowser.model

import android.os.Parcelable
import kotlinx.android.parcel.Parcelize

@Parcelize
data class RemoteFileBrowserItem(
    var path: String? = null,
    var displayName: String? = null,
    var mimeType: String? = null,
    var modifiedTimestamp: Long = 0,
    var size: Long = 0,
    var isFile: Boolean = false,

    // Used for remote files
    var remoteId: String? = null,
    var hasPreview: Boolean = false,
    var isFavorite: Boolean = false,
    var isEncrypted: Boolean = false,
    var permissions: String? = null,
    var isAllowedToReShare: Boolean = false
) : Parcelable
