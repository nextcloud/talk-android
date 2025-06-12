/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2025 Marcel Hibbe <dev@mhibbe.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.utils

import android.graphics.Color
import android.os.Build
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity

/**
 * This method is similar to "adjustUIForAPILevel35" in
 * AppCompatActivityExtensions.kt in https://github.com/nextcloud/android-common/
 * Only window.addSystemBarPaddings() had to be removed. This could be unified again at some point.
 */
@JvmOverloads
fun AppCompatActivity.adjustUIForAPILevel35(
    statusBarStyle: SystemBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT),
    navigationBarStyle: SystemBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT)
) {
    val isApiLevel35OrHigher = (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM)
    if (!isApiLevel35OrHigher) {
        return
    }
    enableEdgeToEdge(statusBarStyle, navigationBarStyle)
}
