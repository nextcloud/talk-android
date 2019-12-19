/*
 * Nextcloud Talk application
 *
 * @author Mario Danic
 * Copyright (C) 2017 Mario Danic <mario@lovelyhq.com>
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

import android.annotation.SuppressLint
import android.app.Application
import android.content.Context
import android.text.TextUtils
import android.util.Base64
import androidx.work.CoroutineWorker
import androidx.work.ListenableWorker.Result
import androidx.work.WorkerParameters
import com.nextcloud.talk.R
import com.nextcloud.talk.api.NcApi
import com.nextcloud.talk.events.EventStatus
import com.nextcloud.talk.models.json.push.PushConfigurationState
import com.nextcloud.talk.models.json.push.PushRegistrationOverall
import com.nextcloud.talk.newarch.domain.repository.offline.UsersRepository
import com.nextcloud.talk.newarch.local.models.getCredentials
import com.nextcloud.talk.newarch.utils.hashWithAlgorithm
import com.nextcloud.talk.utils.ApiUtils
import com.nextcloud.talk.utils.PushUtils
import com.nextcloud.talk.utils.preferences.AppPreferences
import io.reactivex.Observer
import io.reactivex.disposables.Disposable
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import org.greenrobot.eventbus.EventBus
import org.koin.core.KoinComponent
import org.koin.core.inject
import java.security.PublicKey
import java.util.*

class PushRegistrationWorker(
        context: Context,
        workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams), KoinComponent {

    val usersRepository: UsersRepository by inject()
    val eventBus: EventBus by inject()
    val appPreferences: AppPreferences by inject()
    val application: Application by inject()
    val ncApi: NcApi by inject()

    override suspend fun doWork(): Result {
        val pushUtils = PushUtils(usersRepository)
        pushUtils.generateRsa2048KeyPair()
        pushRegistrationToServer()
        return Result.success()
    }

    private fun pushRegistrationToServer() {
        val token: String = appPreferences.pushToken
        if (!TextUtils.isEmpty(token)) {
            var credentials: String
            val pushUtils = PushUtils(usersRepository)
            val pushTokenHash = token.hashWithAlgorithm("SHA-512")
            val devicePublicKey = pushUtils.readKeyFromFile(true) as PublicKey?
            if (devicePublicKey != null) {
                val publicKeyBytes: ByteArray? =
                        Base64.encode(devicePublicKey.encoded, Base64.NO_WRAP)
                var publicKey = String(publicKeyBytes!!)
                publicKey = publicKey.replace("(.{64})".toRegex(), "$1\n")
                publicKey = "-----BEGIN PUBLIC KEY-----\n$publicKey\n-----END PUBLIC KEY-----\n"
                val users = usersRepository.getUsers()
                if (users.count() > 0) {
                    var accountPushData: PushConfigurationState?
                    for (userEntityObject in users) {
                        accountPushData = userEntityObject.pushConfiguration
                        if (accountPushData == null || accountPushData.pushToken != token) {
                            val queryMap: MutableMap<String, String> =
                                    HashMap()
                            queryMap["format"] = "json"
                            queryMap["pushTokenHash"] = pushTokenHash
                            queryMap["devicePublicKey"] = publicKey
                            queryMap["proxyServer"] = application.getString(R.string.nc_push_server_url)
                            credentials = userEntityObject.getCredentials()
                            ncApi.registerDeviceForNotificationsWithNextcloud(
                                    credentials,
                                    ApiUtils.getUrlNextcloudPush(userEntityObject.baseUrl),
                                    queryMap
                            )
                                    .blockingSubscribe(object : Observer<PushRegistrationOverall> {
                                        override fun onSubscribe(d: Disposable) {}
                                        @SuppressLint("CheckResult")
                                        override fun onNext(pushRegistrationOverall: PushRegistrationOverall) {
                                            val proxyMap: MutableMap<String, String> =
                                                    HashMap()
                                            proxyMap["pushToken"] = token
                                            proxyMap["deviceIdentifier"] =
                                                    pushRegistrationOverall.ocs.data.deviceIdentifier
                                            proxyMap["deviceIdentifierSignature"] = pushRegistrationOverall.ocs
                                                    .data.signature
                                            proxyMap["userPublicKey"] = pushRegistrationOverall.ocs
                                                    .data.publicKey
                                            ncApi.registerDeviceForNotificationsWithProxy(
                                                    ApiUtils.getUrlPushProxy(), proxyMap
                                            ).subscribe({
                                                val pushConfigurationState = PushConfigurationState()
                                                pushConfigurationState.pushToken = token
                                                pushConfigurationState.deviceIdentifier = proxyMap["deviceIdentifier"]
                                                pushConfigurationState.deviceIdentifierSignature = proxyMap["deviceIdentifierSignature"]
                                                pushConfigurationState.userPublicKey = proxyMap["userPublicKey"]
                                                pushConfigurationState.usesRegularPass = false
                                                GlobalScope.launch {
                                                    val user = usersRepository.getUserWithId(userEntityObject.id!!)
                                                    user.pushConfiguration = pushConfigurationState
                                                    usersRepository.updateUser(user)
                                                }

                                                eventBus.post(
                                                        EventStatus(
                                                                userEntityObject.id!!,
                                                                EventStatus.EventType.PUSH_REGISTRATION,
                                                                true
                                                        )
                                                )
                                            }, {
                                                eventBus.post(
                                                        EventStatus(
                                                                userEntityObject.id!!,
                                                                EventStatus.EventType.PUSH_REGISTRATION,
                                                                false))

                                            })
                                        }

                                        override fun onError(e: Throwable) {
                                            eventBus.post(
                                                    EventStatus(
                                                            userEntityObject.id!!,
                                                            EventStatus.EventType.PUSH_REGISTRATION,
                                                            false
                                                    )
                                            )
                                        }

                                        override fun onComplete() {}
                                    })
                        }
                    }
                }
            }
        }
    }

    companion object {
        const val TAG = "PushRegistrationWorker"
    }
}