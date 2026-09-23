/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2022 Andy Scherzinger <info@andy-scherzinger.de>
 * SPDX-FileCopyrightText: 2017 Mario Danic <mario@lovelyhq.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.jobs

import android.app.NotificationManager
import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import autodagger.AutoInjector
import com.nextcloud.talk.R
import com.nextcloud.talk.api.NcApi
import com.nextcloud.talk.application.NextcloudTalkApplication
import com.nextcloud.talk.application.NextcloudTalkApplication.Companion.sharedApplication
import com.nextcloud.talk.arbitrarystorage.ArbitraryStorageManager
import com.nextcloud.talk.conversationlist.DirectShareHelper
import com.nextcloud.talk.data.database.dao.ChatBlocksDao
import com.nextcloud.talk.data.database.dao.ChatMessagesDao
import com.nextcloud.talk.data.database.dao.ConversationsDao
import com.nextcloud.talk.data.user.model.User
import com.nextcloud.talk.logger.Logger
import com.nextcloud.talk.models.json.push.PushConfigurationState
import com.nextcloud.talk.users.UserManager
import com.nextcloud.talk.utils.ApiUtils
import com.nextcloud.talk.utils.preferences.AppPreferences
import com.nextcloud.talk.webrtc.WebSocketConnectionHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.JavaNetCookieJar
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import java.net.CookieManager
import java.util.zip.CRC32
import javax.inject.Inject

@AutoInjector(NextcloudTalkApplication::class)
class AccountRemovalWorker(context: Context, workerParams: WorkerParameters) : CoroutineWorker(context, workerParams) {

    @Inject
    lateinit var userManager: UserManager

    @Inject
    lateinit var arbitraryStorageManager: ArbitraryStorageManager

    @Inject
    lateinit var appPreferences: AppPreferences

    @Inject
    lateinit var retrofit: Retrofit

    @Inject
    lateinit var okHttpClient: OkHttpClient

    @Inject
    lateinit var chatMessagesDao: ChatMessagesDao

    @Inject
    lateinit var conversationsDao: ConversationsDao

    @Inject
    lateinit var chatBlocksDao: ChatBlocksDao

    @Inject
    lateinit var logger: Logger

    private lateinit var ncApi: NcApi

    override suspend fun doWork(): Result {
        sharedApplication!!.componentApplication.inject(this)

        ncApi = retrofit
            .newBuilder()
            .client(
                okHttpClient
                    .newBuilder()
                    .cookieJar(JavaNetCookieJar(CookieManager()))
                    .build()
            )
            .build()
            .create(NcApi::class.java)

        withContext(Dispatchers.IO) {
            for (user in userManager.getUsersScheduledForDeletion()) {
                val pushConfigurationState = user.pushConfigurationState
                if (pushConfigurationState != null) {
                    unregisterDeviceForNotifications(user, pushConfigurationState)
                } else {
                    initiateUserDeletion(user)
                }
            }
        }

        return Result.success()
    }

    @Suppress("TooGenericExceptionCaught")
    private suspend fun unregisterDeviceForNotifications(user: User, pushConfigurationState: PushConfigurationState) {
        val genericOverall = try {
            ncApi.unregisterDeviceForNotificationsWithNextcloud(
                ApiUtils.getCredentials(user.username, user.token),
                ApiUtils.getUrlNextcloudPush(user.baseUrl!!)
            ).blockingFirst()
        } catch (e: Exception) {
            Log.e(TAG, "error while trying to unregister Device For Notifications", e)
            initiateUserDeletion(user)
            return
        }

        val statusCode = genericOverall.ocs!!.meta!!.statusCode
        if (statusCode == HTTP_OK || statusCode == HTTP_ACCEPTED) {
            val queryMap = hashMapOf(
                "deviceIdentifier" to pushConfigurationState.deviceIdentifier,
                "userPublicKey" to pushConfigurationState.userPublicKey,
                "deviceIdentifierSignature" to pushConfigurationState.deviceIdentifierSignature
            )
            unregisterDeviceForNotificationWithProxy(queryMap, user)
        }
    }

    @Suppress("TooGenericExceptionCaught")
    private suspend fun unregisterDeviceForNotificationWithProxy(queryMap: HashMap<String, String?>, user: User) {
        try {
            ncApi.unregisterDeviceForNotificationsWithProxy(ApiUtils.getUrlPushProxy(), queryMap)
                .ignoreElements()
                .blockingAwait()

            val groupName = String.format(
                applicationContext.resources.getString(R.string.nc_notification_channel),
                user.userId,
                user.baseUrl
            )
            val crc32 = CRC32()
            crc32.update(groupName.toByteArray())
            val notificationManager =
                applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager?
            notificationManager?.deleteNotificationChannelGroup(crc32.value.toString())
        } catch (e: Exception) {
            Log.e(TAG, "error while trying to unregister Device For Notification With Proxy", e)
        }
        initiateUserDeletion(user)
    }

    @Suppress("TooGenericExceptionCaught")
    private suspend fun initiateUserDeletion(user: User) {
        val id = user.id ?: return
        DirectShareHelper.removeShortcutsForUser(applicationContext, id)
        WebSocketConnectionHelper.deleteExternalSignalingInstanceForUserEntity(id)

        try {
            arbitraryStorageManager.deleteAllEntriesForAccountIdentifier(id)
            deleteUser(user)
        } catch (e: Exception) {
            Log.e(TAG, "error while trying to delete All Entries For Account Identifier", e)
        }
    }

    @Suppress("TooGenericExceptionCaught")
    private suspend fun deleteUser(user: User) {
        val id = user.id ?: return
        try {
            userManager.deleteUser(id)
            user.username?.let { Log.d(TAG, "deleted user: $it") }
        } catch (e: Exception) {
            Log.e(TAG, "error while trying to delete user", e)
        }
    }

    companion object {
        const val TAG = "AccountRemovalWorker"
        private const val HTTP_OK = 200
        private const val HTTP_ACCEPTED = 202
    }
}
