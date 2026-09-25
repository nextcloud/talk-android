/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2022 Andy Scherzinger <info@andy-scherzinger.de>
 * SPDX-FileCopyrightText: 2017-2018 Mario Danic <mario@lovelyhq.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.jobs

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import autodagger.AutoInjector
import com.nextcloud.talk.api.NcApi
import com.nextcloud.talk.application.NextcloudTalkApplication
import com.nextcloud.talk.application.NextcloudTalkApplication.Companion.sharedApplication
import com.nextcloud.talk.conversationlist.DirectShareHelper
import com.nextcloud.talk.events.EventStatus
import com.nextcloud.talk.users.UserManager
import com.nextcloud.talk.utils.ApiUtils
import com.nextcloud.talk.utils.UserIdUtils
import com.nextcloud.talk.utils.bundle.BundleKeys
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.JavaNetCookieJar
import okhttp3.OkHttpClient
import org.greenrobot.eventbus.EventBus
import retrofit2.Retrofit
import java.net.CookieManager
import javax.inject.Inject

@AutoInjector(NextcloudTalkApplication::class)
class DeleteConversationWorker(context: Context, workerParams: WorkerParameters) :
    CoroutineWorker(context, workerParams) {

    @Inject
    lateinit var retrofit: Retrofit

    @Inject
    lateinit var okHttpClient: OkHttpClient

    @Inject
    lateinit var userManager: UserManager

    @Inject
    lateinit var eventBus: EventBus

    init {
        sharedApplication!!.componentApplication.inject(this)
    }

    @Suppress("TooGenericExceptionCaught", "ReturnCount")
    override suspend fun doWork(): Result {
        val operationUserId = inputData.getLong(BundleKeys.KEY_INTERNAL_USER_ID, -1)
        val conversationToken = inputData.getString(BundleKeys.KEY_ROOM_TOKEN)
        val operationUser = userManager.getUserWithId(operationUserId) ?: return Result.success()

        val apiVersion = ApiUtils.getConversationApiVersion(operationUser, intArrayOf(ApiUtils.API_V4, 1))
        val credentials = ApiUtils.getCredentials(operationUser.username, operationUser.token)
        val ncApi = retrofit
            .newBuilder()
            .client(okHttpClient.newBuilder().cookieJar(JavaNetCookieJar(CookieManager())).build())
            .build()
            .create(NcApi::class.java)

        try {
            withContext(Dispatchers.IO) {
                ncApi.deleteRoom(
                    credentials,
                    ApiUtils.getUrlForRoom(apiVersion, operationUser.baseUrl, conversationToken)
                ).blockingFirst()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to delete conversation", e)
            return Result.success()
        }

        eventBus.postSticky(
            EventStatus(
                UserIdUtils.getIdForUser(operationUser),
                EventStatus.EventType.CONVERSATION_UPDATE,
                true
            )
        )
        if (conversationToken != null) {
            DirectShareHelper.removeShortcutForConversation(applicationContext, operationUserId, conversationToken)
        }

        return Result.success()
    }

    companion object {
        private val TAG = DeleteConversationWorker::class.java.simpleName
    }
}
