/*
 * Nextcloud Talk application
 *
 * @author Mario Danic
 * Copyright (C) 2017-2018 Mario Danic <mario@lovelyhq.com>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.nextcloud.talk.utils

import android.annotation.SuppressLint
import android.app.NotificationManager
import android.content.Context
import android.media.AudioManager
import android.os.Build
import androidx.annotation.VisibleForTesting
import com.nextcloud.talk.application.NextcloudTalkApplication

object DoNotDisturbUtils {

    private var buildVersion = Build.VERSION.SDK_INT

    @VisibleForTesting
    fun setTestingBuildVersion(version: Int) {
        buildVersion = version
    }

    @SuppressLint("NewApi")
    @JvmOverloads
    fun shouldPlaySound(context: Context? = NextcloudTalkApplication.sharedApplication?.applicationContext): Boolean {

        val notificationManager = context?.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

        var shouldPlaySound = true
        if (buildVersion >= Build.VERSION_CODES.M) {
            if (notificationManager.currentInterruptionFilter != NotificationManager.INTERRUPTION_FILTER_ALL) {
                shouldPlaySound = false
            }
        }

        if (shouldPlaySound) {
            if (audioManager.ringerMode != AudioManager.RINGER_MODE_NORMAL) {
                shouldPlaySound = false
            }
        }

        return shouldPlaySound
    }
}
