/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2023 Marcel Hibbe <dev@mhibbe.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.utils

import android.content.Context
import android.os.VibrationEffect
import android.os.Vibrator

object VibrationUtils {
    const val SHORT_VIBRATE: Long = 100

    fun vibrateShort(context: Context) {
        val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        vibrator.vibrate(VibrationEffect.createOneShot(SHORT_VIBRATE, VibrationEffect.DEFAULT_AMPLITUDE))
    }
}
