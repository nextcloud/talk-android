/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2022 Andy Scherzinger <info@andy-scherzinger.de>
 * SPDX-FileCopyrightText: 2017 Mario Danic <mario@lovelyhq.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.jobs;

import android.app.NotificationManager;
import android.content.Context;
import android.util.Log;

import com.nextcloud.talk.R;
import com.nextcloud.talk.api.NcApi;
import com.nextcloud.talk.application.NextcloudTalkApplication;
import com.nextcloud.talk.arbitrarystorage.ArbitraryStorageManager;
import com.nextcloud.talk.data.database.dao.ChatBlocksDao;
import com.nextcloud.talk.data.database.dao.ChatMessagesDao;
import com.nextcloud.talk.data.database.dao.ConversationsDao;
import com.nextcloud.talk.data.user.model.User;
import com.nextcloud.talk.models.json.generic.GenericMeta;
import com.nextcloud.talk.models.json.generic.GenericOverall;
import com.nextcloud.talk.models.json.push.PushConfigurationState;
import com.nextcloud.talk.users.UserManager;
import com.nextcloud.talk.utils.ApiUtils;
import com.nextcloud.talk.utils.preferences.AppPreferences;
import com.nextcloud.talk.webrtc.WebSocketConnectionHelper;

import java.net.CookieManager;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;
import java.util.zip.CRC32;

import javax.inject.Inject;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;
import autodagger.AutoInjector;
import io.reactivex.Observer;
import io.reactivex.disposables.Disposable;
import okhttp3.JavaNetCookieJar;
import okhttp3.OkHttpClient;
import retrofit2.Retrofit;

@AutoInjector(NextcloudTalkApplication.class)
public class AccountRemovalWorker extends Worker {
    public static final String TAG = "AccountRemovalWorker";

    @Inject UserManager userManager;

    @Inject ArbitraryStorageManager arbitraryStorageManager;

    @Inject AppPreferences appPreferences;

    @Inject Retrofit retrofit;

    @Inject OkHttpClient okHttpClient;

    @Inject ChatMessagesDao chatMessagesDao;

    @Inject ConversationsDao conversationsDao;

    @Inject ChatBlocksDao chatBlocksDao;

    NcApi ncApi;

    public AccountRemovalWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
    }

    @NonNull
    @Override
    public Result doWork() {
        Objects.requireNonNull(NextcloudTalkApplication.Companion.getSharedApplication()).getComponentApplication().inject(this);

        List<User> users = userManager.getUsersScheduledForDeletion().blockingGet();
        for (User user : users) {
            if (user.getPushConfigurationState() != null) {
                PushConfigurationState finalPushConfigurationState = user.getPushConfigurationState();

                ncApi = retrofit
                    .newBuilder()
                    .client(okHttpClient
                                .newBuilder()
                                .cookieJar(new JavaNetCookieJar(new CookieManager()))
                                .build())
                    .build()
                    .create(NcApi.class);

                ncApi.unregisterDeviceForNotificationsWithNextcloud(
                        ApiUtils.getCredentials(user.getUsername(), user.getToken()),
                        ApiUtils.getUrlNextcloudPush(Objects.requireNonNull(user.getBaseUrl())))
                    .blockingSubscribe(new Observer<GenericOverall>() {
                        @Override
                        public void onSubscribe(@io.reactivex.annotations.NonNull Disposable d) {
                            // unused atm
                        }

                        @Override
                        public void onNext(@io.reactivex.annotations.NonNull GenericOverall genericOverall) {
                            GenericMeta meta = Objects.requireNonNull(genericOverall.getOcs()).getMeta();
                            int statusCode = Objects.requireNonNull(meta).getStatusCode();

                            if (statusCode == 200 || statusCode == 202) {
                                HashMap<String, String> queryMap = new HashMap<>();
                                queryMap.put("deviceIdentifier",
                                             finalPushConfigurationState.getDeviceIdentifier());
                                queryMap.put("userPublicKey", finalPushConfigurationState.getUserPublicKey());
                                queryMap.put("deviceIdentifierSignature",
                                             finalPushConfigurationState.getDeviceIdentifierSignature());
                                unregisterDeviceForNotificationWithProxy(queryMap, user);
                            }
                        }

                        @Override
                        public void onError(@io.reactivex.annotations.NonNull Throwable e) {
                            Log.e(TAG, "error while trying to unregister Device For Notifications", e);
                            initiateUserDeletion(user);
                        }

                        @Override
                        public void onComplete() {
                            // unused atm
                        }
                    });
            } else {
                initiateUserDeletion(user);
            }
        }

        return Result.success();
    }

    private void unregisterDeviceForNotificationWithProxy(HashMap<String, String> queryMap, User user) {
        ncApi.unregisterDeviceForNotificationsWithProxy
                (ApiUtils.getUrlPushProxy(), queryMap)
                .subscribe(new Observer<Void>() {
                    @Override
                    public void onSubscribe(Disposable d) {
                        // unused atm
                    }

                    @Override
                    public void onNext(Void aVoid) {
                            String groupName = String.format(
                                getApplicationContext()
                                    .getResources()
                                    .getString(R.string.nc_notification_channel), user.getUserId(), user.getBaseUrl());
                            CRC32 crc32 = new CRC32();
                            crc32.update(groupName.getBytes());
                            NotificationManager notificationManager =
                                    (NotificationManager) getApplicationContext()
                                        .getSystemService(Context.NOTIFICATION_SERVICE);

                            if (notificationManager != null) {
                                notificationManager.deleteNotificationChannelGroup(
                                    Long.toString(crc32.getValue()));
                            }

                        initiateUserDeletion(user);
                    }

                    @Override
                    public void onError(Throwable e) {
                        Log.e(TAG, "error while trying to unregister Device For Notification With Proxy", e);
                        initiateUserDeletion(user);
                    }

                    @Override
                    public void onComplete() {
                        // unused atm
                    }
                });
    }

    private void initiateUserDeletion(User user) {
        if (user.getId() != null) {
            long id = user.getId();
            WebSocketConnectionHelper.deleteExternalSignalingInstanceForUserEntity(id);

            try {
                arbitraryStorageManager.deleteAllEntriesForAccountIdentifier(id);
                deleteUser(user);
            } catch (Throwable e) {
                Log.e(TAG, "error while trying to delete All Entries For Account Identifier", e);
            }
        }
    }

    private void deleteUser(User user) {
        if (user.getId() != null) {
            String username = user.getUsername();
            try {
                userManager.deleteUser(user.getId());
                if (username != null) {
                    Log.d(TAG, "deleted user: " + username);
                }
            } catch (Throwable e) {
                Log.e(TAG, "error while trying to delete user", e);
            }
        }
    }
}
