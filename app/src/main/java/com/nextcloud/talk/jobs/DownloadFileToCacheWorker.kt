/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2022 Andy Scherzinger <info@andy-scherzinger.de>
 * SPDX-FileCopyrightText: 2021 Marcel Hibbe <dev@mhibbe.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.jobs

import android.content.Context
import android.util.Log
import androidx.work.Data
import androidx.work.Worker
import androidx.work.WorkerParameters
import autodagger.AutoInjector
import com.nextcloud.talk.api.NcApi
import com.nextcloud.talk.application.NextcloudTalkApplication
import com.nextcloud.talk.data.user.model.User
import com.nextcloud.talk.users.UserManager
import com.nextcloud.talk.utils.ApiUtils
import com.nextcloud.talk.utils.database.user.CurrentUserProviderNew
import com.nextcloud.talk.utils.preferences.AppPreferences
import okhttp3.ResponseBody
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import javax.inject.Inject

@AutoInjector(NextcloudTalkApplication::class)
class DownloadFileToCacheWorker(val context: Context, workerParameters: WorkerParameters) :
    Worker(context, workerParameters) {

    private var totalFileSize: Long = -1

    @Inject
    lateinit var ncApi: NcApi

    @Inject
    lateinit var userManager: UserManager

    @Inject
    lateinit var currentUserProvider: CurrentUserProviderNew

    @Inject
    lateinit var appPreferences: AppPreferences

    override fun doWork(): Result {
        NextcloudTalkApplication.sharedApplication!!.componentApplication.inject(this)

        if (totalFileSize > -1) {
            setProgressAsync(Data.Builder().putInt(PROGRESS, 0).build())
        }

        try {
            val currentUser = currentUserProvider.currentUser.blockingGet()
            val baseUrl = inputData.getString(KEY_BASE_URL)
            val userId = inputData.getString(KEY_USER_ID)
            val attachmentFolder = inputData.getString(KEY_ATTACHMENT_FOLDER)
            val fileName = inputData.getString(KEY_FILE_NAME)
            val remotePath = inputData.getString(KEY_FILE_PATH)
            totalFileSize = (inputData.getLong(KEY_FILE_SIZE, -1))

            checkNotNull(currentUser)
            checkNotNull(baseUrl)
            checkNotNull(userId)
            checkNotNull(attachmentFolder)
            checkNotNull(fileName)
            checkNotNull(remotePath)

            val url = ApiUtils.getUrlForFileDownload(baseUrl, userId, remotePath)

            return downloadFile(currentUser, url, fileName)
        } catch (e: IllegalStateException) {
            Log.e(javaClass.simpleName, "Something went wrong when trying to download file", e)
            return Result.failure()
        }
    }

    private fun downloadFile(currentUser: User, url: String, fileName: String): Result {
        val downloadCall = ncApi.downloadFile(
            ApiUtils.getCredentials(currentUser.username, currentUser.token),
            url
        )

        return executeDownload(downloadCall.execute().body(), fileName)
    }

    private fun executeDownload(body: ResponseBody?, fileName: String): Result {
        if (body == null) {
            Log.e(TAG, "Response body when downloading $fileName is null!")
            return Result.failure()
        }

        var count: Int
        val data = ByteArray(BYTE_UNIT_DIVIDER * DATA_BYTES)
        val bis: InputStream = BufferedInputStream(body.byteStream(), BYTE_UNIT_DIVIDER * DOWNLOAD_STREAM_SIZE)
        val outputFile = File(context.cacheDir, fileName + "_")
        val output: OutputStream = FileOutputStream(outputFile)
        var total: Long = 0
        val startTime = System.currentTimeMillis()
        var timeCount = 1

        count = bis.read(data)

        while (count != -1) {
            if (totalFileSize > -1) {
                total += count.toLong()
                val progress = (total * COMPLETE_PERCENTAGE / totalFileSize).toInt()
                val currentTime = System.currentTimeMillis() - startTime
                if (currentTime > PROGRESS_THRESHOLD * timeCount) {
                    setProgressAsync(Data.Builder().putInt(PROGRESS, progress).build())
                    timeCount++
                }
            }
            output.write(data, 0, count)
            count = bis.read(data)
        }

        output.flush()
        output.close()
        bis.close()

        return onDownloadComplete(fileName)
    }

    private fun onDownloadComplete(fileName: String): Result {
        val tempFile = File(context.cacheDir, fileName + "_")
        val targetFile = File(context.cacheDir, fileName)

        return if (tempFile.renameTo(targetFile)) {
            setProgressAsync(Data.Builder().putBoolean(SUCCESS, true).build())
            Result.success()
        } else {
            Result.failure()
        }
    }

    companion object {
        const val TAG = "DownloadFileToCache"
        const val KEY_BASE_URL = "KEY_BASE_URL"
        const val KEY_USER_ID = "KEY_USER_ID"
        const val KEY_ATTACHMENT_FOLDER = "KEY_ATTACHMENT_FOLDER"
        const val KEY_FILE_NAME = "KEY_FILE_NAME"
        const val KEY_FILE_PATH = "KEY_FILE_PATH"
        const val KEY_FILE_SIZE = "KEY_FILE_SIZE"
        const val PROGRESS = "PROGRESS"
        const val SUCCESS = "SUCCESS"
        const val BYTE_UNIT_DIVIDER = 1024
        const val DATA_BYTES = 4
        const val DOWNLOAD_STREAM_SIZE = 8
        const val COMPLETE_PERCENTAGE = 100
        const val PROGRESS_THRESHOLD = 50
    }
}
