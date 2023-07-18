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

package com.nextcloud.talk.jobs;

import android.annotation.SuppressLint;
import android.content.Context;
import android.text.TextUtils;
import android.util.Log;

import com.nextcloud.talk.application.NextcloudTalkApplication;
import com.nextcloud.talk.data.user.model.User;
import com.nextcloud.talk.users.UserManager;
import com.nextcloud.talk.webrtc.WebSocketConnectionHelper;

import java.util.List;

import javax.inject.Inject;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;
import autodagger.AutoInjector;

@AutoInjector(NextcloudTalkApplication.class)
public class WebsocketConnectionsWorker extends Worker {

    public static final String TAG = "WebsocketConnectionsWorker";

    @Inject
    UserManager userManager;

    public WebsocketConnectionsWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
    }

    @SuppressLint("LongLogTag")
    @NonNull
    @Override
    public Result doWork() {
        Log.d(TAG, "WebsocketConnectionsWorker started ");

        NextcloudTalkApplication.Companion.getSharedApplication().getComponentApplication().inject(this);

        List<User> users = userManager.getUsers().blockingGet();
        for (User user : users) {
            if (user.getExternalSignalingServer() != null &&
                user.getExternalSignalingServer().getExternalSignalingServer() != null &&
                !TextUtils.isEmpty(user.getExternalSignalingServer().getExternalSignalingServer()) &&
                !TextUtils.isEmpty(user.getExternalSignalingServer().getExternalSignalingTicket())) {

                Log.d(TAG, "trying to getExternalSignalingInstanceForServer for user " + user.getDisplayName());

                WebSocketConnectionHelper.getExternalSignalingInstanceForServer(
                    user.getExternalSignalingServer().getExternalSignalingServer(),
                    user,
                    user.getExternalSignalingServer().getExternalSignalingTicket(),
                    false);
            } else {
                Log.d(TAG, "skipped to getExternalSignalingInstanceForServer for user " + user.getDisplayName());
            }
        }

        return Result.success();
    }
}
