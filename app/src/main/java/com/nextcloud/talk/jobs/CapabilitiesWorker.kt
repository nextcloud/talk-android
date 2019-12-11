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
import autodagger.AutoInjector
import com.nextcloud.talk.api.NcApi
import com.nextcloud.talk.application.NextcloudTalkApplication
import com.nextcloud.talk.events.EventStatus
import com.nextcloud.talk.models.json.capabilities.CapabilitiesOverall
import com.nextcloud.talk.newarch.domain.repository.offline.UsersRepository
import com.nextcloud.talk.newarch.local.models.UserNgEntity
import com.nextcloud.talk.utils.ApiUtils
import com.nextcloud.talk.utils.bundle.BundleKeys.KEY_INTERNAL_USER_ID
import io.reactivex.Observer
import io.reactivex.disposables.Disposable
import kotlinx.coroutines.runBlocking
import okhttp3.JavaNetCookieJar
import okhttp3.OkHttpClient
import org.greenrobot.eventbus.EventBus
import org.koin.core.KoinComponent
import org.koin.core.inject
import retrofit2.Retrofit
import java.net.CookieManager
import java.util.*

@AutoInjector(NextcloudTalkApplication::class)
class CapabilitiesWorker(context: Context, workerParams: WorkerParameters) : CoroutineWorker(context, workerParams), KoinComponent {
    val retrofit: Retrofit by inject()
    val eventBus: EventBus by inject()
    val okHttpClient: OkHttpClient by inject()
    val usersRepository: UsersRepository by inject()
    var ncApi: NcApi? = null

    private fun updateUser(capabilitiesOverall: CapabilitiesOverall, internalUserEntity: UserNgEntity) {
        internalUserEntity.capabilities = capabilitiesOverall.ocs.data.capabilities
        runBlocking {
            val result = usersRepository.updateUser(internalUserEntity)
            eventBus.post(EventStatus(internalUserEntity.id!!,
                    EventStatus.EventType.CAPABILITIES_FETCH, result > 0))
        }

    }

    override suspend fun doWork(): Result {
        val data = inputData
        val internalUserId = data.getLong(KEY_INTERNAL_USER_ID, -1)
        val userEntity: UserNgEntity?
        var userEntityObjectList: MutableList<UserNgEntity> = ArrayList()
        if (internalUserId == -1L || usersRepository.getUserWithId(internalUserId) == null) {
            userEntityObjectList = usersRepository.getUsers().toMutableList()
        } else {
            userEntity = usersRepository.getUserWithId(internalUserId)
            userEntityObjectList.add(userEntity)
        }

        for (userEntityObject in userEntityObjectList) {
            ncApi = retrofit.newBuilder().client(okHttpClient.newBuilder().cookieJar(JavaNetCookieJar(CookieManager())).build()).build().create(NcApi::class.java)
            ncApi!!.getCapabilities(ApiUtils.getCredentials(userEntityObject.username,
                    userEntityObject.token),
                    ApiUtils.getUrlForCapabilities(userEntityObject.baseUrl))
                    .retry(3)
                    .blockingSubscribe(object : Observer<CapabilitiesOverall> {
                        override fun onSubscribe(d: Disposable) {}
                        override fun onNext(capabilitiesOverall: CapabilitiesOverall) {
                            updateUser(capabilitiesOverall, userEntityObject)
                        }

                        override fun onError(e: Throwable) {
                            eventBus.post(EventStatus(userEntityObject.id!!,
                                    EventStatus.EventType.CAPABILITIES_FETCH, false))
                        }

                        override fun onComplete() {}
                    })
        }
        return Result.success()
    }

    companion object {
        const val TAG = "CapabilitiesWorker"
    }
}