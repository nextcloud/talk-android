/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2017-2019 Mario Danic <mario@lovelyhq.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.jobs;

import android.content.Context;
import android.util.Log;

import com.nextcloud.talk.api.NcApi;
import com.nextcloud.talk.application.NextcloudTalkApplication;
import com.nextcloud.talk.data.user.model.User;
import com.nextcloud.talk.models.RetrofitBucket;
import com.nextcloud.talk.users.UserManager;
import com.nextcloud.talk.utils.ApiUtils;
import com.nextcloud.talk.utils.bundle.BundleKeys;

import org.greenrobot.eventbus.EventBus;

import java.util.ArrayList;
import java.util.List;

import javax.inject.Inject;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.work.Data;
import androidx.work.Worker;
import androidx.work.WorkerParameters;
import autodagger.AutoInjector;
import io.reactivex.schedulers.Schedulers;

@AutoInjector(NextcloudTalkApplication.class)
public class AddParticipantsToConversationWorker extends Worker {
    /**
     * The ids the server refused, in the output data of both a partial success and a failure.
     */
    public static final String KEY_FAILED_PARTICIPANTS = "KEY_FAILED_PARTICIPANTS";

    private static final String TAG = AddParticipantsToConversationWorker.class.getSimpleName();

    @Inject
    NcApi ncApi;

    @Inject
    UserManager userManager;

    @Inject
    EventBus eventBus;

    public AddParticipantsToConversationWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
        NextcloudTalkApplication.Companion.getSharedApplication().getComponentApplication().inject(this);
    }

    @NonNull
    @Override
    public Result doWork() {
        Data data = getInputData();
        String[] selectedUserIds = data.getStringArray(BundleKeys.KEY_SELECTED_USERS);
        String[] selectedGroupIds = data.getStringArray(BundleKeys.KEY_SELECTED_GROUPS);
        String[] selectedCircleIds = data.getStringArray(BundleKeys.KEY_SELECTED_CIRCLES);
        String[] selectedEmails = data.getStringArray(BundleKeys.KEY_SELECTED_EMAILS);
        User user =
            userManager.getUserWithInternalId(
                data.getLong(BundleKeys.KEY_INTERNAL_USER_ID, -1))
                .blockingGet();

        int apiVersion = ApiUtils.getConversationApiVersion(user, new int[] {ApiUtils.API_V4, 1});

        String conversationToken = data.getString(BundleKeys.KEY_TOKEN);
        String credentials = ApiUtils.getCredentials(user.getUsername(), user.getToken());

        List<String> failedParticipants = new ArrayList<>();
        failedParticipants.addAll(
            addParticipants(apiVersion, user, credentials, conversationToken, null, selectedUserIds));
        failedParticipants.addAll(
            addParticipants(apiVersion, user, credentials, conversationToken, "groups", selectedGroupIds));
        failedParticipants.addAll(
            addParticipants(apiVersion, user, credentials, conversationToken, "circles", selectedCircleIds));
        failedParticipants.addAll(
            addParticipants(apiVersion, user, credentials, conversationToken, "emails", selectedEmails));

        if (failedParticipants.isEmpty()) {
            return Result.success();
        }

        Data output = new Data.Builder()
            .putStringArray(KEY_FAILED_PARTICIPANTS, failedParticipants.toArray(new String[0]))
            .build();
        int requested = count(selectedUserIds, selectedGroupIds, selectedCircleIds, selectedEmails);
        return failedParticipants.size() == requested ? Result.failure(output) : Result.success(output);
    }

    /**
     * Invites every given id, one request each, and returns those the server refused.
     */
    private List<String> addParticipants(int apiVersion,
                                         User user,
                                         String credentials,
                                         String conversationToken,
                                         @Nullable String source,
                                         @Nullable String[] ids) {
        List<String> failed = new ArrayList<>();
        if (ids == null) {
            return failed;
        }

        for (String id : ids) {
            RetrofitBucket retrofitBucket = source == null
                ? ApiUtils.getRetrofitBucketForAddParticipant(apiVersion, user.getBaseUrl(), conversationToken, id)
                : ApiUtils.getRetrofitBucketForAddParticipantWithSource(apiVersion,
                                                                        user.getBaseUrl(),
                                                                        conversationToken,
                                                                        source,
                                                                        id);
            try {
                ncApi.addParticipant(credentials, retrofitBucket.getUrl(), retrofitBucket.getQueryMap())
                    .subscribeOn(Schedulers.io())
                    .blockingSubscribe();
            } catch (RuntimeException e) {
                Log.w(TAG, "Adding a participant of source " + (source == null ? "users" : source) + " failed", e);
                failed.add(id);
            }
        }
        return failed;
    }

    private static int count(String[]... groups) {
        int total = 0;
        for (String[] group : groups) {
            total += group == null ? 0 : group.length;
        }
        return total;
    }
}
