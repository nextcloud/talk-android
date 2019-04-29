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
import androidx.annotation.NonNull;
import androidx.work.Data;
import androidx.work.Worker;
import androidx.work.WorkerParameters;
import autodagger.AutoInjector;
import com.nextcloud.talk.api.NcApi;
import com.nextcloud.talk.application.NextcloudTalkApplication;
import com.nextcloud.talk.events.EventStatus;
import com.nextcloud.talk.models.database.UserEntity;
import com.nextcloud.talk.models.json.generic.GenericOverall;
import com.nextcloud.talk.utils.ApiUtils;
import com.nextcloud.talk.utils.bundle.BundleKeys;
import com.nextcloud.talk.utils.database.user.UserUtils;
import io.reactivex.Observer;
import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;
import okhttp3.JavaNetCookieJar;
import okhttp3.OkHttpClient;
import org.greenrobot.eventbus.EventBus;
import retrofit2.Retrofit;

import javax.inject.Inject;
import java.net.CookieManager;

@AutoInjector(NextcloudTalkApplication.class)
public class LeaveConversationWorker extends Worker {
    @Inject
    Retrofit retrofit;

    @Inject
    OkHttpClient okHttpClient;

    @Inject
    UserUtils userUtils;

    @Inject
    EventBus eventBus;

    NcApi ncApi;

    public LeaveConversationWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
        NextcloudTalkApplication.getSharedApplication().getComponentApplication().inject(this);
    }

    @NonNull
    @Override
    public Result doWork() {
        Data data = getInputData();
        long operationUserId = data.getLong(BundleKeys.KEY_INTERNAL_USER_ID, -1);
        String conversationToken = data.getString(BundleKeys.KEY_ROOM_TOKEN);
        UserEntity operationUser = userUtils.getUserWithId(operationUserId);

        if (operationUser != null) {
            String credentials = ApiUtils.getCredentials(operationUser.getUsername(), operationUser.getToken());
            ncApi = retrofit.newBuilder().client(okHttpClient.newBuilder().cookieJar(new
                    JavaNetCookieJar(new CookieManager())).build()).build().create(NcApi.class);

            EventStatus eventStatus = new EventStatus(operationUser.getId(),
                    EventStatus.EventType.CONVERSATION_UPDATE, true);

            ncApi.removeSelfFromRoom(credentials, ApiUtils.getUrlForRemoveSelfFromRoom(operationUser.getBaseUrl(), conversationToken))
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
