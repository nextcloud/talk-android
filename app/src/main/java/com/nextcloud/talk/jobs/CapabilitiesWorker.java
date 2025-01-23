/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2022 Andy Scherzinger <info@andy-scherzinger.de>
 * SPDX-FileCopyrightText: 2017-2018 Mario Danic <mario@lovelyhq.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.jobs;

import android.content.Context;
import android.util.Log;

import com.nextcloud.talk.api.NcApi;
import com.nextcloud.talk.application.NextcloudTalkApplication;
import com.nextcloud.talk.data.user.model.User;
import com.nextcloud.talk.events.EventStatus;
import com.nextcloud.talk.models.json.capabilities.CapabilitiesOverall;
import com.nextcloud.talk.users.UserManager;
import com.nextcloud.talk.utils.ApiUtils;
import com.nextcloud.talk.utils.UserIdUtils;
import com.nextcloud.talk.utils.bundle.BundleKeys;

import org.greenrobot.eventbus.EventBus;

import java.net.CookieManager;
import java.util.ArrayList;
import java.util.List;

import javax.inject.Inject;

import androidx.annotation.NonNull;
import androidx.work.Data;
import androidx.work.Worker;
import androidx.work.WorkerParameters;
import autodagger.AutoInjector;
import io.reactivex.Observer;
import io.reactivex.disposables.Disposable;
import okhttp3.JavaNetCookieJar;
import okhttp3.OkHttpClient;
import retrofit2.Retrofit;

@AutoInjector(NextcloudTalkApplication.class)
public class CapabilitiesWorker extends Worker {
    public static final String TAG = "CapabilitiesWorker";
    public static final long NO_ID = -1;

    @Inject
    UserManager userManager;

    @Inject
    Retrofit retrofit;

    @Inject
    EventBus eventBus;

    @Inject
    OkHttpClient okHttpClient;

    NcApi ncApi;

    public CapabilitiesWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
    }

    private void updateUser(CapabilitiesOverall capabilitiesOverall, User user) {
        if (capabilitiesOverall.getOcs() != null && capabilitiesOverall.getOcs().getData() != null &&
            capabilitiesOverall.getOcs().getData().getCapabilities() != null) {

            user.setCapabilities(capabilitiesOverall.getOcs().getData().getCapabilities());
            user.setServerVersion(capabilitiesOverall.getOcs().getData().getServerVersion());

            try {
                int rowsCount = userManager.updateOrCreateUser(user).blockingGet();
                if (rowsCount > 0) {
                    eventBus.post(new EventStatus(UserIdUtils.INSTANCE.getIdForUser(user),
                                                  EventStatus.EventType.CAPABILITIES_FETCH,
                                                  true));
                } else {
                    Log.w(TAG, "Error updating user");
                    eventBus.post(new EventStatus(UserIdUtils.INSTANCE.getIdForUser(user),
                                                  EventStatus.EventType.CAPABILITIES_FETCH,
                                                  false));
                }
            } catch (Exception e) {
                Log.e(TAG, "Error updating user", e);
                eventBus.post(new EventStatus(UserIdUtils.INSTANCE.getIdForUser(user),
                                              EventStatus.EventType.CAPABILITIES_FETCH,
                                              false));
            }
        }
    }

    @NonNull
    @Override
    public Result doWork() {
        NextcloudTalkApplication.Companion.getSharedApplication().getComponentApplication().inject(this);

        Data data = getInputData();

        long internalUserId = data.getLong(BundleKeys.KEY_INTERNAL_USER_ID, -1);

        List<User> userEntityObjectList = new ArrayList<>();
        boolean userNotFound = userManager.getUserWithInternalId(internalUserId).isEmpty().blockingGet();

        if (internalUserId == -1 || userNotFound) {
            userEntityObjectList = userManager.getUsers().blockingGet();
        } else {
            userEntityObjectList.add(userManager.getUserWithInternalId(internalUserId).blockingGet());
        }

        for (User user : userEntityObjectList) {

            ncApi = retrofit
                .newBuilder()
                .client(okHttpClient
                            .newBuilder()
                            .cookieJar(new JavaNetCookieJar(new CookieManager()))
                            .build())
                .build()
                .create(NcApi.class);

            String url = "";
            String baseurl = user.getBaseUrl();
            if (baseurl != null) {
                url = ApiUtils.getUrlForCapabilities(baseurl);
            }

            ncApi.getCapabilities(ApiUtils.getCredentials(user.getUsername(), user.getToken()), url)
                .retry(3)
                .blockingSubscribe(new Observer<CapabilitiesOverall>() {
                    @Override
                    public void onSubscribe(Disposable d) {
                        // unused atm
                    }

                    @Override
                    public void onNext(CapabilitiesOverall capabilitiesOverall) {
                        updateUser(capabilitiesOverall, user);
                    }

                    @Override
                    public void onError(Throwable e) {
                        eventBus.post(new EventStatus(user.getId(),
                                                      EventStatus.EventType.CAPABILITIES_FETCH,
                                                      false));
                    }

                    @Override
                    public void onComplete() {
                        // unused atm
                    }
                });
        }

        return Result.success();
    }
}
