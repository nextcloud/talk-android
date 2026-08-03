/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.attachmentpreview

import android.graphics.Bitmap
import com.nextcloud.talk.utils.Mimetype

internal enum class MediaKind { IMAGE, VIDEO, OTHER }

internal data class FileDescription(
    val uri: String,
    val name: String,
    val kind: MediaKind,
    val mimeType: String?,
    val detail: String?,
    // What `detail` would read as under the other compress setting — lets the large preview's
    // detail chip reserve width for whichever variant is wider, so it never resizes on HQ toggle.
    val alternateDetail: String? = null,
    val aspectRatio: Float? = null,
    val videoThumbnail: Bitmap? = null
)

internal fun mediaKind(mimeType: String?): MediaKind =
    when {
        mimeType?.startsWith(Mimetype.IMAGE_PREFIX) == true -> MediaKind.IMAGE
        mimeType?.startsWith(Mimetype.VIDEO_PREFIX) == true -> MediaKind.VIDEO
        else -> MediaKind.OTHER
    }
