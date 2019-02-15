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
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.work.Data;
import androidx.work.Worker;
import androidx.work.WorkerParameters;
import autodagger.AutoInjector;
import com.bluelinelabs.logansquare.LoganSquare;
import com.nextcloud.talk.api.NcApi;
import com.nextcloud.talk.application.NextcloudTalkApplication;
import com.nextcloud.talk.events.EventStatus;
import com.nextcloud.talk.models.database.UserEntity;
import com.nextcloud.talk.models.json.capabilities.CapabilitiesOverall;
import com.nextcloud.talk.utils.ApiUtils;
import com.nextcloud.talk.utils.bundle.BundleKeys;
import com.nextcloud.talk.utils.database.user.UserUtils;
import io.reactivex.Observer;
import io.reactivex.disposables.Disposable;
import okhttp3.JavaNetCookieJar;
import okhttp3.OkHttpClient;
import org.greenrobot.eventbus.EventBus;
import retrofit2.Retrofit;

import javax.inject.Inject;
import java.io.IOException;
import java.net.CookieManager;
import java.util.ArrayList;
import java.util.List;

@AutoInjector(NextcloudTalkApplication.class)
public class CapabilitiesWorker extends Worker {
    public static final String TAG = "CapabilitiesWorker";

    @Inject
    UserUtils userUtils;

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

    private void updateUser(CapabilitiesOverall capabilitiesOverall, UserEntity internalUserEntity) {
        try {
            userUtils.createOrUpdateUser(null, null,
                    null, null,
                    null, null, null, internalUserEntity.getId(),
                    LoganSquare.serialize(capabilitiesOverall.getOcs().getData().getCapabilities()), null, null)
                    .blockingSubscribe(new Observer<UserEntity>() {
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

    @NonNull
    @Override
    public Result doWork() {
        NextcloudTalkApplication.getSharedApplication().getComponentApplication().inject(this);

        Data data = getInputData();

        long internalUserId = data.getLong(BundleKeys.KEY_INTERNAL_USER_ID, -1);

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
                    JavaNetCookieJar(new CookieManager())).build()).build().create(NcApi.class);

            ncApi.getCapabilities(ApiUtils.getCredentials(internalUserEntity.getUsername(),
                    internalUserEntity.getToken()), ApiUtils.getUrlForCapabilities(internalUserEntity.getBaseUrl()))
                    .retry(3)
                    .blockingSubscribe(new Observer<CapabilitiesOverall>() {
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

        return Result.success();
    }
}
