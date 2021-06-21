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

import com.nextcloud.talk.api.NcApi;
import com.nextcloud.talk.application.NextcloudTalkApplication;
import com.nextcloud.talk.models.database.UserEntity;
import com.nextcloud.talk.utils.ApiUtils;
import com.nextcloud.talk.utils.bundle.BundleKeys;
import com.nextcloud.talk.utils.database.user.UserUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.inject.Inject;

import androidx.annotation.NonNull;
import androidx.work.Data;
import androidx.work.Worker;
import androidx.work.WorkerParameters;
import autodagger.AutoInjector;
import io.reactivex.Observer;
import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;

@AutoInjector(NextcloudTalkApplication.class)
public class ShareOperationWorker extends Worker {
    @Inject
    UserUtils userUtils;
    @Inject
    NcApi ncApi;
    private final String TAG = "ShareOperationWorker";
    private long userId;
    private UserEntity operationsUser;
    private String roomToken;
    private List<String> filesArray = new ArrayList<>();
    private String credentials;
    private String baseUrl;
    private String metaData;

    public ShareOperationWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
        NextcloudTalkApplication.Companion.getSharedApplication().getComponentApplication().inject(this);
        Data data = workerParams.getInputData();
        userId = data.getLong(BundleKeys.INSTANCE.getKEY_INTERNAL_USER_ID(), 0);
        roomToken = data.getString(BundleKeys.INSTANCE.getKEY_ROOM_TOKEN());
        metaData = data.getString(BundleKeys.INSTANCE.getKEY_META_DATA());
        Collections.addAll(filesArray, data.getStringArray(BundleKeys.INSTANCE.getKEY_FILE_PATHS()));
        operationsUser = userUtils.getUserWithId(userId);
        credentials = ApiUtils.getCredentials(operationsUser.getUsername(), operationsUser.getToken());
        baseUrl = operationsUser.getBaseUrl();
    }


    @NonNull
    @Override
    public Result doWork() {

        for (int i = 0; i < filesArray.size(); i++) {
            ncApi.createRemoteShare(credentials,
                    ApiUtils.getSharingUrl(baseUrl),
                    filesArray.get(i),
                    roomToken,
                    "10",
                     metaData)
                    .subscribeOn(Schedulers.io())
                    .blockingSubscribe(new Observer<Void>() {
                        @Override
                        public void onSubscribe(Disposable d) {

                        }

                        @Override
                        public void onNext(Void aVoid) {

                        }

                        @Override
                        public void onError(Throwable e) {
                            Log.w(TAG, "error while creating RemoteShare", e);
                        }

                        @Override
                        public void onComplete() {

                        }
                    });
        }

        return Result.success();
    }
}
