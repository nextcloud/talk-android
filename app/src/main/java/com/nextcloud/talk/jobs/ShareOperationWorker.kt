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
package com.nextcloud.talk.jobs

import android.content.Context
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.nextcloud.talk.api.NcApi
import com.nextcloud.talk.newarch.domain.repository.offline.UsersRepository
import com.nextcloud.talk.newarch.local.models.UserNgEntity
import com.nextcloud.talk.newarch.local.models.getCredentials
import com.nextcloud.talk.utils.ApiUtils
import com.nextcloud.talk.utils.bundle.BundleKeys.KEY_CONVERSATION_TOKEN
import com.nextcloud.talk.utils.bundle.BundleKeys.KEY_FILE_PATHS
import com.nextcloud.talk.utils.bundle.BundleKeys.KEY_INTERNAL_USER_ID
import io.reactivex.Observer
import io.reactivex.disposables.Disposable
import io.reactivex.schedulers.Schedulers
import org.koin.core.KoinComponent
import org.koin.core.inject
import java.util.*

class ShareOperationWorker(
        context: Context,
        workerParams: WorkerParameters
) : Worker(context, workerParams), KoinComponent {
    val ncApi: NcApi by inject()
    val usersRepository: UsersRepository by inject()

    private val userId: Long
    private val operationsUser: UserNgEntity
    private val roomToken: String?
    private val filesArray = mutableListOf<String>()
    private val credentials: String
    private val baseUrl: String
    override fun doWork(): Result {
        for (i in filesArray.indices) {
            ncApi.createRemoteShare(
                            credentials,
                            ApiUtils.getSharingUrl(baseUrl),
                            filesArray[i],
                            roomToken,
                            "10"
                    )
                    .subscribeOn(Schedulers.io())
                    .blockingSubscribe(object : Observer<Void?> {
                        override fun onSubscribe(d: Disposable) {}
                        override fun onError(e: Throwable) {}
                        override fun onComplete() {}
                        override fun onNext(t: Void) {
                        }
                    })
        }
        return Result.success()
    }

    init {
        val data = workerParams.inputData
        userId = data.getLong(KEY_INTERNAL_USER_ID, 0)
        roomToken = data.getString(KEY_CONVERSATION_TOKEN)

        Collections.addAll(
                filesArray, *data.getStringArray(KEY_FILE_PATHS)
        )
        operationsUser = usersRepository.getUserWithId(userId)
        credentials = operationsUser.getCredentials()
        baseUrl = operationsUser.baseUrl
    }
}