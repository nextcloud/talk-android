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
import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;
import autodagger.AutoInjector;
import com.bluelinelabs.logansquare.LoganSquare;
import com.nextcloud.talk.application.NextcloudTalkApplication;
import com.nextcloud.talk.models.ExternalSignalingServer;
import com.nextcloud.talk.models.database.UserEntity;
import com.nextcloud.talk.utils.database.user.UserUtils;
import com.nextcloud.talk.webrtc.WebSocketConnectionHelper;

import javax.inject.Inject;
import java.io.IOException;
import java.util.List;

@AutoInjector(NextcloudTalkApplication.class)
public class WebsocketConnectionsWorker extends Worker {

    private static final String TAG = "WebsocketConnectionsWorker";

    @Inject
    UserUtils userUtils;

    public WebsocketConnectionsWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
    }

    @SuppressLint("LongLogTag")
    @NonNull
    @Override
    public Result doWork() {
        NextcloudTalkApplication.getSharedApplication().getComponentApplication().inject(this);

        List<UserEntity> userEntityList = userUtils.getUsers();
        UserEntity userEntity;
        ExternalSignalingServer externalSignalingServer;
        WebSocketConnectionHelper webSocketConnectionHelper = new WebSocketConnectionHelper();
        for (int i = 0; i < userEntityList.size(); i++) {
            userEntity = userEntityList.get(i);
            if (!TextUtils.isEmpty(userEntity.getExternalSignalingServer())) {
                try {
                    externalSignalingServer = LoganSquare.parse(userEntity.getExternalSignalingServer(), ExternalSignalingServer.class);
                    if (!TextUtils.isEmpty(externalSignalingServer.getExternalSignalingServer()) &&
                            !TextUtils.isEmpty(externalSignalingServer.getExternalSignalingTicket())) {
                        webSocketConnectionHelper.getExternalSignalingInstanceForServer(
                                externalSignalingServer.getExternalSignalingServer(),
                                userEntity, externalSignalingServer.getExternalSignalingTicket(),
                                false);
                    }
                } catch (IOException e) {
                    Log.e(TAG, "Failed to parse external signaling server");
                }
            }
        }

        return Result.success();
    }
}
