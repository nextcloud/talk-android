/*
 * Nextcloud Talk application
 *
 * @author Mario Danic
 * Copyright (C) 2017 Mario Danic <mario@lovelyhq.com>
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

import android.annotation.TargetApi;
import android.app.NotificationChannel;
import android.app.NotificationChannelGroup;
import android.app.NotificationManager;
import android.content.Context;
import android.graphics.Color;
import android.os.Build;
import android.service.notification.StatusBarNotification;
import android.text.TextUtils;

import com.nextcloud.talk.R;
import com.nextcloud.talk.models.database.UserEntity;

import java.util.zip.CRC32;

public class NotificationUtils {
    public static final String NOTIFICATION_CHANNEL_CALLS = "NOTIFICATION_CHANNEL_CALLS";
    public static final String NOTIFICATION_CHANNEL_MESSAGES = "NOTIFICATION_CHANNEL_MESSAGES";
    public static final String NOTIFICATION_CHANNEL_CALLS_V2 = "NOTIFICATION_CHANNEL_CALLS_V2";
    public static final String NOTIFICATION_CHANNEL_MESSAGES_V2 = "NOTIFICATION_CHANNEL_MESSAGES_V2";
    public static final String NOTIFICATION_CHANNEL_MESSAGES_V3 = "NOTIFICATION_CHANNEL_MESSAGES_V3";
    public static final String NOTIFICATION_CHANNEL_CALLS_V3 = "NOTIFICATION_CHANNEL_CALLS_V3";

    @TargetApi(Build.VERSION_CODES.O)
    public static void createNotificationChannel(NotificationManager notificationManager,
                                                 String channelId, String channelName,
                                                 String channelDescription, boolean enableLights,
                                                 int importance) {

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O
                && notificationManager.getNotificationChannel(channelId) == null) {

            NotificationChannel channel = new NotificationChannel(channelId, channelName,
                    importance);

            channel.setDescription(channelDescription);
            channel.enableLights(enableLights);
            channel.setLightColor(Color.RED);
            channel.setSound(null, null);

            notificationManager.createNotificationChannel(channel);
        }
    }

    @TargetApi(Build.VERSION_CODES.O)
    public static void createNotificationChannelGroup(NotificationManager notificationManager,
                                                      String groupId, CharSequence groupName) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            NotificationChannelGroup notificationChannelGroup = new NotificationChannelGroup(groupId, groupName);
            if (!notificationManager.getNotificationChannelGroups().contains(notificationChannelGroup)) {
                notificationManager.createNotificationChannelGroup(notificationChannelGroup);
            }
        }
    }

    public static void cancelExistingNotifications(Context context, UserEntity conversationUser) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M && conversationUser.getId() != -1 &&
                context != null) {

            NotificationManager notificationManager =
                    (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);

            CRC32 crc32 = new CRC32();
            String groupName = String.format(context.getResources().getString(R.string
                    .nc_notification_channel), conversationUser.getUserId(), conversationUser.getBaseUrl());
            crc32.update(groupName.getBytes());
            String crc32GroupString = Long.toString(crc32.getValue());

            if (notificationManager != null) {
                StatusBarNotification statusBarNotifications[] = notificationManager.getActiveNotifications();
                for (StatusBarNotification statusBarNotification : statusBarNotifications) {

                    if (statusBarNotification.getNotification() != null &&
                            !TextUtils.isEmpty(statusBarNotification.getNotification().getGroup())) {
                        if (statusBarNotification.getNotification().getGroup().equals(crc32GroupString)) {
                            notificationManager.cancel(statusBarNotification.getId());
                        }
                    }
                }
            }
        }
    }

}
