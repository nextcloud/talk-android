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
import android.text.TextUtils;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;
import autodagger.AutoInjector;
import com.bluelinelabs.logansquare.LoganSquare;
import com.nextcloud.talk.R;
import com.nextcloud.talk.api.NcApi;
import com.nextcloud.talk.application.NextcloudTalkApplication;
import com.nextcloud.talk.models.database.UserEntity;
import com.nextcloud.talk.models.json.generic.GenericOverall;
import com.nextcloud.talk.models.json.push.PushConfigurationState;
import com.nextcloud.talk.utils.ApiUtils;
import com.nextcloud.talk.utils.database.arbitrarystorage.ArbitraryStorageUtils;
import com.nextcloud.talk.utils.database.user.UserUtils;
import com.nextcloud.talk.webrtc.WebSocketConnectionHelper;
import io.reactivex.rxjava3.core.CompletableObserver;
import io.reactivex.rxjava3.core.Observer;
import io.reactivex.rxjava3.disposables.Disposable;
import okhttp3.JavaNetCookieJar;
import okhttp3.OkHttpClient;
import retrofit2.Retrofit;

import javax.inject.Inject;
import java.io.IOException;
import java.net.CookieManager;
import java.util.HashMap;
import java.util.zip.CRC32;

@AutoInjector(NextcloudTalkApplication.class)
public class AccountRemovalWorker extends Worker {
    public static final String TAG = "AccountRemovalWorker";

    @Inject
    UserUtils userUtils;

    @Inject
    ArbitraryStorageUtils arbitraryStorageUtils;

    @Inject
    Retrofit retrofit;

    @Inject
    OkHttpClient okHttpClient;

    NcApi ncApi;

    public AccountRemovalWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
    }

    @NonNull
    @Override
    public Result doWork() {
        NextcloudTalkApplication.Companion.getSharedApplication().getComponentApplication().inject(this);

        PushConfigurationState pushConfigurationState;
        String credentials;
        for (Object userEntityObject : userUtils.getUsersScheduledForDeletion()) {
            UserEntity userEntity = (UserEntity) userEntityObject;
            try {
                if (!TextUtils.isEmpty(userEntity.getPushConfigurationState())) {
                    pushConfigurationState = LoganSquare.parse(userEntity.getPushConfigurationState(),
                            PushConfigurationState.class);
                    PushConfigurationState finalPushConfigurationState = pushConfigurationState;

                    credentials = ApiUtils.getCredentials(userEntity.getUsername(), userEntity.getToken());

                    ncApi = retrofit.newBuilder().client(okHttpClient.newBuilder().cookieJar(new
                            JavaNetCookieJar(new CookieManager())).build()).build().create(NcApi.class);

                    ncApi.unregisterDeviceForNotificationsWithNextcloud(credentials, ApiUtils.getUrlNextcloudPush(userEntity
                            .getBaseUrl()))
                            .blockingSubscribe(new Observer<GenericOverall>() {
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
                                        unregisterDeviceForNotificationWithProxy(queryMap, userEntity);
                                    }
                                }

                                @Override
                                public void onError(Throwable e) {
                                    Log.e(TAG, "error while trying to unregister Device For Notifications", e);
                                }

                                @Override
                                public void onComplete() {

                                }
                            });
                } else {
                    deleteUser(userEntity);
                }
            } catch (IOException e) {
                Log.d(TAG, "Something went wrong while removing job at parsing PushConfigurationState");
                deleteUser(userEntity);
            }
        }

        return Result.success();
    }

    private void unregisterDeviceForNotificationWithProxy(HashMap<String, String> queryMap, UserEntity userEntity) {
        ncApi.unregisterDeviceForNotificationsWithProxy
                (ApiUtils.getUrlPushProxy(), queryMap)
                .subscribe(new Observer<Void>() {
                    @Override
                    public void onSubscribe(Disposable d) {

                    }

                    @Override
                    public void onNext(Void aVoid) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            String groupName = String.format(getApplicationContext().getResources()
                                    .getString(R.string
                                            .nc_notification_channel), userEntity.getUserId(), userEntity.getBaseUrl());
                            CRC32 crc32 = new CRC32();
                            crc32.update(groupName.getBytes());
                            NotificationManager notificationManager =
                                    (NotificationManager) getApplicationContext().getSystemService
                                            (Context.NOTIFICATION_SERVICE);

                            if (notificationManager != null) {
                                notificationManager.deleteNotificationChannelGroup(Long
                                        .toString(crc32.getValue()));
                            }
                        }
                        WebSocketConnectionHelper.deleteExternalSignalingInstanceForUserEntity(userEntity.getId());
                        deleteAllEntriesForAccountIdentifier(userEntity);
                    }

                    @Override
                    public void onError(Throwable e) {
                        Log.e(TAG, "error while trying to unregister Device For Notification With Proxy", e);
                    }

                    @Override
                    public void onComplete() {

                    }
                });
    }

    private void deleteAllEntriesForAccountIdentifier(UserEntity userEntity) {
        arbitraryStorageUtils.deleteAllEntriesForAccountIdentifier(userEntity.getId()).subscribe(new Observer() {
            @Override
            public void onSubscribe(Disposable d) {

            }

            @Override
            public void onNext(Object o) {
                deleteUser(userEntity);
            }

            @Override
            public void onError(Throwable e) {
                Log.e(TAG, "error while trying to delete All Entries For Account Identifier", e);
            }

            @Override
            public void onComplete() {

            }
        });
    }

    private void deleteUser(UserEntity userEntity) {
        String username = userEntity.getUsername();
        userUtils.deleteUser(userEntity.getId())
                .subscribe(new CompletableObserver() {
                    @Override
                    public void onSubscribe(Disposable d) {

                    }

                    @Override
                    public void onComplete() {
                        Log.d(TAG, "deleted user: " + username);
                    }

                    @Override
                    public void onError(Throwable e) {
                        Log.e(TAG, "error while trying to delete user", e);
                    }
                });
    }
}
