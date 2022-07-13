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

import com.bluelinelabs.logansquare.LoganSquare;
import com.nextcloud.talk.api.NcApi;
import com.nextcloud.talk.application.NextcloudTalkApplication;
import com.nextcloud.talk.data.user.model.User;
import com.nextcloud.talk.events.EventStatus;
import com.nextcloud.talk.models.json.capabilities.CapabilitiesOverall;
import com.nextcloud.talk.users.UserManager;
import com.nextcloud.talk.utils.ApiUtils;
import com.nextcloud.talk.utils.bundle.BundleKeys;

import org.greenrobot.eventbus.EventBus;

import java.io.IOException;
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

    @Inject
    UserManager userManager;

    @Inject
    Retrofit retrofit;

    @Inject
    EventBus eventBus;

    @Inject
    OkHttpClient okHttpClient;

    NcApi ncApi;

    private Disposable dbQueryDisposable;

    public CapabilitiesWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);

    }

    private void updateUser(CapabilitiesOverall capabilitiesOverall, User internalUserEntity) {
        try {
            dbQueryDisposable = userManager.createOrUpdateUser(null, new UserManager.UserAttributes(
                internalUserEntity.getId(),
                null,
                null,
                null,
                null,
                null,
                null,
                LoganSquare.serialize(capabilitiesOverall.getOcs().getData().getCapabilities()),
                null,
                null)).subscribe(
                user -> eventBus.post(new EventStatus(user.getId(),
                                                      EventStatus.EventType.CAPABILITIES_FETCH,
                                                      true)),
                throwable -> {
                    eventBus.post(new EventStatus(internalUserEntity.getId(),
                                                  EventStatus.EventType.CAPABILITIES_FETCH,
                                                  false));
                    dispose(dbQueryDisposable);
                },
                () -> dispose(dbQueryDisposable));
        } catch (IOException e) {
            Log.e(TAG, "Failed to create or update user");
        }
    }

    private void dispose(Disposable disposable) {
        if (disposable != null && !disposable.isDisposed()) {
            disposable.dispose();
        }
    }

    @NonNull
    @Override
    public Result doWork() {
        NextcloudTalkApplication.Companion.getSharedApplication().getComponentApplication().inject(this);

        Data data = getInputData();

        long internalUserId = data.getLong(BundleKeys.INSTANCE.getKEY_INTERNAL_USER_ID(), -1);

        User user;
        List<User> userEntityObjectList = new ArrayList<>();

        if (internalUserId == -1 ||
            (user = userManager.getUserWithInternalId(internalUserId).blockingGet()) == null) {
            userEntityObjectList = userManager.getUsers().blockingGet();
        } else {
            userEntityObjectList.add(user);
        }

        for (User userEntityObject : userEntityObjectList) {

            ncApi = retrofit.newBuilder().client(okHttpClient.newBuilder().cookieJar(new
                                                                                         JavaNetCookieJar(new CookieManager())).build()).build().create(NcApi.class);

            ncApi.getCapabilities(ApiUtils.getCredentials(userEntityObject.getUsername(),
                                                          userEntityObject.getToken()),
                                  ApiUtils.getUrlForCapabilities(userEntityObject.getBaseUrl()))
                .retry(3)
                .blockingSubscribe(new Observer<CapabilitiesOverall>() {
                    @Override
                    public void onSubscribe(Disposable d) {

                    }

                    @Override
                    public void onNext(CapabilitiesOverall capabilitiesOverall) {
                        updateUser(capabilitiesOverall, userEntityObject);
                    }

                    @Override
                    public void onError(Throwable e) {
                        eventBus.post(new EventStatus(userEntityObject.getId(),
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
