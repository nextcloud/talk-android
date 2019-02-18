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

package com.nextcloud.talk.receivers;

import android.app.NotificationChannelGroup;
import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.util.Log;
import autodagger.AutoInjector;
import com.nextcloud.talk.application.NextcloudTalkApplication;
import com.nextcloud.talk.utils.NotificationUtils;
import com.nextcloud.talk.utils.database.user.UserUtils;
import com.nextcloud.talk.utils.preferences.AppPreferences;

import javax.inject.Inject;

@AutoInjector(NextcloudTalkApplication.class)
public class PackageReplacedReceiver extends BroadcastReceiver {
    private static final String TAG = "PackageReplacedReceiver";

    @Inject
    UserUtils userUtils;

    @Inject
    AppPreferences appPreferences;

    @Override
    public void onReceive(Context context, Intent intent) {
        NextcloudTalkApplication.getSharedApplication().getComponentApplication().inject(this);

        if (intent != null && intent.getAction() != null &&
                intent.getAction().equals("android.intent.action.MY_PACKAGE_REPLACED")) {
            try {
                PackageInfo packageInfo = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
                if (packageInfo.versionCode > 43 && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    NotificationManager notificationManager =
                            (NotificationManager) context.getSystemService(Context
                                    .NOTIFICATION_SERVICE);

                    if (notificationManager != null) {
                        if (!appPreferences.getIsNotificationChannelUpgradedToV2()) {
                            for (NotificationChannelGroup notificationChannelGroup : notificationManager
                                    .getNotificationChannelGroups()) {
                                notificationManager.deleteNotificationChannelGroup(notificationChannelGroup.getId());
                            }

                            notificationManager.deleteNotificationChannel(NotificationUtils.NOTIFICATION_CHANNEL_CALLS);
                            notificationManager.deleteNotificationChannel(NotificationUtils.NOTIFICATION_CHANNEL_MESSAGES);

                            appPreferences.setNotificationChannelIsUpgradedToV2(true);
                        }

                        if ((!appPreferences.getIsNotificationChannelUpgradedToV3()) && packageInfo.versionCode > 51) {
                            notificationManager.deleteNotificationChannel(NotificationUtils.NOTIFICATION_CHANNEL_MESSAGES_V2);
                            notificationManager.deleteNotificationChannel(NotificationUtils.NOTIFICATION_CHANNEL_CALLS_V2);
                            appPreferences.setNotificationChannelIsUpgradedToV3(true);
                        }
                    }

                }
            } catch (PackageManager.NameNotFoundException e) {
                Log.e(TAG, "Failed to fetch package info");
            }
        }
    }
}
