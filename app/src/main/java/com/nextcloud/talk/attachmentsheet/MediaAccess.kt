/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.attachmentsheet

import android.Manifest
import android.os.Build

/**
 * How much of the device media library the app may read.
 */
enum class MediaAccess { FULL, PARTIAL, NONE }

/**
 * Derives [MediaAccess] from the granted runtime permissions. Android 14 and newer can grant only the photos the
 * user picked (READ_MEDIA_VISUAL_USER_SELECTED); Android 13 uses per-type permissions; older versions use the
 * single storage permission.
 */
fun resolveMediaAccess(sdkInt: Int, granted: Set<String>): MediaAccess =
    when {
        sdkInt >= Build.VERSION_CODES.TIRAMISU ->
            when {
                Manifest.permission.READ_MEDIA_IMAGES in granted ||
                    Manifest.permission.READ_MEDIA_VIDEO in granted -> MediaAccess.FULL

                sdkInt >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE &&
                    READ_MEDIA_VISUAL_USER_SELECTED in granted -> MediaAccess.PARTIAL

                else -> MediaAccess.NONE
            }

        Manifest.permission.READ_EXTERNAL_STORAGE in granted -> MediaAccess.FULL

        else -> MediaAccess.NONE
    }

/**
 * Whether the app may read files of the user without own picker: any media permission, including the partial
 * grant of Android 14, or audio. Used by flows that already hold a URI (recording, camera, share-to-Talk).
 */
fun hasMediaFilesAccess(sdkInt: Int, granted: Set<String>): Boolean =
    sdkInt >= Build.VERSION_CODES.TIRAMISU &&
        (resolveMediaAccess(sdkInt, granted) != MediaAccess.NONE || Manifest.permission.READ_MEDIA_AUDIO in granted)

/**
 * Permissions to request in one dialog so that Android 14 offers the "select photos" choice next to "allow all".
 */
fun mediaPermissionsToRequest(sdkInt: Int): Array<String> =
    when {
        sdkInt >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE ->
            arrayOf(
                Manifest.permission.READ_MEDIA_IMAGES,
                Manifest.permission.READ_MEDIA_VIDEO,
                READ_MEDIA_VISUAL_USER_SELECTED
            )

        sdkInt >= Build.VERSION_CODES.TIRAMISU ->
            arrayOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO)

        else -> arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
    }

// Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED exists only from API 34; the string is stable.
internal const val READ_MEDIA_VISUAL_USER_SELECTED = "android.permission.READ_MEDIA_VISUAL_USER_SELECTED"
