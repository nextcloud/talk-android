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

import android.support.annotation.NonNull;
import android.util.Log;

import com.bluelinelabs.logansquare.LoganSquare;
import com.evernote.android.job.Job;
import com.nextcloud.talk.api.NcApi;
import com.nextcloud.talk.application.NextcloudTalkApplication;
import com.nextcloud.talk.events.EventStatus;
import com.nextcloud.talk.models.database.UserEntity;
import com.nextcloud.talk.models.json.capabilities.CapabilitiesOverall;
import com.nextcloud.talk.utils.ApiUtils;
import com.nextcloud.talk.utils.bundle.BundleKeys;
import com.nextcloud.talk.utils.database.user.UserUtils;

import org.greenrobot.eventbus.EventBus;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import javax.inject.Inject;

import autodagger.AutoInjector;
import io.reactivex.Observer;
import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;
import okhttp3.JavaNetCookieJar;
import okhttp3.OkHttpClient;
import retrofit2.Retrofit;

@AutoInjector(NextcloudTalkApplication.class)
public class CapabilitiesJob extends Job {
    public static final String TAG = "CapabilitiesJob";

    @Inject
    UserUtils userUtils;

    @Inject
    Retrofit retrofit;

    @Inject
    EventBus eventBus;

    @Inject
    OkHttpClient okHttpClient;

    NcApi ncApi;

    @NonNull
    @Override
    protected Result onRunJob(Params params) {
        NextcloudTalkApplication.getSharedApplication().getComponentApplication().inject(this);

        long internalUserId = getParams().getExtras().getLong(BundleKeys.KEY_INTERNAL_USER_ID, -1);

        UserEntity userEntity;
        List userEntityObjectList = new ArrayList();

        if (internalUserId == -1 || (userEntity = userUtils.getUserWithInternalId(internalUserId)) == null) {
            userEntityObjectList = userUtils.getUsers();
        } else {
            userEntityObjectList.add(userEntity);
        }

        for (Object userEntityObject : userEntityObjectList) {
            UserEntity internalUserEntity = (UserEntity) userEntityObject;

            ncApi = retrofit.newBuilder().client(okHttpClient.newBuilder().cookieJar(new
                    JavaNetCookieJar(new java.net.CookieManager())).build()).build().create(NcApi.class);

            ncApi.getCapabilities(ApiUtils.getCredentials(internalUserEntity.getUsername(),
                    internalUserEntity.getToken()), ApiUtils.getUrlForCapabilities(internalUserEntity.getBaseUrl()))
                    .retry(3)
                    .subscribeOn(Schedulers.newThread())
                    .subscribe(new Observer<CapabilitiesOverall>() {
                        @Override
                        public void onSubscribe(Disposable d) {

                        }

                        @Override
                        public void onNext(CapabilitiesOverall capabilitiesOverall) {
                            updateUser(capabilitiesOverall, internalUserEntity);
                        }

                        @Override
                        public void onError(Throwable e) {
                            eventBus.post(new EventStatus(internalUserEntity.getId(),
                                    EventStatus.EventType.CAPABILITIES_FETCH, false));

                        }

                        @Override
                        public void onComplete() {

                        }
                    });
        }

        return Result.SUCCESS;
    }

    private void updateUser(CapabilitiesOverall capabilitiesOverall, UserEntity internalUserEntity) {
        try {
            userUtils.createOrUpdateUser(null, null,
                    null, null,
                    null, null, null, internalUserEntity.getId(),
                    LoganSquare.serialize(capabilitiesOverall.getOcs().getData().getCapabilities()), null)
                    .subscribeOn(Schedulers.newThread())
                    .subscribe(new Observer<UserEntity>() {
                        @Override
                        public void onSubscribe(Disposable d) {

                        }

                        @Override
                        public void onNext(UserEntity userEntity) {
                            eventBus.post(new EventStatus(userEntity.getId(),
                                    EventStatus.EventType.CAPABILITIES_FETCH, true));
                        }

                        @Override
                        public void onError(Throwable e) {
                            eventBus.post(new EventStatus(internalUserEntity.getId(),
                                    EventStatus.EventType.CAPABILITIES_FETCH, false));
                        }

                        @Override
                        public void onComplete() {

                        }
                    });
        } catch (IOException e) {
            Log.e(TAG, "Failed to create or update user");
        }

    }
}
