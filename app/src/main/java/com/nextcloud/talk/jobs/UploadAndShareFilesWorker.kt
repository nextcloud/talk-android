/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2024 Parneet Singh <gurayaparneet@gmail.com>
 * SPDX-FileCopyrightText: 2021-2022 Marcel Hibbe <dev@mhibbe.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.jobs

import android.Manifest
import android.app.Activity
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.net.toUri
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import autodagger.AutoInjector
import com.nextcloud.talk.R
import com.nextcloud.talk.activities.MainActivity
import com.nextcloud.talk.api.NcApi
import com.nextcloud.talk.application.NextcloudTalkApplication
import com.nextcloud.talk.data.user.model.User
import com.nextcloud.talk.upload.chunked.ChunkedFileUploader
import com.nextcloud.talk.upload.chunked.OnDataTransferProgressListener
import com.nextcloud.talk.upload.normal.FileUploader
import com.nextcloud.talk.users.UserManager
import com.nextcloud.talk.utils.CapabilitiesUtil
import com.nextcloud.talk.utils.FileUtils
import com.nextcloud.talk.utils.NotificationUtils
import com.nextcloud.talk.utils.RemoteFileUtils
import com.nextcloud.talk.utils.bundle.BundleKeys.KEY_INTERNAL_USER_ID
import com.nextcloud.talk.utils.bundle.BundleKeys.KEY_ROOM_TOKEN
import com.nextcloud.talk.utils.database.user.CurrentUserProviderNew
import com.nextcloud.talk.utils.permissions.PlatformPermissionUtil
import com.nextcloud.talk.utils.preferences.AppPreferences
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import java.io.File
import javax.inject.Inject

