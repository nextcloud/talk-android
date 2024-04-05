/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2022 Andy Scherzinger <info@andy-scherzinger.de>
 * SPDX-FileCopyrightText: 2022 Marcel Hibbe <dev@mhibbe.de>
 * SPDX-FileCopyrightText: 2017 Mario Danic <mario@lovelyhq.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.jobs;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.work.Data;
import androidx.work.Worker;
import androidx.work.WorkerParameters;
import autodagger.AutoInjector;
import okhttp3.JavaNetCookieJar;
import okhttp3.OkHttpClient;
import retrofit2.Retrofit;

import com.nextcloud.talk.api.NcApi;
import com.nextcloud.talk.application.NextcloudTalkApplication;
import com.nextcloud.talk.utils.ClosedInterfaceImpl;
import com.nextcloud.talk.utils.PushUtils;

import java.net.CookieManager;

import javax.inject.Inject;

@AutoInjector(NextcloudTalkApplication.class)
public class PushRegistrationWorker extends Worker {
    public static final String TAG = "PushRegistrationWorker";
    public static final String ORIGIN = "origin";

    @Inject
    Retrofit retrofit;

    @Inject
    OkHttpClient okHttpClient;

    public PushRegistrationWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
    }

    @NonNull
    @Override
    public Result doWork() {
        NextcloudTalkApplication.Companion.getSharedApplication().getComponentApplication().inject(this);
        if (new ClosedInterfaceImpl().isGooglePlayServicesAvailable()) {
            Data data = getInputData();
            String origin = data.getString("origin");
            Log.d(TAG, "PushRegistrationWorker called via " + origin);

            NcApi ncApi = retrofit
                .newBuilder()
                .client(okHttpClient
                            .newBuilder()
                            .cookieJar(new JavaNetCookieJar(new CookieManager()))
                            .build())
                .build()
                .create(NcApi.class);

            PushUtils pushUtils = new PushUtils();
            pushUtils.generateRsa2048KeyPair();
            pushUtils.pushRegistrationToServer(ncApi);

            return Result.success();
        }
        Log.w(TAG, "executing PushRegistrationWorker doesn't make sense because Google Play Services are not " +
            "available");
        return Result.failure();
    }
}
