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

package com.moyn.talk.jobs;

import android.content.Context;
import android.util.Log;

import com.bluelinelabs.logansquare.LoganSquare;
import com.moyn.talk.api.NcApi;
import com.moyn.talk.application.NextcloudTalkApplication;
import com.moyn.talk.events.EventStatus;
import com.moyn.talk.models.ExternalSignalingServer;
import com.moyn.talk.models.database.UserEntity;
import com.moyn.talk.models.json.signaling.settings.SignalingSettingsOverall;
import com.moyn.talk.utils.ApiUtils;
import com.moyn.talk.utils.bundle.BundleKeys;
import com.moyn.talk.utils.database.user.UserUtils;

import org.greenrobot.eventbus.EventBus;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import javax.inject.Inject;

import androidx.annotation.NonNull;
import androidx.work.Data;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;
import autodagger.AutoInjector;
import io.reactivex.Observer;
import io.reactivex.disposables.Disposable;

@AutoInjector(NextcloudTalkApplication.class)
public class SignalingSettingsWorker extends Worker {
    private static final String TAG = "SignalingSettingsJob";

    @Inject
    UserUtils userUtils;

    @Inject
    NcApi ncApi;

    @Inject
    EventBus eventBus;

    public SignalingSettingsWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
    }

    @NonNull
    @Override
    public Result doWork() {
        NextcloudTalkApplication.Companion.getSharedApplication().getComponentApplication().inject(this);

        Data data = getInputData();

        long internalUserId = data.getLong(BundleKeys.INSTANCE.getKEY_INTERNAL_USER_ID(), -1);

        List<UserEntity> userEntityList = new ArrayList<>();
        UserEntity userEntity;
        if (internalUserId == -1 || (userEntity = userUtils.getUserWithInternalId(internalUserId)) == null) {
            userEntityList = userUtils.getUsers();
        } else {
            userEntityList.add(userEntity);
        }

        for (int i = 0; i < userEntityList.size(); i++) {
            userEntity = userEntityList.get(i);
            UserEntity finalUserEntity = userEntity;

            int apiVersion = ApiUtils.getSignalingApiVersion(finalUserEntity, new int[] {ApiUtils.APIv3, 2, 1});

            ncApi.getSignalingSettings(ApiUtils.getCredentials(userEntity.getUsername(), userEntity.getToken()),
                    ApiUtils.getUrlForSignalingSettings(apiVersion, userEntity.getBaseUrl()))
                    .blockingSubscribe(new Observer<SignalingSettingsOverall>() {
                        @Override
                        public void onSubscribe(Disposable d) {

                        }

                        @Override
                        public void onNext(SignalingSettingsOverall signalingSettingsOverall) {
                            ExternalSignalingServer externalSignalingServer;
                            externalSignalingServer = new ExternalSignalingServer();
                            externalSignalingServer.setExternalSignalingServer(signalingSettingsOverall.getOcs().getSettings().getExternalSignalingServer());
                            externalSignalingServer.setExternalSignalingTicket(signalingSettingsOverall.getOcs().getSettings().getExternalSignalingTicket());

                            try {
                                userUtils.createOrUpdateUser(null, null, null, null, null,
                                        null, null, finalUserEntity.getId(), null, null, LoganSquare.serialize(externalSignalingServer))
                                        .subscribe(new Observer<UserEntity>() {
                                            @Override
                                            public void onSubscribe(Disposable d) {

                                            }

                                            @Override
                                            public void onNext(UserEntity userEntity) {
                                                eventBus.post(new EventStatus(finalUserEntity.getId(), EventStatus.EventType.SIGNALING_SETTINGS, true));
                                            }

                                            @Override
                                            public void onError(Throwable e) {
                                                eventBus.post(new EventStatus(finalUserEntity.getId(), EventStatus.EventType.SIGNALING_SETTINGS, false));
                                            }

                                            @Override
                                            public void onComplete() {

                                            }
                                        });
                            } catch (IOException e) {
                                Log.e(TAG, "Failed to serialize external signaling server");
                            }
                        }

                        @Override
                        public void onError(Throwable e) {
                            eventBus.post(new EventStatus(finalUserEntity.getId(), EventStatus.EventType.SIGNALING_SETTINGS, false));
                        }

                        @Override
                        public void onComplete() {

                        }
                    });
        }

        OneTimeWorkRequest websocketConnectionsWorker = new OneTimeWorkRequest.Builder(WebsocketConnectionsWorker.class).build();
        WorkManager.getInstance().enqueue(websocketConnectionsWorker);

        return Result.success();
    }
}
