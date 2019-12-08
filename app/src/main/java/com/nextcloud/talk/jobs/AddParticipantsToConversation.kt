/*
 * Nextcloud Talk application
 *
 * @author Mario Danic
 * Copyright (C) 2017-2019 Mario Danic <mario@lovelyhq.com>
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
package com.nextcloud.talk.jobs

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.Worker
import androidx.work.WorkerParameters
import autodagger.AutoInjector
import com.nextcloud.talk.api.NcApi
import com.nextcloud.talk.application.NextcloudTalkApplication
import com.nextcloud.talk.application.NextcloudTalkApplication.Companion.sharedApplication
import com.nextcloud.talk.events.EventStatus
import com.nextcloud.talk.models.RetrofitBucket
import com.nextcloud.talk.newarch.domain.repository.offline.UsersRepository
import com.nextcloud.talk.utils.ApiUtils
import com.nextcloud.talk.utils.bundle.BundleKeys.KEY_INTERNAL_USER_ID
import com.nextcloud.talk.utils.bundle.BundleKeys.KEY_SELECTED_GROUPS
import com.nextcloud.talk.utils.bundle.BundleKeys.KEY_SELECTED_USERS
import com.nextcloud.talk.utils.bundle.BundleKeys.KEY_TOKEN
import com.nextcloud.talk.utils.database.user.UserUtils
import io.reactivex.schedulers.Schedulers
import org.greenrobot.eventbus.EventBus
import org.koin.core.KoinComponent
import org.koin.core.inject
import javax.inject.Inject

@AutoInjector(NextcloudTalkApplication::class)
class AddParticipantsToConversation(context: Context,
                                    workerParams: WorkerParameters) : CoroutineWorker(context, workerParams), KoinComponent {
    @JvmField
    @Inject
    var ncApi: NcApi? = null

    val eventBus: EventBus by inject()
    val usersRepository: UsersRepository by inject()

    override suspend fun doWork(): Result {
        val data = inputData
        val selectedUserIds = data.getStringArray(KEY_SELECTED_USERS)
        val selectedGroupIds = data.getStringArray(KEY_SELECTED_GROUPS)
        val user = usersRepository.getUserWithId(data.getLong(KEY_INTERNAL_USER_ID, -1))
        val conversationToken = data.getString(KEY_TOKEN)
        val credentials = ApiUtils.getCredentials(user.username, user.token)
        var retrofitBucket: RetrofitBucket
        for (userId in selectedUserIds!!) {
            retrofitBucket = ApiUtils.getRetrofitBucketForAddParticipant(user.baseUrl, conversationToken,
                    userId)
            ncApi!!.addParticipant(credentials, retrofitBucket.url, retrofitBucket.queryMap)
                    .subscribeOn(Schedulers.io())
                    .blockingSubscribe()
        }
        for (groupId in selectedGroupIds!!) {
            retrofitBucket = ApiUtils.getRetrofitBucketForAddGroupParticipant(user.baseUrl, conversationToken,
                    groupId)
            ncApi!!.addParticipant(credentials, retrofitBucket.url, retrofitBucket.queryMap)
                    .subscribeOn(Schedulers.io())
                    .blockingSubscribe()
        }
        eventBus.post(EventStatus(user.id, EventStatus.EventType.PARTICIPANTS_UPDATE, true))
        return Result.success()
    }

    init {
        sharedApplication
                ?.componentApplication
                ?.inject(this)
    }
}