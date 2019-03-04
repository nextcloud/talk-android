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

import android.content.Context;
import android.text.TextUtils;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.work.*;
import autodagger.AutoInjector;
import com.bluelinelabs.logansquare.LoganSquare;
import com.nextcloud.talk.api.NcApi;
import com.nextcloud.talk.application.NextcloudTalkApplication;
import com.nextcloud.talk.events.EventStatus;
import com.nextcloud.talk.models.ExternalSignalingServer;
import com.nextcloud.talk.models.database.UserEntity;
import com.nextcloud.talk.models.json.signaling.settings.SignalingSettingsOverall;
import com.nextcloud.talk.utils.ApiUtils;
import com.nextcloud.talk.utils.bundle.BundleKeys;
import com.nextcloud.talk.utils.database.user.UserUtils;
import io.reactivex.Observer;
import io.reactivex.disposables.Disposable;
import org.greenrobot.eventbus.EventBus;

import javax.inject.Inject;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

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
        NextcloudTalkApplication.getSharedApplication().getComponentApplication().inject(this);

        Data data = getInputData();

        long internalUserId = data.getLong(BundleKeys.KEY_INTERNAL_USER_ID, -1);

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
            ncApi.getSignalingSettings(ApiUtils.getCredentials(userEntity.getUsername(), userEntity.getToken()),
                    ApiUtils.getUrlForSignalingSettings(userEntity.getBaseUrl()))
                    .blockingSubscribe(new Observer<SignalingSettingsOverall>() {
                        @Override
                        public void onSubscribe(Disposable d) {

                        }

                        @Override
                        public void onNext(SignalingSettingsOverall signalingSettingsOverall) {
                            ExternalSignalingServer externalSignalingServer;
                            if (!TextUtils.isEmpty(signalingSettingsOverall.getOcs().getSettings().getExternalSignalingServer()) &&
                                    !TextUtils.isEmpty(signalingSettingsOverall.getOcs().getSettings().getExternalSignalingTicket())) {
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
                            } else {
                                eventBus.post(new EventStatus(finalUserEntity.getId(), EventStatus.EventType.SIGNALING_SETTINGS, true));
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
