/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2022 Marcel Hibbe <dev@mhibbe.de>
 * SPDX-FileCopyrightText: 2017-2018 Mario Danic <mario@lovelyhq.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.jobs

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.OneTimeWorkRequest
import com.nextcloud.talk.utils.setExpeditedIfSupported
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import autodagger.AutoInjector
import com.nextcloud.talk.api.NcApi
import com.nextcloud.talk.application.NextcloudTalkApplication
import com.nextcloud.talk.application.NextcloudTalkApplication.Companion.sharedApplication
import com.nextcloud.talk.data.user.model.User
import com.nextcloud.talk.users.UserManager
import com.nextcloud.talk.utils.ApiUtils
import com.nextcloud.talk.utils.bundle.BundleKeys.KEY_FILE_PATHS
import com.nextcloud.talk.utils.bundle.BundleKeys.KEY_INTERNAL_USER_ID
import com.nextcloud.talk.utils.bundle.BundleKeys.KEY_META_DATA
import com.nextcloud.talk.utils.bundle.BundleKeys.KEY_ROOM_TOKEN
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.withContext
import retrofit2.HttpException
import javax.inject.Inject

@AutoInjector(NextcloudTalkApplication::class)
class ShareOperationWorker(context: Context, workerParams: WorkerParameters) :
    CoroutineWorker(context, workerParams) {

    @Inject
    lateinit var userManager: UserManager

    @Inject
    lateinit var ncApi: NcApi

    private var userId: Long = 0
    private var roomToken: String? = null
    private val filesArray: MutableList<String?> = ArrayList()
    private lateinit var credentials: String
    private var baseUrl: String? = null
    private var metaData: String? = null

    override suspend fun doWork(): Result {
        sharedApplication!!.componentApplication.inject(this)
        userId = inputData.getLong(KEY_INTERNAL_USER_ID, 0)
        roomToken = inputData.getString(KEY_ROOM_TOKEN)
        metaData = inputData.getString(KEY_META_DATA)
        inputData.getStringArray(KEY_FILE_PATHS)?.let { filesArray.addAll(it.toList()) }

        val operationsUser = userManager.getUserWithId(userId)!!
        baseUrl = operationsUser.baseUrl
        credentials = ApiUtils.getCredentials(operationsUser.username, operationsUser.token)!!

        withContext(Dispatchers.IO) {
            for (filePath in filesArray) {
                tryCreateShare(filePath)
            }
        }
        roomToken?.let { _shareCompletedFlow.tryEmit(it) }
        return Result.success()
    }

    @Suppress("TooGenericExceptionCaught")
    private suspend fun tryCreateShare(filePath: String?) {
        for (attempt in 1..SHARE_MAX_ATTEMPTS) {
            var succeeded = false
            var shouldRetry = false
            ncApi.createRemoteShare(
                credentials,
                ApiUtils.getSharingUrl(baseUrl!!),
                filePath,
                roomToken,
                "10",
                metaData,
                "" // no reference id
            )
                .blockingSubscribe(
                    { succeeded = true },
                    { e ->
                        if (e is HttpException && e.code() == HTTP_NOT_FOUND && attempt < SHARE_MAX_ATTEMPTS) {
                            shouldRetry = true
                        } else {
                            Log.w(TAG, "error while creating RemoteShare", e)
                        }
                    }
                )
            if (succeeded || !shouldRetry) return
            delay(SHARE_RETRY_DELAY_MS)
        }
    }

    companion object {
        private val TAG = ShareOperationWorker::class.simpleName
        private const val HTTP_NOT_FOUND = 404
        private const val SHARE_MAX_ATTEMPTS = 4
        private const val SHARE_RETRY_DELAY_MS = 2000L

        private val _shareCompletedFlow: MutableSharedFlow<String> = MutableSharedFlow(extraBufferCapacity = 1)
        val shareCompletedFlow: SharedFlow<String> = _shareCompletedFlow

        fun shareFile(roomToken: String?, currentUser: User, remotePath: String, metaData: String?) {
            val paths: MutableList<String> = ArrayList()
            paths.add(remotePath)

            val data = Data.Builder()
                .putLong(KEY_INTERNAL_USER_ID, currentUser.id!!)
                .putString(KEY_ROOM_TOKEN, roomToken)
                .putStringArray(KEY_FILE_PATHS, paths.toTypedArray())
                .putString(KEY_META_DATA, metaData)
                .build()
            val shareWorker = OneTimeWorkRequest.Builder(ShareOperationWorker::class.java)
                .setInputData(data)
                .setExpeditedIfSupported()
                .build()
            WorkManager.getInstance().enqueue(shareWorker)
        }
    }
}
