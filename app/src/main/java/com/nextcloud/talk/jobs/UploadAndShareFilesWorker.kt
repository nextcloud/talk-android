/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2024 Parneet Singh <gurayaparneet@gmail.com>
 * SPDX-FileCopyrightText: 2021-2022 Marcel Hibbe <dev@mhibbe.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.jobs

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.app.NotificationManager
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.net.toUri
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkManager
import androidx.work.WorkRequest
import androidx.work.Worker
import androidx.work.WorkerParameters
import autodagger.AutoInjector
import com.nextcloud.talk.R
import com.nextcloud.talk.api.NcApi
import com.nextcloud.talk.api.NcApiCoroutines
import com.nextcloud.talk.application.NextcloudTalkApplication
import com.nextcloud.talk.data.database.dao.ChatMessagesDao
import com.nextcloud.talk.data.database.model.SendStatus
import com.nextcloud.talk.data.user.model.User
import com.nextcloud.talk.models.json.chatpostattachment.PostConversationAttachmentRequest
import com.nextcloud.talk.models.json.chatprobeattachmentfolder.ChatProbeAttachmentData
import com.nextcloud.talk.models.json.chatprobeattachmentfolder.ProbeConversationAttachmentRequest
import com.nextcloud.talk.upload.chunked.ChunkedFileUploader
import com.nextcloud.talk.upload.chunked.OnDataTransferProgressListener
import com.nextcloud.talk.upload.normal.FileUploader
import com.nextcloud.talk.users.UserManager
import com.nextcloud.talk.utils.ApiUtils
import com.nextcloud.talk.utils.CapabilitiesUtil
import com.nextcloud.talk.utils.FileUtils
import com.nextcloud.talk.utils.ImageCompressor
import com.nextcloud.talk.utils.NotificationUtils
import com.nextcloud.talk.utils.RemoteFileUtils
import com.nextcloud.talk.utils.VideoCompressor
import com.nextcloud.talk.utils.database.user.CurrentUserProviderOld
import com.nextcloud.talk.utils.permissions.PlatformPermissionUtil
import com.nextcloud.talk.utils.preferences.AppPreferences
import io.reactivex.Observable
import io.reactivex.disposables.Disposable
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import java.io.File
import java.io.IOException
import java.util.Collections
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
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
    lateinit var ncApiCoroutines: NcApiCoroutines

    @Inject
    lateinit var currentUserProvider: CurrentUserProviderOld

    @Inject
    lateinit var appPreferences: AppPreferences

    @Inject
    lateinit var okHttpClient: OkHttpClient

    @Inject
    lateinit var platformPermissionUtil: PlatformPermissionUtil

    @Inject
    lateinit var chatDao: ChatMessagesDao

    lateinit var fileName: String

    private var mNotifyManager: NotificationManager? = null

    lateinit var roomToken: String
    lateinit var conversationName: String
    lateinit var currentUser: User
    private var isChunkedUploading = false
    private var file: File? = null
    private var chunkedFileUploader: ChunkedFileUploader? = null
    private var referenceId: String? = null
    private var internalConversationId: String? = null
    private var uploadDisposable: Disposable? = null
    private var uploadLatch: CountDownLatch? = null

    /**
     * WorkManager's own `isStopped`/`onStopped()` only becomes true/fires after
     * cancelUniqueWork() round-trips through WorkManager's internal executor and Room DB - for a
     * small/compressed image the whole upload+share can finish faster than that round-trip, so
     * isStopped alone arrives too late. [cancelledReferenceIds] is set synchronously by the UI
     * before cancelUniqueWork() is even called, so it's visible to doWork() immediately.
     */
    private fun isCancelled(): Boolean = referenceId?.let { cancelledReferenceIds.contains(it) } == true

    override fun doWork(): Result {
        NextcloudTalkApplication.sharedApplication!!.componentApplication.inject(this)

        try {
            return doUpload()
        } finally {
            referenceId?.let { cancelledReferenceIds.remove(it) }
        }
    }

    @Suppress(
        "Detekt.TooGenericExceptionCaught",
        "Detekt.LongMethod",
        "Detekt.CyclomaticComplexMethod",
        "Detekt.ReturnCount"
    )
    private fun doUpload(): Result {
        return try {
            currentUser = currentUserProvider.currentUser.blockingGet()
            val sourceFile = inputData.getString(DEVICE_SOURCE_FILE)
            roomToken = inputData.getString(ROOM_TOKEN)!!
            conversationName = inputData.getString(CONVERSATION_NAME)!!
            val metaData = inputData.getString(META_DATA)
            referenceId = inputData.getString(KEY_REFERENCE_ID)
            internalConversationId = inputData.getString(KEY_INTERNAL_CONVERSATION_ID)

            checkNotNull(currentUser)
            checkNotNull(sourceFile)
            require(sourceFile.isNotEmpty())
            checkNotNull(roomToken)

            var sourceFileUri = sourceFile.toUri()
            fileName = FileUtils.getFileName(sourceFileUri, context)
            file = FileUtils.getFileFromUri(context, sourceFileUri)

            initNotificationSetup()

            if (inputData.getBoolean(COMPRESS_IMAGES, false)) {
                sourceFileUri = compressMediaIfPossible(sourceFileUri)
            }

            val remotePath = getRemotePath(currentUser)

            val useConversationSubfolders = CapabilitiesUtil.hasConversationSubfoldersForAttachments(
                currentUser.capabilities!!.spreedCapability!!
            )
            file?.let { isChunkedUploading = it.length() > CHUNK_UPLOAD_THRESHOLD_SIZE }
            val uploadSuccess: Boolean = uploadFile(
                sourceFileUri = sourceFileUri,
                metaData = metaData,
                remotePath = remotePath,
                useConversationSubfolders = useConversationSubfolders
            )

            if (uploadSuccess && (isStopped || isCancelled())) {
                // Cancelled right as the upload finished - don't share a cancelled upload.
                return Result.failure()
            }

            if (uploadSuccess) {
                // useConversationSubfolders already shares as part of uploadFile() via
                // postConversationAttachment, so only share explicitly for the plain upload path.
                val shareSuccess = useConversationSubfolders || shareFile(remotePath, metaData)
                if (shareSuccess) {
                    updatePlaceholderStatus(SendStatus.SENT_PENDING_ACK)
                    _uploadCompletedFlow.tryEmit(roomToken)
                    return Result.success()
                }
                Log.e(TAG, "Share operation failed after upload")
                return failUpload()
            } else if (isStopped || isCancelled()) {
                // since work is cancelled the result would be ignored anyways
                return Result.failure()
            }

            Log.e(TAG, "Something went wrong when trying to upload file")
            failUpload()
        } catch (e: IOException) {
            // Transient network failures (connection reset, timeout, dropped Wi-Fi, ...) shouldn't
            // require the user to manually resend - retry a few times with backoff instead, and only
            // give up once we've exhausted the allowed attempts.
            Log.w(
                TAG,
                "Network error while uploading file (attempt ${runAttemptCount + 1}/$MAX_UPLOAD_ATTEMPTS)",
                e
            )
            if (isStopped || isCancelled()) {
                Result.failure()
            } else if (runAttemptCount < MAX_UPLOAD_ATTEMPTS - 1) {
                Result.retry()
            } else {
                failUpload()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Something went wrong when trying to upload file", e)
            failUpload()
        }
    }

    private fun failUpload(): Result {
        showFailedToUploadNotification()
        updatePlaceholderStatus(SendStatus.FAILED)
        return Result.failure()
    }

    /**
     * Replaces [file] and [fileName] with a compressed copy if [sourceFileUri] points to a
     * compressible image or video, returning the [Uri] that should be uploaded.
     */
    @Suppress("ReturnCount")
    private fun compressMediaIfPossible(sourceFileUri: Uri): Uri {
        val originalFile = file ?: return sourceFileUri
        val mimeType = FileUtils.resolveMimeType(context, sourceFileUri)

        val compressedFile = when {
            ImageCompressor.isCompressible(mimeType) -> ImageCompressor.compress(context, originalFile)
            VideoCompressor.isCompressible(mimeType) -> VideoCompressor.compress(context, originalFile)
            else -> null
        } ?: return sourceFileUri

        file = compressedFile
        fileName = compressedFile.name
        return Uri.fromFile(compressedFile)
    }

    private fun uploadFile(
        sourceFileUri: Uri,
        metaData: String?,
        remotePath: String,
        useConversationSubfolders: Boolean
    ): Boolean =
        if (file == null) {
            false
        } else if (useConversationSubfolders) {
            uploadUsingConversationSubfolders(sourceFileUri, metaData)
        } else if (isChunkedUploading) {
            Log.d(TAG, "starting chunked upload because size is " + file!!.length())
            val mimeType = context.contentResolver.getType(sourceFileUri)?.toMediaTypeOrNull()
            chunkedFileUploader = ChunkedFileUploader(
                okHttpClient,
                currentUser,
                this,
                ncApiCoroutines
            )
            chunkedFileUploader!!.upload(file!!, mimeType, remotePath)
        } else {
            Log.d(TAG, "starting normal upload (not chunked) of $fileName")
            val observable = FileUploader(
                okHttpClient,
                context,
                currentUser,
                roomToken,
                ncApi,
                file!!,
                ncApiCoroutines
            )
                .upload(sourceFileUri, fileName, remotePath, null)
            blockingUpload(observable)
        }

    // unlike .blockingFirst(), keeps a Disposable so onStopped() can cancel the underlying OkHttp call
    private fun blockingUpload(observable: Observable<Boolean>): Boolean {
        val latch = CountDownLatch(1)
        uploadLatch = latch
        var result = false
        var error: Throwable? = null
        uploadDisposable = observable.subscribe(
            { success ->
                result = success
                latch.countDown()
            },
            { throwable ->
                error = throwable
                latch.countDown()
            },
            { latch.countDown() }
        )
        latch.await()
        uploadDisposable = null
        uploadLatch = null

        if (isStopped || isCancelled()) {
            return false
        }
        error?.let { throw it }
        return result
    }

    private fun uploadUsingConversationSubfolders(sourceFileUri: Uri, metaData: String?): Boolean =
        runBlocking {
            val credentials = ApiUtils.getCredentials(
                currentUser.username,
                currentUser.token
            ) ?: return@runBlocking false
            val uploadId = UUID.randomUUID().toString()
            val fileNames = ProbeConversationAttachmentRequest().apply {
                fileNames = listOf(fileName)
            }

            val probeResponse = ncApiCoroutines.probeConversationAttachmentFolder(
                credentials,
                ApiUtils.getUrlForChatAttachmentFolder(ApiUtils.API_V1, currentUser.baseUrl, roomToken),
                fileNames
            )

            val draftFolderPath = probeResponse.ocs?.data?.folder
            if (draftFolderPath.isNullOrEmpty()) {
                Log.e(TAG, "Draft folder path missing in probe response")
                return@runBlocking false
            }
            val predictedName = resolveFinalFileName(fileName, probeResponse.ocs?.data!!)
            val tempRemotePath = "/$draftFolderPath/$uploadId-$fileName"

            val uploadSuccess = if (isChunkedUploading) {
                val mimeType = context.contentResolver.getType(sourceFileUri)?.toMediaTypeOrNull()
                chunkedFileUploader = ChunkedFileUploader(
                    okHttpClient,
                    currentUser,
                    this@UploadAndShareFilesWorker,
                    ncApiCoroutines
                )
                chunkedFileUploader!!.upload(file!!, mimeType, tempRemotePath)
            } else {
                FileUploader(okHttpClient, context, currentUser, roomToken, ncApi, file!!, ncApiCoroutines)
                    .uploadToConversationSubfolder(sourceFileUri, tempRemotePath)
            }

            if (!uploadSuccess || isStopped || isCancelled()) {
                return@runBlocking false
            }

            val params = PostConversationAttachmentRequest().apply {
                filePath = tempRemotePath
                referenceId = this@UploadAndShareFilesWorker.referenceId.orEmpty()
                talkMetaData = metaData
                fileName = predictedName
            }

            runCatching {
                ncApiCoroutines.postConversationAttachment(
                    credentials,
                    ApiUtils.getUrlForChatAttachment(ApiUtils.API_V1, currentUser.baseUrl, roomToken),
                    params
                )
            }
                .onFailure { Log.e(TAG, "Failed to finalize uploaded attachment", it) }
                .isSuccess
        }

    @SuppressLint("CheckResult")
    private fun shareFile(remotePath: String, metaData: String?): Boolean =
        try {
            ncApi.createRemoteShare(
                ApiUtils.getCredentials(currentUser.username, currentUser.token),
                ApiUtils.getSharingUrl(currentUser.baseUrl!!),
                remotePath,
                roomToken,
                "10",
                metaData,
                referenceId.orEmpty()
            ).blockingFirst()
            true
        } catch (e: NoSuchElementException) {
            Log.e(TAG, "Failed to share file to room", e)
            false
        }

    private fun resolveFinalFileName(originalName: String, probeData: ChatProbeAttachmentData): String =
        probeData.renames?.get(originalName) ?: originalName

    private fun getRemotePath(currentUser: User): String {
        val remotePath = CapabilitiesUtil.getAttachmentFolder(
            currentUser.capabilities!!.spreedCapability!!
        ) + "/" + fileName
        return RemoteFileUtils.getNewPathIfFileExists(ncApi, currentUser, remotePath)
    }

    override fun onTransferProgress(percentage: Int) {
        setProgressAsync(Data.Builder().putInt(PROGRESS_KEY, percentage).build())
    }

    private fun updatePlaceholderStatus(status: SendStatus) {
        val refId = referenceId ?: return
        val convId = internalConversationId ?: return
        val entity = runBlocking { chatDao.getTempMessageForConversation(convId, refId, null).firstOrNull() }
        entity?.let { chatDao.updateChatMessage(it.copy(sendStatus = status)) }
    }

    override fun onStopped() {
        if (file != null && isChunkedUploading) {
            chunkedFileUploader?.abortUpload {}
        }
        uploadDisposable?.dispose()
        uploadLatch?.countDown()
        super.onStopped()
    }

    private fun initNotificationSetup() {
        mNotifyManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
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

        mNotifyManager!!.notify(SystemClock.uptimeMillis().toInt(), failureNotification)
    }

    private fun getResourceString(context: Context, resourceId: Int): String = context.resources.getString(resourceId)

    companion object {
        private val TAG = UploadAndShareFilesWorker::class.simpleName
        private const val DEVICE_SOURCE_FILE = "DEVICE_SOURCE_FILE"
        private const val ROOM_TOKEN = "ROOM_TOKEN"
        private const val CONVERSATION_NAME = "CONVERSATION_NAME"
        private const val META_DATA = "META_DATA"
        const val KEY_REFERENCE_ID = "REFERENCE_ID"
        const val KEY_INTERNAL_CONVERSATION_ID = "INTERNAL_CONVERSATION_ID"
        const val PROGRESS_KEY = "UPLOAD_PROGRESS"
        private const val COMPRESS_IMAGES = "COMPRESS_IMAGES"
        private const val CHUNK_UPLOAD_THRESHOLD_SIZE: Long = 1024 * 1024

        // Total attempts allowed for a single upload (1 initial run + retries) before giving up on a
        // transient network failure and marking the placeholder FAILED.
        private const val MAX_UPLOAD_ATTEMPTS = 4
        const val REQUEST_PERMISSION = 3123

        // referenceIds the user cancelled - set synchronously here, before cancelUniqueWork() is
        // even called, so doWork() can see it immediately instead of waiting for isStopped, which
        // only becomes true once WorkManager's own async cancellation dispatch completes.
        private val cancelledReferenceIds: MutableSet<String> = Collections.newSetFromMap(ConcurrentHashMap())

        private val _uploadCompletedFlow: MutableSharedFlow<String> = MutableSharedFlow(
            replay = 1,
            extraBufferCapacity = 1
        )
        val uploadCompletedFlow: SharedFlow<String> = _uploadCompletedFlow

        @OptIn(ExperimentalCoroutinesApi::class)
        fun clearUploadCompletedReplay() {
            _uploadCompletedFlow.resetReplayCache()
        }

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

        @Suppress("LongParameterList")
        fun upload(
            fileUri: String,
            roomToken: String,
            conversationName: String,
            metaData: String?,
            referenceId: String = "",
            internalConversationId: String = "",
            compressImages: Boolean = false
        ): UUID {
            val data: Data = Data.Builder()
                .putString(DEVICE_SOURCE_FILE, fileUri)
                .putString(ROOM_TOKEN, roomToken)
                .putString(CONVERSATION_NAME, conversationName)
                .putString(META_DATA, metaData)
                .putString(KEY_REFERENCE_ID, referenceId)
                .putString(KEY_INTERNAL_CONVERSATION_ID, internalConversationId)
                .putBoolean(COMPRESS_IMAGES, compressImages)
                .build()
            val uploadWorker: OneTimeWorkRequest = OneTimeWorkRequest.Builder(UploadAndShareFilesWorker::class.java)
                .setInputData(data)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .setBackoffCriteria(
                    BackoffPolicy.EXPONENTIAL,
                    WorkRequest.MIN_BACKOFF_MILLIS,
                    TimeUnit.MILLISECONDS
                )
                .build()
            WorkManager.getInstance().enqueueUniqueWork(fileUri, ExistingWorkPolicy.KEEP, uploadWorker)
            return uploadWorker.id
        }

        fun cancelUpload(referenceId: String, fileUri: String) {
            cancelledReferenceIds.add(referenceId)
            WorkManager.getInstance().cancelUniqueWork(fileUri)
        }
    }
}
