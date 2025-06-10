/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2025 Marcel Hibbe <dev@mhibbe.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.utils

import android.os.Build
import android.view.WindowInsets
import androidx.annotation.ColorInt
import androidx.appcompat.app.AppCompatActivity

// replace this with the common lib whenever https://github.com/nextcloud/android-common/pull/668 is merged
// and then delete this file
fun AppCompatActivity.setStatusBarColor(@ColorInt color: Int) {
    window.decorView.setOnApplyWindowInsetsListener { view, insets ->
        view.setBackgroundColor(color)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            val statusBarHeight = insets.getInsets(WindowInsets.Type.statusBars()).top
            view.setPadding(0, statusBarHeight, 0, 0)
        }

        insets
    }
}