@AutoInjector(NextcloudTalkApplication::class)
class UploadAndShareFilesWorker(val context: Context, workerParameters: WorkerParameters) :
    Worker(context, workerParameters),
    OnDataTransferProgressListener {

    @Inject
    lateinit var ncApi: NcApi

    @Inject
    lateinit var userManager: UserManager

    @Inject
    lateinit var currentUserProvider: CurrentUserProviderNew

    @Inject
    lateinit var appPreferences: AppPreferences

    @Inject
    lateinit var okHttpClient: OkHttpClient

    @Inject
    lateinit var platformPermissionUtil: PlatformPermissionUtil

    lateinit var fileName: String

    private var mNotifyManager: NotificationManager? = null
    private var mBuilder: NotificationCompat.Builder? = null
    private var notificationId: Int = 0

    lateinit var roomToken: String
    lateinit var conversationName: String
    lateinit var currentUser: User
    private var isChunkedUploading = false
    private var file: File? = null
    private var chunkedFileUploader: ChunkedFileUploader? = null

    @Suppress("Detekt.TooGenericExceptionCaught")
    override fun doWork(): Result {
        NextcloudTalkApplication.sharedApplication!!.componentApplication.inject(this)

        return try {
            currentUser = currentUserProvider.currentUser.blockingGet()
            val sourceFile = inputData.getString(DEVICE_SOURCE_FILE)
            roomToken = inputData.getString(ROOM_TOKEN)!!
            conversationName = inputData.getString(CONVERSATION_NAME)!!
            val metaData = inputData.getString(META_DATA)

            checkNotNull(currentUser)
            checkNotNull(sourceFile)
            require(sourceFile.isNotEmpty())
            checkNotNull(roomToken)

            val sourceFileUri = sourceFile.toUri()
            fileName = FileUtils.getFileName(sourceFileUri, context)
            file = FileUtils.getFileFromUri(context, sourceFileUri)
            val remotePath = getRemotePath(currentUser)

            initNotificationSetup()
            file?.let { isChunkedUploading = it.length() > CHUNK_UPLOAD_THRESHOLD_SIZE }
            val uploadSuccess: Boolean = uploadFile(sourceFileUri, metaData, remotePath)

            if (uploadSuccess) {
                cancelNotification()
                return Result.success()
            } else if (isStopped) {
                // since work is cancelled the result would be ignored anyways
                return Result.failure()
            }

            Log.e(TAG, "Something went wrong when trying to upload file")
            showFailedToUploadNotification()
            return Result.failure()
        } catch (e: Exception) {
            Log.e(TAG, "Something went wrong when trying to upload file", e)
            showFailedToUploadNotification()
            return Result.failure()
        }
    }

    private fun uploadFile(sourceFileUri: Uri, metaData: String?, remotePath: String): Boolean =
        if (file == null) {
            false
        } else if (isChunkedUploading) {
            Log.d(TAG, "starting chunked upload because size is " + file!!.length())

            initNotificationWithPercentage()
            val mimeType = context.contentResolver.getType(sourceFileUri)?.toMediaTypeOrNull()

            chunkedFileUploader = ChunkedFileUploader(okHttpClient, currentUser, roomToken, metaData, this)
            chunkedFileUploader!!.upload(file!!, mimeType, remotePath)
        } else {
            Log.d(TAG, "starting normal upload (not chunked) of $fileName")

            FileUploader(okHttpClient, context, currentUser, roomToken, ncApi, file!!)
                .upload(sourceFileUri, fileName, remotePath, metaData)
                .blockingFirst()
        }

    private fun getRemotePath(currentUser: User): String {
        val remotePath = CapabilitiesUtil.getAttachmentFolder(
            currentUser.capabilities!!.spreedCapability!!
        ) + "/" + fileName
        return RemoteFileUtils.getNewPathIfFileExists(ncApi, currentUser, remotePath)
    }

    override fun onTransferProgress(percentage: Int) {
        val progressUpdateNotification = mBuilder!!
            .setProgress(HUNDRED_PERCENT, percentage, false)
            .setContentText(getNotificationContentText(percentage))
            .build()

        mNotifyManager!!.notify(notificationId, progressUpdateNotification)
    }

    override fun onStopped() {
        if (file != null && isChunkedUploading) {
            chunkedFileUploader?.abortUpload {
                mNotifyManager?.cancel(notificationId)
            }
        }
        super.onStopped()
    }

    private fun initNotificationSetup() {
        mNotifyManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        mBuilder = NotificationCompat.Builder(
            context,
            NotificationUtils.NotificationChannels
                .NOTIFICATION_CHANNEL_UPLOADS.name
        )
    }

    private fun initNotificationWithPercentage() {
        val initNotification = mBuilder!!
            .setContentTitle(context.resources.getString(R.string.nc_upload_in_progess))
            .setContentText(getNotificationContentText(ZERO_PERCENT))
            .setSmallIcon(R.drawable.upload_white)
            .setOngoing(true)
            .setProgress(HUNDRED_PERCENT, ZERO_PERCENT, false)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setGroup(NotificationUtils.KEY_UPLOAD_GROUP)
            .setContentIntent(getIntentToOpenConversation())
            .addAction(
                R.drawable.ic_cancel_white_24dp,
                getResourceString(context, R.string.nc_cancel),
                getCancelUploadIntent()
            )
            .build()

        notificationId = SystemClock.uptimeMillis().toInt()
        mNotifyManager!!.notify(notificationId, initNotification)
        // only need one summary notification but multiple upload worker can call it more than once but it is safe
        // because of the same notification object config and id.
        makeSummaryNotification()
    }

    private fun makeSummaryNotification() {
        // summary notification encapsulating the group of notifications
        val summaryNotification = NotificationCompat.Builder(
            context,
            NotificationUtils.NotificationChannels
                .NOTIFICATION_CHANNEL_UPLOADS.name
        ).setSmallIcon(R.drawable.upload_white)
            .setGroup(NotificationUtils.KEY_UPLOAD_GROUP)
            .setGroupSummary(true)
            .build()

        mNotifyManager?.notify(NotificationUtils.GROUP_SUMMARY_NOTIFICATION_ID, summaryNotification)
    }

    private fun getActiveUploadNotifications(): Int? {
        // filter out active notifications that are upload notifications using group
        return mNotifyManager?.activeNotifications?.filter {
            it.notification.group == NotificationUtils
                .KEY_UPLOAD_GROUP
        }?.size
    }

    private fun cancelNotification() {
        mNotifyManager?.cancel(notificationId)
        // summary notification would not get dismissed automatically
        // if child notifications are cancelled programmatically
        // so check if only 1 notification left if yes
        // then cancel it (which is summary notification)
        if (getActiveUploadNotifications() == 1) {
            mNotifyManager?.cancel(NotificationUtils.GROUP_SUMMARY_NOTIFICATION_ID)
        }
    }

    private fun getNotificationContentText(percentage: Int): String =
        String.format(
            getResourceString(context, R.string.nc_upload_notification_text),
            getShortenedFileName(),
            conversationName,
            percentage
        )

    private fun getShortenedFileName(): String =
        if (fileName.length > NOTIFICATION_FILE_NAME_MAX_LENGTH) {
            THREE_DOTS + fileName.takeLast(NOTIFICATION_FILE_NAME_MAX_LENGTH)
        } else {
            fileName
        }

    private fun getCancelUploadIntent(): PendingIntent =
        WorkManager.getInstance(applicationContext)
            .createCancelPendingIntent(id)

    private fun getIntentToOpenConversation(): PendingIntent? {
        val bundle = Bundle()
        val intent = Intent(context, MainActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_NEW_TASK

        bundle.putString(KEY_ROOM_TOKEN, roomToken)
        bundle.putLong(KEY_INTERNAL_USER_ID, currentUser.id!!)

        intent.putExtras(bundle)

        val requestCode = System.currentTimeMillis().toInt()
        val intentFlag: Int = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            PendingIntent.FLAG_MUTABLE
        } else {
            0
        }
        return PendingIntent.getActivity(context, requestCode, intent, intentFlag)
    }

    private fun showFailedToUploadNotification() {
        val failureTitle = getResourceString(context, R.string.nc_upload_failed_notification_title)
        val failureText = String.format(
            getResourceString(context, R.string.nc_upload_failed_notification_text),
            fileName
        )
        val failureNotification = NotificationCompat.Builder(
            context,
            NotificationUtils.NotificationChannels
                .NOTIFICATION_CHANNEL_UPLOADS.name
        )
            .setContentTitle(failureTitle)
            .setContentText(failureText)
            .setSmallIcon(R.drawable.baseline_error_24)
            .setGroup(NotificationUtils.KEY_UPLOAD_GROUP)
            .setOngoing(false)
            .build()

        mNotifyManager?.cancel(notificationId)
        // update current notification with failure info
        mNotifyManager!!.notify(SystemClock.uptimeMillis().toInt(), failureNotification)
    }

    private fun getResourceString(context: Context, resourceId: Int): String = context.resources.getString(resourceId)

    companion object {
        private val TAG = UploadAndShareFilesWorker::class.simpleName
        private const val DEVICE_SOURCE_FILE = "DEVICE_SOURCE_FILE"
        private const val ROOM_TOKEN = "ROOM_TOKEN"
        private const val CONVERSATION_NAME = "CONVERSATION_NAME"
        private const val META_DATA = "META_DATA"
        private const val CHUNK_UPLOAD_THRESHOLD_SIZE: Long = 1024000
        private const val NOTIFICATION_FILE_NAME_MAX_LENGTH = 20
        private const val THREE_DOTS = "…"
        private const val HUNDRED_PERCENT = 100
        private const val ZERO_PERCENT = 0
        const val REQUEST_PERMISSION = 3123

        fun requestStoragePermission(activity: Activity) {
            when {
                Build.VERSION
                    .SDK_INT >= Build.VERSION_CODES.TIRAMISU -> {
                    activity.requestPermissions(
                        arrayOf(
                            Manifest.permission.READ_MEDIA_IMAGES,
                            Manifest.permission.READ_MEDIA_VIDEO,
                            Manifest.permission.READ_MEDIA_AUDIO
                        ),
                        REQUEST_PERMISSION
                    )
                }

                Build.VERSION.SDK_INT > Build.VERSION_CODES.Q -> {
                    activity.requestPermissions(
                        arrayOf(
                            Manifest.permission.READ_EXTERNAL_STORAGE
                        ),
                        REQUEST_PERMISSION
                    )
                }

                else -> {
                    activity.requestPermissions(
                        arrayOf(
                            Manifest.permission.WRITE_EXTERNAL_STORAGE
                        ),
                        REQUEST_PERMISSION
                    )
                }
            }
        }

        fun upload(fileUri: String, roomToken: String, conversationName: String, metaData: String?) {
            val data: Data = Data.Builder()
                .putString(DEVICE_SOURCE_FILE, fileUri)
                .putString(ROOM_TOKEN, roomToken)
                .putString(CONVERSATION_NAME, conversationName)
                .putString(META_DATA, metaData)
                .build()
            val uploadWorker: OneTimeWorkRequest = OneTimeWorkRequest.Builder(UploadAndShareFilesWorker::class.java)
                .setInputData(data)
                .build()
            WorkManager.getInstance().enqueueUniqueWork(fileUri, ExistingWorkPolicy.KEEP, uploadWorker)
        }
    }
}
