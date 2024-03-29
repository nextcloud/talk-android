/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2022 Andy Scherzinger <info@andy-scherzinger.de>
 * SPDX-FileCopyrightText: 2017-2018 Mario Danic <mario@lovelyhq.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.jobs;

import android.content.Context;

import com.nextcloud.talk.api.NcApi;
import com.nextcloud.talk.application.NextcloudTalkApplication;
import com.nextcloud.talk.data.user.model.User;
import com.nextcloud.talk.events.EventStatus;
import com.nextcloud.talk.models.json.generic.GenericOverall;
import com.nextcloud.talk.users.UserManager;
import com.nextcloud.talk.utils.ApiUtils;
import com.nextcloud.talk.utils.UserIdUtils;
import com.nextcloud.talk.utils.bundle.BundleKeys;

import org.greenrobot.eventbus.EventBus;

import java.net.CookieManager;

import javax.inject.Inject;

import androidx.annotation.NonNull;
import androidx.work.Data;
import androidx.work.Worker;
import androidx.work.WorkerParameters;
import autodagger.AutoInjector;
import io.reactivex.Observer;
import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;
import okhttp3.JavaNetCookieJar;
import okhttp3.OkHttpClient;
import retrofit2.Retrofit;

@AutoInjector(NextcloudTalkApplication.class)
public class DeleteConversationWorker extends Worker {
    @Inject
    Retrofit retrofit;

    @Inject
    OkHttpClient okHttpClient;

    @Inject
    UserManager userManager;

    @Inject
    EventBus eventBus;

    NcApi ncApi;

    public DeleteConversationWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
        NextcloudTalkApplication.Companion.getSharedApplication().getComponentApplication().inject(this);
    }

    @NonNull
    @Override
    public Result doWork() {
        Data data = getInputData();
        long operationUserId = data.getLong(BundleKeys.KEY_INTERNAL_USER_ID, -1);
        String conversationToken = data.getString(BundleKeys.KEY_ROOM_TOKEN);
        User operationUser = userManager.getUserWithId(operationUserId).blockingGet();

        if (operationUser != null) {
            int apiVersion = ApiUtils.getConversationApiVersion(operationUser, new int[]{ApiUtils.API_V4, 1});

            String credentials = ApiUtils.getCredentials(operationUser.getUsername(), operationUser.getToken());
            ncApi = retrofit
                .newBuilder()
                .client(okHttpClient.newBuilder().cookieJar(new JavaNetCookieJar(new CookieManager())).build())
                .build()
                .create(NcApi.class);

            EventStatus eventStatus = new EventStatus(UserIdUtils.INSTANCE.getIdForUser(operationUser),
                                                      EventStatus.EventType.CONVERSATION_UPDATE,
                                                      true);

            ncApi.deleteRoom(credentials, ApiUtils.getUrlForRoom(apiVersion, operationUser.getBaseUrl(),
                                                                 conversationToken))
                .subscribeOn(Schedulers.io())
                .blockingSubscribe(new Observer<GenericOverall>() {
                    Disposable disposable;

                    @Override
                    public void onSubscribe(Disposable d) {
                        disposable = d;
                    }

                    @Override
                    public void onNext(GenericOverall genericOverall) {
                        eventBus.postSticky(eventStatus);
                    }

                    @Override
                    public void onError(Throwable e) {
                        // unused atm
                    }

                    @Override
                    public void onComplete() {
                        disposable.dispose();
                    }
                });
        }

        return Result.success();
    }
}
