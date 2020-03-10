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
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.nextcloud.talk.api.NcApi
import com.nextcloud.talk.events.EventStatus
import com.nextcloud.talk.models.json.generic.GenericOverall
import com.nextcloud.talk.newarch.domain.repository.offline.UsersRepository
import com.nextcloud.talk.newarch.local.models.UserNgEntity
import com.nextcloud.talk.newarch.local.models.getCredentials
import com.nextcloud.talk.utils.ApiUtils
import com.nextcloud.talk.utils.bundle.BundleKeys.KEY_CONVERSATION_TOKEN
import com.nextcloud.talk.utils.bundle.BundleKeys.KEY_INTERNAL_USER_ID
import io.reactivex.Observer
import io.reactivex.disposables.Disposable
import io.reactivex.schedulers.Schedulers
import okhttp3.JavaNetCookieJar
import okhttp3.OkHttpClient
import org.greenrobot.eventbus.EventBus
import org.koin.core.KoinComponent
import org.koin.core.inject
import retrofit2.Retrofit
import java.net.CookieManager

class DeleteConversationWorker(context: Context,
                               workerParams: WorkerParameters) : CoroutineWorker(context, workerParams), KoinComponent {
    val retrofit: Retrofit by inject()
    val okHttpClient: OkHttpClient by inject()
    val eventBus: EventBus by inject()
    val usersRepository: UsersRepository by inject()

    var ncApi: NcApi? = null
    override suspend fun doWork(): Result {
        val data = inputData
        val operationUserId = data.getLong(KEY_INTERNAL_USER_ID, -1)
        val conversationToken = data.getString(KEY_CONVERSATION_TOKEN)
        val operationUser: UserNgEntity? = usersRepository.getUserWithId(operationUserId)
        operationUser?.let {
            val credentials = it.getCredentials()
            ncApi = retrofit.newBuilder().client(okHttpClient.newBuilder().cookieJar(JavaNetCookieJar(CookieManager())).build()).build().create(NcApi::class.java)
            val eventStatus = EventStatus(it.id,
                    EventStatus.EventType.CONVERSATION_UPDATE, true)
            ncApi!!.deleteRoom(credentials, ApiUtils.getRoom(it.baseUrl, conversationToken))
                    .subscribeOn(Schedulers.io())
                    .blockingSubscribe(object : Observer<GenericOverall?> {
                        var disposable: Disposable? = null
                        override fun onSubscribe(d: Disposable) {
                            disposable = d
                        }

                        override fun onNext(genericOverall: GenericOverall) {
                            eventBus.postSticky(eventStatus)
                        }

                        override fun onError(e: Throwable) {}
                        override fun onComplete() {
                            disposable!!.dispose()
                        }
                    })
        }
        return Result.success()
    }
}
