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

import com.nextcloud.talk.application.NextcloudTalkApplication;
import com.nextcloud.talk.data.user.model.User;
import com.nextcloud.talk.models.ExternalSignalingServer;
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

    private static final String TAG = "WebsocketConnectionsWorker";

    @Inject
    UserManager userUtils;

    public WebsocketConnectionsWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
    }

    @SuppressLint("LongLogTag")
    @NonNull
    @Override
    public Result doWork() {
        NextcloudTalkApplication.Companion.getSharedApplication().getComponentApplication().inject(this);

        List<User> userEntityList = userUtils.getUsers().blockingGet();
        User userEntity;
        ExternalSignalingServer externalSignalingServer;
        WebSocketConnectionHelper webSocketConnectionHelper = new WebSocketConnectionHelper();
        for (int i = 0; i < userEntityList.size(); i++) {
            userEntity = userEntityList.get(i);
            if (userEntity.getExternalSignalingServer() != null) {
                if (!TextUtils.isEmpty(userEntity.getExternalSignalingServer().getExternalSignalingServer()) &&
                    !TextUtils.isEmpty(userEntity.getExternalSignalingServer().getExternalSignalingTicket())) {
                    webSocketConnectionHelper.getExternalSignalingInstanceForServer(
                        userEntity.getExternalSignalingServer().getExternalSignalingServer(),
                        userEntity, userEntity.getExternalSignalingServer().getExternalSignalingTicket(),
                        false);
                }
            }
        }

        return Result.success();
    }
}
