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

package com.nextcloud.talk.receivers

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import autodagger.AutoInjector
import com.nextcloud.talk.application.NextcloudTalkApplication
import com.nextcloud.talk.utils.NotificationUtils
import com.nextcloud.talk.utils.database.user.UserUtils
import com.nextcloud.talk.utils.preferences.AppPreferences
import javax.inject.Inject

@AutoInjector(NextcloudTalkApplication::class)
class PackageReplacedReceiver : BroadcastReceiver() {

    @Inject
    lateinit var userUtils: UserUtils

    @Inject
    lateinit var appPreferences: AppPreferences

    override fun onReceive(context: Context, intent: Intent?) {
        NextcloudTalkApplication.sharedApplication!!.componentApplication.inject(this)

        if (intent != null && intent.action != null &&
                intent.action == "android.intent.action.MY_PACKAGE_REPLACED") {
            try {
                val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
                if (packageInfo.versionCode > 43 && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    val notificationManager = context.getSystemService(Context
                            .NOTIFICATION_SERVICE) as NotificationManager

                    if (notificationManager != null) {
                        if (!appPreferences.isNotificationChannelUpgradedToV2) {
                            for (notificationChannelGroup in notificationManager
                                    .notificationChannelGroups) {
                                notificationManager.deleteNotificationChannelGroup(notificationChannelGroup.id)
                            }

                            notificationManager.deleteNotificationChannel(NotificationUtils.NOTIFICATION_CHANNEL_CALLS)
                            notificationManager.deleteNotificationChannel(NotificationUtils.NOTIFICATION_CHANNEL_MESSAGES)

                            appPreferences.setNotificationChannelIsUpgradedToV2(true)
                        }

                        if (!appPreferences.isNotificationChannelUpgradedToV3 && packageInfo.versionCode > 51) {
                            notificationManager.deleteNotificationChannel(NotificationUtils.NOTIFICATION_CHANNEL_MESSAGES_V2)
                            notificationManager.deleteNotificationChannel(NotificationUtils.NOTIFICATION_CHANNEL_CALLS_V2)
                            appPreferences.setNotificationChannelIsUpgradedToV3(true)
                        }
                    }

                }
            } catch (e: PackageManager.NameNotFoundException) {
                Log.e(TAG, "Failed to fetch package info")
            }

        }
    }

    companion object {
        private val TAG = "PackageReplacedReceiver"
    }
}
