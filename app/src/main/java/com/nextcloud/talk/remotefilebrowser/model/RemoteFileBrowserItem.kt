/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2022 Andy Scherzinger <info@andy-scherzinger.de>
 * SPDX-FileCopyrightText: 2017-2018 Mario Danic <mario@lovelyhq.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.remotefilebrowser.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

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
