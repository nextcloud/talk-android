/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.attachmentsheet

import android.Manifest
import android.content.pm.PackageManager
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

/**
 * Permissions of the "file from device" request: the set of the attachment sheet, plus audio from Android 13 on,
 * which the request has always included.
 */
fun shareFilePermissionsToRequest(sdkInt: Int): Array<String> =
    if (sdkInt >= Build.VERSION_CODES.TIRAMISU) {
        mediaPermissionsToRequest(sdkInt) + Manifest.permission.READ_MEDIA_AUDIO
    } else {
        mediaPermissionsToRequest(sdkInt)
    }

/**
 * Whether the result of the "file from device" request allows to pick a file. No single result is the answer:
 * Android 14 may grant only the selected photos, and before Android 10 the granted read permission differs from the
 * one [effectivelyGranted] checks. Any granted permission or an effective access is enough.
 */
fun isShareFileRequestGranted(grantResults: IntArray, effectivelyGranted: Boolean): Boolean =
    grantResults.any { it == PackageManager.PERMISSION_GRANTED } || effectivelyGranted

// Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED exists only from API 34; the string is stable.
internal const val READ_MEDIA_VISUAL_USER_SELECTED = "android.permission.READ_MEDIA_VISUAL_USER_SELECTED"
