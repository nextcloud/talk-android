/*
 * Nextcloud Talk application
 *
 * @author Marcel Hibbe
 * @author Mario Danic
 * Copyright (C) 2022 Marcel Hibbe <dev@mhibbe.de>
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
import android.util.Log
import androidx.work.Data
import androidx.work.OneTimeWorkRequest
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import androidx.work.Worker
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
import io.reactivex.schedulers.Schedulers
import javax.inject.Inject

@AutoInjector(NextcloudTalkApplication::class)
class ShareOperationWorker(context: Context, workerParams: WorkerParameters) : Worker(context, workerParams) {

    @Inject
    lateinit var userManager: UserManager

    @Inject
    lateinit var ncApi: NcApi

    private val userId: Long
    private val roomToken: String?
    private val filesArray: MutableList<String?> = ArrayList()
    private val credentials: String
    private val baseUrl: String?
    private val metaData: String?

    override fun doWork(): Result {
        for (filePath in filesArray) {
            ncApi.createRemoteShare(
                credentials,
                ApiUtils.getSharingUrl(baseUrl),
                filePath,
                roomToken,
                "10",
                metaData
            )
                .subscribeOn(Schedulers.io())
                .blockingSubscribe(
                    {},
                    { e -> Log.w(TAG, "error while creating RemoteShare", e) }
                )
        }
        return Result.success()
    }

    init {
        sharedApplication!!.componentApplication.inject(this)
        val data = workerParams.inputData
        userId = data.getLong(KEY_INTERNAL_USER_ID, 0)
        roomToken = data.getString(KEY_ROOM_TOKEN)
        metaData = data.getString(KEY_META_DATA)
        data.getStringArray(KEY_FILE_PATHS)?.let { filesArray.addAll(it.toList()) }

        val operationsUser = userManager.getUserWithId(userId).blockingGet()
        baseUrl = operationsUser.baseUrl
        credentials = ApiUtils.getCredentials(operationsUser.username, operationsUser.token)
    }

    companion object {
        private val TAG = ShareOperationWorker::class.simpleName

        fun shareFile(
            roomToken: String?,
            currentUser: User,
            remotePath: String,
            metaData: String?
        ) {
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
                .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                .build()
            WorkManager.getInstance().enqueue(shareWorker)
        }
    }
}
