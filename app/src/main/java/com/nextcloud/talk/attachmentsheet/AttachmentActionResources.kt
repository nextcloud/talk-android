/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.attachmentsheet

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.nextcloud.talk.R

@DrawableRes
internal fun AttachmentAction.iconRes(): Int =
    when (this) {
        AttachmentAction.PICTURE_FROM_CAM -> R.drawable.ic_baseline_photo_camera_24
        AttachmentAction.VIDEO_FROM_CAM -> R.drawable.ic_baseline_videocam_24
        AttachmentAction.GALLERY -> R.drawable.baseline_photo_library_24
        AttachmentAction.FILE_FROM_LOCAL -> R.drawable.upload
        AttachmentAction.FILE_FROM_CLOUD -> R.drawable.ic_share_variant
        AttachmentAction.CREATE_THREAD -> R.drawable.outline_forum_24
        AttachmentAction.CREATE_POLL -> R.drawable.ic_baseline_bar_chart_24
        AttachmentAction.SHARE_LOCATION -> R.drawable.ic_baseline_location_on_24
        AttachmentAction.SHARE_CONTACT -> R.drawable.ic_baseline_person_24
    }

/**
 * Label for the entry, or null when it needs a runtime argument (the cloud entry carries the server name).
 */
@StringRes
internal fun AttachmentAction.labelRes(): Int? =
    when (this) {
        AttachmentAction.PICTURE_FROM_CAM -> R.string.nc_upload_picture_from_cam
        AttachmentAction.VIDEO_FROM_CAM -> R.string.nc_upload_video_from_cam
        AttachmentAction.GALLERY -> R.string.nc_gallery
        AttachmentAction.FILE_FROM_LOCAL -> R.string.nc_upload_from_device
        AttachmentAction.FILE_FROM_CLOUD -> null
        AttachmentAction.CREATE_THREAD -> R.string.start_thread
        AttachmentAction.CREATE_POLL -> R.string.nc_create_poll
        AttachmentAction.SHARE_LOCATION -> R.string.nc_share_location
        AttachmentAction.SHARE_CONTACT -> R.string.nc_share_contact
    }
