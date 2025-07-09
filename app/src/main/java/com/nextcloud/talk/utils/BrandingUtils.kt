/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2024 Marcel Hibbe <dev@mhibbe.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.utils

import android.content.Context

object BrandingUtils {
    private const val ORIGINAL_NEXTCLOUD_TALK_APPLICATION_ID = "com.nextcloud.talk2"

    fun isOriginalNextcloudClient(context: Context): Boolean =
        context.packageName.equals(ORIGINAL_NEXTCLOUD_TALK_APPLICATION_ID)
}
