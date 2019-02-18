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

package com.nextcloud.talk.utils;

import android.app.NotificationManager;
import android.content.Context;
import android.media.AudioManager;
import android.os.Build;
import android.os.Vibrator;
import com.nextcloud.talk.application.NextcloudTalkApplication;

public class DoNotDisturbUtils {
    private static final String TAG = "DoNotDisturbUtils";

    public static boolean shouldPlaySound() {
        Context context = NextcloudTalkApplication.getSharedApplication().getApplicationContext();

        NotificationManager notificationManager =
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        AudioManager audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);

        boolean shouldPlaySound = true;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && notificationManager != null) {
            if (notificationManager.getCurrentInterruptionFilter() != NotificationManager.INTERRUPTION_FILTER_ALL) {
                shouldPlaySound = false;
            }
        }

        if (audioManager != null && shouldPlaySound) {
            if (audioManager.getRingerMode() != AudioManager.RINGER_MODE_NORMAL) {
                shouldPlaySound = false;
            }
        }

        return shouldPlaySound;
    }

    public static boolean hasVibrator() {
        Context context = NextcloudTalkApplication.getSharedApplication().getApplicationContext();
        Vibrator vibrator = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
        return (vibrator != null && vibrator.hasVibrator());
    }

    public static boolean shouldVibrate(boolean vibrate) {

        if (hasVibrator()) {
            Context context = NextcloudTalkApplication.getSharedApplication().getApplicationContext();
            AudioManager audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);

            if (audioManager != null) {
                if (vibrate) {
                    return (audioManager.getRingerMode() != AudioManager.RINGER_MODE_SILENT);
                } else {
                    return (audioManager.getRingerMode() == AudioManager.RINGER_MODE_VIBRATE);
                }
            }
        }

        return false;
    }
}
