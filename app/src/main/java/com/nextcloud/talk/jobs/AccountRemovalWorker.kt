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

import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.nextcloud.talk.R
import com.nextcloud.talk.api.NcApi
import com.nextcloud.talk.models.json.generic.GenericOverall
import com.nextcloud.talk.newarch.domain.repository.offline.UsersRepository
import com.nextcloud.talk.newarch.local.dao.UsersDao
import com.nextcloud.talk.newarch.local.models.UserNgEntity
import com.nextcloud.talk.newarch.local.models.getCredentials
import com.nextcloud.talk.utils.ApiUtils
import io.reactivex.Observer
import io.reactivex.disposables.Disposable
import kotlinx.coroutines.runBlocking
import okhttp3.JavaNetCookieJar
import okhttp3.OkHttpClient
import org.koin.core.KoinComponent
import org.koin.core.inject
import retrofit2.Retrofit
import java.net.CookieManager
import java.util.zip.CRC32

class AccountRemovalWorker(context: Context, workerParams: WorkerParameters) : CoroutineWorker(context, workerParams), KoinComponent {
    val okHttpClient: OkHttpClient by inject()
    val retrofit: Retrofit by inject()
    private val usersDao: UsersDao by inject()
    val usersRepository: UsersRepository by inject()
    var ncApi: NcApi? = null

    override suspend fun doWork(): Result {
        for (userEntityObject in usersDao.getUsersScheduledForDeletion()) {
            val userEntity: UserNgEntity = userEntityObject
            val credentials = userEntity.getCredentials()
            userEntity.pushConfiguration?.let {
                ncApi = retrofit.newBuilder().client(okHttpClient.newBuilder().cookieJar(JavaNetCookieJar(CookieManager())).build()).build().create(NcApi::class.java)
                ncApi!!.unregisterDeviceForNotificationsWithNextcloud(credentials,
                        ApiUtils.getUrlNextcloudPush(userEntity.baseUrl))
                        .blockingSubscribe(object : Observer<GenericOverall> {
                            override fun onSubscribe(d: Disposable) {}
                            override fun onNext(genericOverall: GenericOverall) {
                                if (genericOverall.ocs.meta.statusCode == 200
                                        || genericOverall.ocs.meta.statusCode == 202) {
                                    val queryMap = HashMap<String, String?>()
                                    queryMap["deviceIdentifier"] = userEntity.pushConfiguration!!.deviceIdentifier
                                    queryMap["userPublicKey"] = userEntity.pushConfiguration!!.userPublicKey
                                    queryMap["deviceIdentifierSignature"] = userEntity.pushConfiguration!!.deviceIdentifierSignature

                                    unregisterWithProxy(queryMap, userEntity)
                                }
                            }

                            override fun onError(e: Throwable) {}
                            override fun onComplete() {}
                        })
            } ?: run {
                runBlocking {
                    usersRepository.deleteUserWithId(userEntity.id!!)
                }
            }
        }
        return Result.success()
    }

    private fun unregisterWithProxy(queryMap: HashMap<String, String?>, userEntity: UserNgEntity) {
        val notificationManager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        ncApi!!.unregisterDeviceForNotificationsWithProxy(ApiUtils.getUrlPushProxy(), queryMap)
                .doOnComplete {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        val groupName = java.lang.String.format(applicationContext.resources
                                .getString(R.string.nc_notification_channel), userEntity.userId,
                                userEntity.baseUrl)
                        val crc32 = CRC32()
                        crc32.update(groupName.toByteArray())
                        notificationManager.deleteNotificationChannelGroup(crc32.value.toString())
                    }

                    //deleteExternalSignalingInstanceForUserEntity(
                    //        userEntity.id!!)
                    runBlocking {
                        usersRepository.deleteUserWithId(userEntity.id!!)
                    }

                }
                .blockingSubscribe()
    }

    companion object {
        const val TAG = "AccountRemovalWorker"
    }
}