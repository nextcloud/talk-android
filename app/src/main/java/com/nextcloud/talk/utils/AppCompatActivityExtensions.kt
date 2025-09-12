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
    // It may make sense to change navigationBarStyle to "SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT)"
    // For now, it is set to "light" to have a fully transparent navigation bar to align with the XML screens.
    // It may be wanted to have a semi transparent navigation bar in the future. Then set it to "auto" and try to
    // migrate the XML screens to Compose (having semi transparent navigation bar for XML did not work out. In
    // general, supporting both XML and Compose system bar handling is a pain and we will have it easier without XML)
    // So in short: migrate all screens to Compose. Then it's easier to decide if navigation bar should be semi
    // transparent or not for all screens.
    navigationBarStyle: SystemBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT)
) {
    val isApiLevel35OrHigher = (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM)
    if (!isApiLevel35OrHigher) {
        return
    }
    enableEdgeToEdge(statusBarStyle, navigationBarStyle)
}
