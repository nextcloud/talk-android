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

package com.nextcloud.talk.services.firebase;

import android.annotation.SuppressLint;

import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;
import com.nextcloud.talk.jobs.NotificationWorker;
import com.nextcloud.talk.utils.bundle.BundleKeys;

import androidx.work.Data;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;

public class MagicFirebaseMessagingService extends FirebaseMessagingService {

    @SuppressLint("LongLogTag")
    @Override
    public void onMessageReceived(RemoteMessage remoteMessage) {
        if (remoteMessage == null) {
            return;
        }

        if (remoteMessage.getData() != null) {
            Data messageData = new Data.Builder()
                    .putString(BundleKeys.KEY_NOTIFICATION_SUBJECT, remoteMessage.getData().get("subject"))
                    .putString(BundleKeys.KEY_NOTIFICATION_SIGNATURE, remoteMessage.getData().get("signature"))
                    .build();

            OneTimeWorkRequest pushNotificationWork = new OneTimeWorkRequest.Builder(NotificationWorker.class)
                    .setInputData(messageData)
                    .build();
            WorkManager.getInstance().enqueue(pushNotificationWork);
        }
    }
}
