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
import com.nextcloud.talk.models.ExternalSignalingServer;
import com.nextcloud.talk.models.json.signaling.settings.SignalingSettingsOverall;
import com.nextcloud.talk.users.UserManager;
import com.nextcloud.talk.utils.ApiUtils;
import com.nextcloud.talk.utils.UserIdUtils;
import com.nextcloud.talk.utils.bundle.BundleKeys;

import org.greenrobot.eventbus.EventBus;

import java.util.ArrayList;
import java.util.List;

import javax.inject.Inject;

import androidx.annotation.NonNull;
import androidx.work.Data;
import androidx.work.Worker;
import androidx.work.WorkerParameters;
import autodagger.AutoInjector;
import io.reactivex.Observer;
import io.reactivex.SingleObserver;
import io.reactivex.disposables.Disposable;

@AutoInjector(NextcloudTalkApplication.class)
public class SignalingSettingsWorker extends Worker {

    @Inject
    UserManager userManager;

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

        long internalUserId = data.getLong(BundleKeys.KEY_INTERNAL_USER_ID, -1);

        List<User> userEntityObjectList = new ArrayList<>();
        boolean userNotFound = userManager.getUserWithInternalId(internalUserId).isEmpty().blockingGet();

        if (internalUserId == -1 || userNotFound) {
            userEntityObjectList = userManager.getUsers().blockingGet();
        } else {
            userEntityObjectList.add(userManager.getUserWithInternalId(internalUserId).blockingGet());
        }

        for (User user : userEntityObjectList) {

            int apiVersion = ApiUtils.getSignalingApiVersion(user, new int[] {ApiUtils.API_V3, 2, 1});

            ncApi.getSignalingSettings(
                    ApiUtils.getCredentials(user.getUsername(), user.getToken()),
                    ApiUtils.getUrlForSignalingSettings(apiVersion, user.getBaseUrl()))
                .blockingSubscribe(new Observer<SignalingSettingsOverall>() {
                    @Override
                    public void onSubscribe(Disposable d) {
                        // unused stm
                    }

                    @Override
                    public void onNext(SignalingSettingsOverall signalingSettingsOverall) {
                        ExternalSignalingServer externalSignalingServer;
                        externalSignalingServer = new ExternalSignalingServer();

                        if (signalingSettingsOverall.getOcs() != null &&
                            signalingSettingsOverall.getOcs().getSettings() != null) {
                            externalSignalingServer.setExternalSignalingServer(signalingSettingsOverall
                                                                                   .getOcs()
                                                                                   .getSettings()
                                                                                   .getExternalSignalingServer());
                            externalSignalingServer.setExternalSignalingTicket(signalingSettingsOverall
                                                                                   .getOcs()
                                                                                   .getSettings()
                                                                                   .getExternalSignalingTicket());
                        }

                        user.setExternalSignalingServer(externalSignalingServer);

                        userManager.saveUser(user).subscribe(new SingleObserver<Integer>() {
                            @Override
                            public void onSubscribe(Disposable d) {
                                // unused atm
                            }

                            @Override
                            public void onSuccess(Integer rows) {
                                if (rows > 0) {
                                    eventBus.post(new EventStatus(UserIdUtils.INSTANCE.getIdForUser(user),
                                                                  EventStatus.EventType.SIGNALING_SETTINGS,
                                                                  true));
                                } else {
                                    eventBus.post(new EventStatus(UserIdUtils.INSTANCE.getIdForUser(user),
                                                                  EventStatus.EventType.SIGNALING_SETTINGS,
                                                                  false));
                                }
                            }

                            @Override
                            public void onError(Throwable e) {
                                eventBus.post(new EventStatus(UserIdUtils.INSTANCE.getIdForUser(user),
                                                              EventStatus.EventType.SIGNALING_SETTINGS,
                                                              false));
                            }
                        });
                    }

                    @Override
                    public void onError(Throwable e) {
                        eventBus.post(new EventStatus(UserIdUtils.INSTANCE.getIdForUser(user),
                                                      EventStatus.EventType.SIGNALING_SETTINGS,
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
