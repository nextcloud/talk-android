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
import com.nextcloud.talk.mediaviewer.stream.ProgressiveVideoFile
import com.nextcloud.talk.mediaviewer.stream.VideoDownloadRegistry
import com.nextcloud.talk.utils.ApiUtils
import com.nextcloud.talk.utils.FileUtils
import com.nextcloud.talk.utils.bundle.BundleKeys.KEY_INTERNAL_USER_ID
import com.nextcloud.talk.utils.preferences.AppPreferences
import kotlinx.coroutines.runBlocking
import okhttp3.ResponseBody
import retrofit2.Call
import java.io.IOException
import javax.inject.Inject

@AutoInjector(NextcloudTalkApplication::class)
class DownloadFileToCacheWorker(val context: Context, workerParameters: WorkerParameters) :
    Worker(context, workerParameters) {

    private var totalFileSize: Long = -1
    private var stream: ProgressiveVideoFile? = null

    @Volatile
    private var activeCall: Call<ResponseBody>? = null

    override fun onStopped() {
        activeCall?.cancel()
        super.onStopped()
    }

    @Inject
    lateinit var ncApi: NcApi

    @Inject
    lateinit var userManager: UserManager

    @Inject
    lateinit var appPreferences: AppPreferences

    override fun doWork(): Result {
        NextcloudTalkApplication.sharedApplication!!.componentApplication.inject(this)

        return try {
            val currentUser = runBlocking { userManager.getUserWithId(inputData.getLong(KEY_INTERNAL_USER_ID, 0L)) }
            val attachmentFolder = inputData.getString(KEY_ATTACHMENT_FOLDER)
            val fileId = inputData.getString(KEY_FILE_ID)
            val fileName = inputData.getString(KEY_FILE_NAME)
            val remotePath = inputData.getString(KEY_FILE_PATH)
            totalFileSize = (inputData.getLong(KEY_FILE_SIZE, -1))

            checkNotNull(currentUser)
            val baseUrl = checkNotNull(currentUser.baseUrl)
            val userId = checkNotNull(currentUser.userId)
            checkNotNull(attachmentFolder)
            checkNotNull(fileName)
            checkNotNull(remotePath)

            val url = ApiUtils.getUrlForFileDownload(baseUrl, userId, remotePath)

            downloadFile(currentUser, url, fileId, fileName)
        } catch (e: IllegalStateException) {
            Log.e(javaClass.simpleName, "Something went wrong when trying to download file", e)
            Result.failure()
        } catch (e: IOException) {
            Log.e(TAG, "File download failed", e)
            Result.failure()
        } finally {
            activeCall = null
            stream?.let { download ->
                download.fail()
                inputData.getString(KEY_STREAM_KEY)?.let { VideoDownloadRegistry.remove(it, download) }
            }
        }
    }

    private fun downloadFile(currentUser: User, url: String, fileId: String?, fileName: String): Result {
        val downloadCall = ncApi.downloadFile(
            ApiUtils.getCredentials(currentUser.username, currentUser.token),
            url
        )

        activeCall = downloadCall
        if (isStopped) downloadCall.cancel()
        val response = downloadCall.execute()
        if (!response.isSuccessful) {
            response.errorBody()?.close()
            return Result.failure()
        }
        val validator = response.headers()["ETag"]?.takeUnless { it.startsWith("W/") }
            ?: response.headers()["Last-Modified"]
        return response.body()?.use { executeDownload(it, fileId, fileName, validator) } ?: Result.failure()
    }

    private fun executeDownload(body: ResponseBody, fileId: String?, fileName: String, validator: String?): Result {
        val targetFile = FileUtils.resolveSharedAttachmentFile(context.cacheDir, fileId, fileName)
        if (targetFile == null) {
            Log.e(TAG, "Refused to download file with unsafe name: $fileName")
            return Result.failure()
        }
        val expectedLength = body.contentLength().takeIf { it >= 0 } ?: totalFileSize.takeIf { it > 0 } ?: -1L
        val key = inputData.getString(KEY_STREAM_KEY)
        val download = key?.let { ProgressiveVideoFile(expectedLength, validator) }
        stream = download
        var lastProgressTime = 0L
        CacheFileWriter.write(
            body.byteStream(),
            targetFile,
            expectedLength,
            { isStopped },
            onTemporaryFile = { temporary ->
                download?.attach(temporary)
                if (key != null && download != null) VideoDownloadRegistry.register(key, download)
                reportProgress(0L, expectedLength)
            },
            publish = { temporary, target ->
                download?.publish(temporary, target) ?: temporary.renameTo(target)
            }
        ) { total ->
            download?.advance(total)
            val now = android.os.SystemClock.elapsedRealtime()
            if (now - lastProgressTime >= PROGRESS_THRESHOLD) {
                reportProgress(total, expectedLength)
                lastProgressTime = now
            }
        }
        return Result.success(Data.Builder().putBoolean(SUCCESS, true).putInt(PROGRESS, COMPLETE_PERCENTAGE).build())
    }

    private fun reportProgress(bytes: Long, total: Long) {
        setProgressAsync(
            Data.Builder()
                .putInt(PROGRESS, DownloadProgress.percent(bytes, total))
                .putBoolean(STREAM_READY, stream != null)
                .build()
        )
    }

    companion object {
        const val TAG = "DownloadFileToCache"
        const val KEY_ATTACHMENT_FOLDER = "KEY_ATTACHMENT_FOLDER"
        const val KEY_FILE_ID = "KEY_FILE_ID"
        const val KEY_FILE_NAME = "KEY_FILE_NAME"
        const val KEY_FILE_PATH = "KEY_FILE_PATH"
        const val KEY_FILE_SIZE = "KEY_FILE_SIZE"
        const val KEY_STREAM_KEY = "VIDEO_STREAM_KEY"
        const val STREAM_READY = "VIDEO_STREAM_READY"
        const val PROGRESS = "PROGRESS"
        const val SUCCESS = "SUCCESS"
        const val COMPLETE_PERCENTAGE = 100
        const val PROGRESS_THRESHOLD = 250
    }
}
