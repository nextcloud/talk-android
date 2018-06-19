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

package com.nextcloud.talk.jobs;


import android.app.NotificationManager;
import android.content.Context;
import android.os.Build;
import android.support.annotation.NonNull;
import android.text.TextUtils;
import android.util.Log;

import com.bluelinelabs.logansquare.LoganSquare;
import com.evernote.android.job.Job;
import com.nextcloud.talk.R;
import com.nextcloud.talk.api.NcApi;
import com.nextcloud.talk.application.NextcloudTalkApplication;
import com.nextcloud.talk.models.database.UserEntity;
import com.nextcloud.talk.models.json.generic.GenericOverall;
import com.nextcloud.talk.models.json.push.PushConfigurationState;
import com.nextcloud.talk.utils.ApiUtils;
import com.nextcloud.talk.utils.database.user.UserUtils;

import java.io.IOException;
import java.net.CookieManager;
import java.util.HashMap;
import java.util.zip.CRC32;

import javax.inject.Inject;

import autodagger.AutoInjector;
import io.reactivex.CompletableObserver;
import io.reactivex.Observer;
import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;
import okhttp3.JavaNetCookieJar;
import okhttp3.OkHttpClient;
import retrofit2.Retrofit;

@AutoInjector(NextcloudTalkApplication.class)
public class AccountRemovalJob extends Job {
    public static final String TAG = "AccountRemovalJob";

    @Inject
    UserUtils userUtils;

    @Inject
    Retrofit retrofit;

    @Inject
    OkHttpClient okHttpClient;

    NcApi ncApi;

    @NonNull
    @Override
    protected Result onRunJob(Params params) {
        NextcloudTalkApplication.getSharedApplication().getComponentApplication().inject(this);

        PushConfigurationState pushConfigurationState;
        for (Object userEntityObject : userUtils.getUsersScheduledForDeletion()) {
            UserEntity userEntity = (UserEntity) userEntityObject;
            try {
                if (!TextUtils.isEmpty(userEntity.getPushConfigurationState())) {
                    pushConfigurationState = LoganSquare.parse(userEntity.getPushConfigurationState(),
                            PushConfigurationState.class);
                    PushConfigurationState finalPushConfigurationState = pushConfigurationState;

                    ncApi = retrofit.newBuilder().client(okHttpClient.newBuilder().cookieJar(new
                            JavaNetCookieJar(new CookieManager())).build()).build().create(NcApi.class);

                    ncApi.unregisterDeviceForNotificationsWithNextcloud(ApiUtils.getCredentials(userEntity.getUsername(),
                            userEntity.getToken()), ApiUtils.getUrlNextcloudPush(userEntity.getBaseUrl()))
                            .subscribeOn(Schedulers.newThread())
                            .subscribe(new Observer<GenericOverall>() {
                                @Override
                                public void onSubscribe(Disposable d) {

                                }

                                @Override
                                public void onNext(GenericOverall genericOverall) {
                                    if (genericOverall.getOcs().getMeta().getStatusCode() == 200
                                            || genericOverall.getOcs().getMeta().getStatusCode() == 202) {
                                        HashMap<String, String> queryMap = new HashMap<>();
                                        queryMap.put("deviceIdentifier", finalPushConfigurationState.deviceIdentifier);
                                        queryMap.put("userPublicKey", finalPushConfigurationState.getUserPublicKey());
                                        queryMap.put("deviceIdentifierSignature",
                                                finalPushConfigurationState.getDeviceIdentifierSignature());

                                        ncApi.unregisterDeviceForNotificationsWithProxy
                                                (ApiUtils.getCredentials(userEntity.getUsername(),
                                                        userEntity.getToken()), ApiUtils.getUrlPushProxy(), queryMap)
                                                .subscribeOn(Schedulers.newThread())
                                                .subscribe(new Observer<Void>() {
                                                    @Override
                                                    public void onSubscribe(Disposable d) {

                                                    }

                                                    @Override
                                                    public void onNext(Void aVoid) {
                                                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                                            String groupName = String.format(getContext().getResources().getString(R.string
                                                                    .nc_notification_channel), userEntity.getUserId(), userEntity.getBaseUrl());
                                                            CRC32 crc32 = new CRC32();
                                                            crc32.update(groupName.getBytes());
                                                            NotificationManager notificationManager =
                                                                    (NotificationManager) getContext().getSystemService(Context
                                                                            .NOTIFICATION_SERVICE);

                                                            if (notificationManager != null) {
                                                                notificationManager.deleteNotificationChannelGroup(Long
                                                                        .toString(crc32.getValue()));
                                                            }
                                                        }

                                                        userUtils.deleteUser(userEntity.getId()).subscribe(new
                                                                                                                   CompletableObserver() {
                                                                                                                       @Override
                                                                                                                       public void onSubscribe(Disposable d) {

                                                                                                                       }

                                                                                                                       @Override
                                                                                                                       public void onComplete() {

                                                                                                                       }

                                                                                                                       @Override
                                                                                                                       public void onError(Throwable e) {

                                                                                                                       }
                                                                                                                   });
                                                    }

                                                    @Override
                                                    public void onError(Throwable e) {

                                                    }

                                                    @Override
                                                    public void onComplete() {

                                                    }
                                                });
                                    }
                                }

                                @Override
                                public void onError(Throwable e) {

                                }

                                @Override
                                public void onComplete() {

                                }
                            });
                } else {
                    userUtils.deleteUser(userEntity.getId())
                            .subscribeOn(Schedulers.newThread())
                            .subscribe(new CompletableObserver() {
                                @Override
                                public void onSubscribe(Disposable d) {

                                }

                                @Override
                                public void onComplete() {

                                }

                                @Override
                                public void onError(Throwable e) {

                                }
                            });
                }
            } catch (IOException e) {
                Log.d(TAG, "Something went wrong while removing job at parsing PushConfigurationState");
                userUtils.deleteUser(userEntity.getId())
                        .subscribeOn(Schedulers.newThread())
                        .subscribe(new CompletableObserver() {
                            @Override
                            public void onSubscribe(Disposable d) {

                            }

                            @Override
                            public void onComplete() {

                            }

                            @Override
                            public void onError(Throwable e) {

                            }
                        });
            }
        }
        return Result.SUCCESS;
    }
}
