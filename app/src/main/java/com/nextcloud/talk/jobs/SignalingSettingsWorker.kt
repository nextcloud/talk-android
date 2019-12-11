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
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import autodagger.AutoInjector
import com.nextcloud.talk.api.NcApi
import com.nextcloud.talk.application.NextcloudTalkApplication
import com.nextcloud.talk.application.NextcloudTalkApplication.Companion.sharedApplication
import com.nextcloud.talk.events.EventStatus
import com.nextcloud.talk.jobs.WebsocketConnectionsWorker
import com.nextcloud.talk.models.ExternalSignalingServer
import com.nextcloud.talk.models.json.signaling.settings.SignalingSettingsOverall
import com.nextcloud.talk.newarch.domain.repository.offline.UsersRepository
import com.nextcloud.talk.newarch.local.models.UserNgEntity
import com.nextcloud.talk.newarch.local.models.getCredentials
import com.nextcloud.talk.utils.ApiUtils
import com.nextcloud.talk.utils.bundle.BundleKeys.KEY_INTERNAL_USER_ID
import io.reactivex.Observer
import io.reactivex.disposables.Disposable
import kotlinx.coroutines.runBlocking
import org.greenrobot.eventbus.EventBus
import org.koin.core.KoinComponent
import org.koin.core.inject
import java.util.*
import javax.inject.Inject

class SignalingSettingsWorker(context: Context, workerParams: WorkerParameters) : CoroutineWorker(context, workerParams), KoinComponent {
    val ncApi: NcApi by inject()
    val eventBus: EventBus by inject()
    val usersRepository: UsersRepository by inject()

    override suspend fun doWork(): Result {
        val data = inputData
        val internalUserId = data.getLong(KEY_INTERNAL_USER_ID, -1)
        var userEntityList: MutableList<UserNgEntity?> = ArrayList()
        var userEntity: UserNgEntity?
        if (internalUserId == -1L || usersRepository.getUserWithId(internalUserId) == null) {
            userEntityList = usersRepository.getUsers().toMutableList()
        } else {
            userEntity = usersRepository.getUserWithId(internalUserId)
            userEntityList.add(userEntity)
        }
        for (i in userEntityList.indices) {
            userEntity = userEntityList[i]
            val finalUserEntity: UserNgEntity? = userEntity
            ncApi!!.getSignalingSettings(
                    userEntity!!.getCredentials(),
                    ApiUtils.getUrlForSignalingSettings(userEntity.baseUrl))
                    .blockingSubscribe(object : Observer<SignalingSettingsOverall> {
                        override fun onSubscribe(d: Disposable) {}
                        override fun onNext(signalingSettingsOverall: SignalingSettingsOverall) {
                            val externalSignalingServer: ExternalSignalingServer
                            externalSignalingServer = ExternalSignalingServer()
                            externalSignalingServer.externalSignalingServer = signalingSettingsOverall.ocs.settings.externalSignalingServer
                            externalSignalingServer.externalSignalingTicket = signalingSettingsOverall.ocs.settings.externalSignalingTicket
                            val user = usersRepository.getUserWithId(userEntity.id!!)
                            user.externalSignaling = externalSignalingServer
                            runBlocking {
                                val result = usersRepository.updateUser(user)
                                eventBus.post(EventStatus(user.id!!,
                                        EventStatus.EventType.SIGNALING_SETTINGS, result > 0))
                            }

                        }

                        override fun onError(e: Throwable) {
                            eventBus.post(EventStatus(finalUserEntity!!.id!!,
                                    EventStatus.EventType.SIGNALING_SETTINGS, false))
                        }

                        override fun onComplete() {}
                    })
        }
        val websocketConnectionsWorker = OneTimeWorkRequest.Builder(WebsocketConnectionsWorker::class.java).build()
        WorkManager.getInstance().enqueue(websocketConnectionsWorker)
        return Result.success()
    }
}
