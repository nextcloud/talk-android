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

import android.app.NotificationManager
import android.content.Context
import android.media.AudioManager
import android.os.Build
import android.os.Vibrator
import com.nextcloud.talk.application.NextcloudTalkApplication

object DoNotDisturbUtils {
  fun isDnDActive(): Boolean {
    val context = NextcloudTalkApplication.sharedApplication?.applicationContext

    val notificationManager =
      context?.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
      if (notificationManager.currentInterruptionFilter == NotificationManager
              .INTERRUPTION_FILTER_NONE || notificationManager
              .currentInterruptionFilter == NotificationManager
              .INTERRUPTION_FILTER_ALARMS || notificationManager
              .currentInterruptionFilter == NotificationManager.INTERRUPTION_FILTER_PRIORITY
      ) {
        return true
      }
    }

    return false

  }

  fun isInDoNotDisturbWithPriority(): Boolean {
    val context = NextcloudTalkApplication.sharedApplication?.applicationContext

    val notificationManager =
      context?.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
      if (notificationManager.currentInterruptionFilter == NotificationManager.INTERRUPTION_FILTER_PRIORITY) {
        return true
      }
    }

    return false
  }

  fun shouldPlaySound(): Boolean {
    val context = NextcloudTalkApplication.sharedApplication?.applicationContext

    val notificationManager =
      context?.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    var shouldPlaySound = true
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
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

  fun hasVibrator(): Boolean {
    val context = NextcloudTalkApplication.sharedApplication?.applicationContext
    val vibrator = context?.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
    return vibrator.hasVibrator()
  }

  fun shouldVibrate(vibrate: Boolean): Boolean {

    if (hasVibrator()) {
      val context = NextcloudTalkApplication.sharedApplication?.applicationContext
      val audioManager = context?.getSystemService(Context.AUDIO_SERVICE) as AudioManager

      return if (vibrate) {
        audioManager.ringerMode != AudioManager.RINGER_MODE_SILENT
      } else {
        audioManager.ringerMode == AudioManager.RINGER_MODE_VIBRATE
      }
    }

    return false
  }
}
