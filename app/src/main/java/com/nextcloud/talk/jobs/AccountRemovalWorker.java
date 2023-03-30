/*
 * Nextcloud Talk application
 *
 * @author Mario Danic
 * @author Andy Scherzinger
 * Copyright (C) 2022 Andy Scherzinger <info@andy-scherzinger.de>
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
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.util.Log;

import com.nextcloud.talk.R;
import com.nextcloud.talk.api.NcApi;
import com.nextcloud.talk.application.NextcloudTalkApplication;
import com.nextcloud.talk.arbitrarystorage.ArbitraryStorageManager;
import com.nextcloud.talk.data.user.model.User;
import com.nextcloud.talk.models.json.generic.GenericMeta;
import com.nextcloud.talk.models.json.generic.GenericOverall;
import com.nextcloud.talk.models.json.push.PushConfigurationState;
import com.nextcloud.talk.users.UserManager;
import com.nextcloud.talk.utils.ApiUtils;
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

    @Inject
    UserManager userManager;

    @Inject
    ArbitraryStorageManager arbitraryStorageManager;

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
                        ApiUtils.getUrlNextcloudPush(user.getBaseUrl()))
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
                        }

                        @Override
                        public void onComplete() {
                            // unused atm
                        }
                    });
            } else {
                deleteUser(user);
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
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
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
                        }

                        if (user.getId() != null) {
                            WebSocketConnectionHelper.deleteExternalSignalingInstanceForUserEntity(user.getId());
                        }
                        deleteAllEntriesForAccountIdentifier(user);
                    }

                    @Override
                    public void onError(Throwable e) {
                        Log.e(TAG, "error while trying to unregister Device For Notification With Proxy", e);
                    }

                    @Override
                    public void onComplete() {
                        // unused atm
                    }
                });
    }

    private void deleteAllEntriesForAccountIdentifier(User user) {
        if (user.getId() != null) {
            try {
                arbitraryStorageManager.deleteAllEntriesForAccountIdentifier(user.getId());
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
                Log.d(TAG, "deleted user: " + username);
            } catch (Throwable e) {
                Log.e(TAG, "error while trying to delete user", e);
            }
        }
        if (userManager.getUsers().blockingGet().isEmpty()) {
            restartApp(getApplicationContext());
        }
    }

    public static void restartApp(Context context) {
        PackageManager packageManager = context.getPackageManager();
        Intent intent = packageManager.getLaunchIntentForPackage(context.getPackageName());
        ComponentName componentName = intent.getComponent();
        Intent mainIntent = Intent.makeRestartActivityTask(componentName);
        context.startActivity(mainIntent);
        Runtime.getRuntime().exit(0);
    }
}
