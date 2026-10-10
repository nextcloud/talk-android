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
import android.content.pm.ServiceInfo
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
import androidx.work.ForegroundInfo
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
import com.nextcloud.talk.models.json.chatpostattachment.PostConversationAttachmentRequestDto
import com.nextcloud.talk.models.json.chatprobeattachmentfolder.ChatProbeAttachmentDataDto
import com.nextcloud.talk.models.json.chatprobeattachmentfolder.ProbeConversationAttachmentRequestDto
import com.nextcloud.talk.upload.chunked.ChunkedFileUploader
import com.nextcloud.talk.upload.chunked.OnDataTransferProgressListener
import com.nextcloud.talk.upload.UploadNotification
import com.nextcloud.talk.upload.UploadProgressThrottle
import com.nextcloud.talk.upload.UploadRetryPolicy
import com.nextcloud.talk.upload.UploadWorkspace
import com.nextcloud.talk.upload.PreparedUpload
import com.nextcloud.talk.upload.normal.FileUploader
import com.nextcloud.talk.users.UserManager
import com.nextcloud.talk.utils.ApiUtils
import com.nextcloud.talk.utils.CapabilitiesUtil
import com.nextcloud.talk.utils.FileUtils
import com.nextcloud.talk.utils.ImageCompressor
import com.nextcloud.talk.utils.NotificationUtils
import com.nextcloud.talk.utils.RemoteFileUtils
import com.nextcloud.talk.utils.VideoCompressor
import com.nextcloud.talk.utils.bundle.BundleKeys.KEY_INTERNAL_USER_ID
import com.nextcloud.talk.utils.permissions.PlatformPermissionUtil
import com.nextcloud.talk.utils.preferences.AppPreferences
import io.reactivex.Observable
import io.reactivex.disposables.Disposable
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import java.io.File
import java.io.IOException
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import javax.inject.Inject

@Suppress("TooManyFunctions")
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
    lateinit var appPreferences: AppPreferences

    @Inject
    lateinit var okHttpClient: OkHttpClient

    @Inject
    lateinit var platformPermissionUtil: PlatformPermissionUtil

    @Inject
    lateinit var chatDao: ChatMessagesDao

    lateinit var fileName: String

    private val notificationManager by lazy {
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    }

    lateinit var roomToken: String
    lateinit var conversationName: String
    lateinit var currentUser: User
    private var isChunkedUploading = false
    private var file: File? = null

    @Volatile
    private var chunkedFileUploader: ChunkedFileUploader? = null

    private var referenceId: String? = null
    private var internalConversationId: String? = null
    private var uploadDisposable: Disposable? = null
    private var uploadLatch: CountDownLatch? = null
    private lateinit var workspace: UploadWorkspace
    private val progressThrottle = UploadProgressThrottle()
    private var isForeground = false
    private var keepWorkspace = false
    private val notificationId = id.hashCode()

    /**
     * The user cancelled this upload. The cancel is a flag file in the workspace, not a cancellation in
     * WorkManager: a cancelled work left in the queue of the conversation makes the APPEND_OR_REPLACE of the next
     * upload delete the other, still living works of that queue. The worker looks at the flag at the start, between
     * its stages and in the progress callback, and then aborts itself, see [cancelledResult].
     */
    private fun isCancelled(): Boolean = ::workspace.isInitialized && workspace.isCancelled()

    override fun doWork(): Result {
        NextcloudTalkApplication.sharedApplication!!.componentApplication.inject(this)

        referenceId = inputData.getString(KEY_REFERENCE_ID)
        workspace = UploadWorkspace(workspaceDir(context, id))
        // WorkManager does not interrupt the thread of a stopped worker, so an old run can still be busy.
        if (!workspace.tryLock()) {
            Log.w(TAG, "Another run of this upload is still active, trying again later")
            return Result.retry()
        }
        try {
            return doUpload()
        } finally {
            if (!keepWorkspace) {
                workspace.delete()
            }
            workspace.unlock()
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
            // Read first, so a failure below can still mark the placeholder message as failed.
            internalConversationId = inputData.getString(KEY_INTERNAL_CONVERSATION_ID)

            // The user comes first: a cancelled upload that waited for a retry has to remove its parts from the
            // server, and that needs the account.
            val userId = inputData.getLong(KEY_INTERNAL_USER_ID, 0L)
            currentUser = runBlocking { userManager.getUserWithId(userId) } ?: run {
                // E.g. the account was removed while the upload waited for a retry.
                Log.e(TAG, "No user found for id $userId")
                return if (isCancelled()) cancelledResult() else failUpload()
            }
            if (isCancelled()) {
                return cancelledResult()
            }
            val sourceFile = inputData.getString(DEVICE_SOURCE_FILE)
            roomToken = inputData.getString(ROOM_TOKEN)!!
            conversationName = inputData.getString(CONVERSATION_NAME)!!
            val metaData = inputData.getString(META_DATA)

            checkNotNull(sourceFile)
            require(sourceFile.isNotEmpty())
            checkNotNull(roomToken)

            val sourceFileUri = sourceFile.toUri()
            fileName = FileUtils.getFileName(sourceFileUri, context)
            deleteFinishedWorkspaces()

            // The stages come before the preparation: after the share nothing is needed any more, after the
            // upload only the path, and the prepared file may be gone (the system cleaned the cache).
            if (workspace.isShared()) {
                return successResult()
            }
            startForeground()

            val prepared = if (workspace.uploadedPath() != null) {
                workspace.prepared()
            } else {
                prepareOnce(sourceFileUri) ?: return failUpload()
            }
            file = prepared?.file
            fileName = prepared?.fileName ?: workspace.uploadedName() ?: fileName
            if (isStopped || isCancelled()) {
                return stoppedResult()
            }

            val useConversationSubfolders = CapabilitiesUtil.hasConversationSubfoldersForAttachments(
                currentUser.capabilities!!.spreedCapability!!
            )
            val allowUpdate = inputData.getBoolean(ALLOW_UPDATE, false)
            file?.let { isChunkedUploading = it.length() > CHUNK_UPLOAD_THRESHOLD_SIZE }
            val uploadUri = prepared?.let { Uri.fromFile(it.file) }

            val shared = if (useConversationSubfolders) {
                // The conversation subfolder path shares as part of its last step, postConversationAttachment.
                uploadUsingConversationSubfolders(uploadUri, metaData, allowUpdate)
            } else {
                uploadAndShare(uploadUri, metaData)
            }

            if (shared) {
                workspace.markShared()
                return successResult()
            } else if (isStopped || isCancelled()) {
                return stoppedResult()
            }

            Log.e(TAG, "Something went wrong when trying to upload file")
            failUpload()
        } catch (e: Exception) {
            handleUploadError(e)
        }
    }

    /**
     * Upload and share are two stages. What is done is kept in the workspace, so a run after a retry or after a
     * stop does not upload the file or share it a second time.
     */
    private fun uploadAndShare(uploadUri: Uri?, metaData: String?): Boolean {
        val remotePath = workspace.uploadedPath() ?: getRemotePath(currentUser)
        val uploaded = workspace.uploadedPath() != null || uploadFile(checkNotNull(uploadUri), remotePath)
        if (uploaded) {
            workspace.markUploaded(remotePath, fileName)
        }
        // Not shared when the user cancelled right as the upload finished.
        return uploaded && !isCancelled() && shareFile(remotePath, metaData)
    }

    private fun successResult(): Result {
        // A stopped work is scheduled again whatever it returns; the stages in the workspace make that run short.
        keepWorkspace = isStopped
        updatePlaceholderStatus(SendStatus.SENT_PENDING_ACK)
        _uploadCompletedFlow.tryEmit(roomToken)
        return Result.success()
    }

    private fun handleUploadError(e: Exception): Result {
        if (isStopped || isCancelled()) {
            return stoppedResult()
        }
        val kind = UploadRetryPolicy.classify(e)
        val serverErrors = if (kind == UploadRetryPolicy.FailureKind.SERVER) {
            workspace.registerServerError()
        } else {
            workspace.serverErrors()
        }
        return when (UploadRetryPolicy.decide(kind, serverErrors)) {
            UploadRetryPolicy.Decision.RETRY -> {
                Log.w(TAG, "Upload interrupted ($kind, server errors: $serverErrors), will resume", e)
                keepWorkspace = true
                Result.retry()
            }

            UploadRetryPolicy.Decision.FAIL -> {
                Log.e(TAG, "Something went wrong when trying to upload file", e)
                failUpload()
            }
        }
    }

    /**
     * A stop by the system keeps the parts on the server and the prepared file, so the next run resumes.
     * The result of a work that WorkManager stopped is ignored; WorkManager schedules it again by itself.
     */
    private fun stoppedResult(): Result =
        if (isCancelled()) {
            cancelledResult()
        } else {
            keepWorkspace = true
            Result.retry()
        }

    /**
     * The user cancelled the upload. Removes the parts from the server (here, in the worker thread, not in
     * onStopped, which runs inside a WorkManager cancellation handler) and the placeholder message. Returns
     * success, not failure or cancelled: a failed or cancelled work in the queue of the conversation makes
     * WorkManager delete the other uploads of that queue when the next file is sent.
     */
    private fun cancelledResult(): Result {
        abortRemote(deleteUploadedFile = true)
        deletePlaceholder()
        return Result.success()
    }

    private fun newChunkedUploader() =
        ChunkedFileUploader(
            okHttpClient,
            currentUser,
            this,
            ncApiCoroutines,
            isRestarted = workspace::isRestarted,
            markRestarted = workspace::markRestarted
        )

    /**
     * Publishes [uploader] before the last stop check: a stop that comes earlier is seen here, a later one reaches
     * the uploader through [onStopped].
     */
    private fun startChunkedUpload(uploader: ChunkedFileUploader, mimeType: MediaType?, path: String): Boolean {
        chunkedFileUploader = uploader
        if (isStopped || isCancelled()) {
            return false
        }
        return uploader.upload(file!!, mimeType, path)
    }

    /**
     * Removes what the upload left on the server: the parts of a chunked upload and, when [deleteUploadedFile] is
     * set and the file was not shared yet, the assembled file. Never throws.
     */
    @Suppress("Detekt.TooGenericExceptionCaught")
    private fun abortRemote(deleteUploadedFile: Boolean = false) {
        try {
            if (!::currentUser.isInitialized) {
                return
            }
            val uploader = chunkedFileUploader ?: newChunkedUploader()
            // The parts are keyed by the prepared file: this finds them also when the upload of this run did not
            // start yet or ran in an earlier process.
            val prepared = workspace.prepared()?.file
            if (prepared != null && prepared.length() > CHUNK_UPLOAD_THRESHOLD_SIZE) {
                uploader.abortUpload(prepared) {}
            } else if (chunkedFileUploader != null) {
                uploader.abortUpload {}
            }
            val uploadedPath = workspace.uploadedPath()
            if (deleteUploadedFile && uploadedPath != null && !workspace.isShared()) {
                uploader.deleteUploadedFile(uploadedPath)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not remove the upload from the server", e)
        }
    }

    private fun deletePlaceholder() {
        val refId = referenceId?.takeIf { it.isNotEmpty() } ?: return
        val convId = internalConversationId?.takeIf { it.isNotEmpty() } ?: return
        chatDao.deleteTempChatMessageIfPending(convId, refId)
    }

    @SuppressLint("InlinedApi")
    @Suppress("Detekt.TooGenericExceptionCaught")
    private fun startForeground() {
        try {
            setForegroundAsync(
                ForegroundInfo(
                    notificationId,
                    progressNotification(0),
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                )
            ).get()
            isForeground = true
        } catch (e: Exception) {
            // E.g. Android 12+ does not allow starting a foreground service from the background.
            Log.w(TAG, "Could not start the foreground service, uploading as a regular job", e)
        }
    }

    private fun progressNotification(percent: Int) =
        UploadNotification.build(
            context,
            notificationId,
            fileName,
            percent,
            id.toString(),
            referenceId,
            internalConversationId
        )

    private fun prepareFile(sourceFileUri: Uri, dir: File): PreparedUpload? {
        val original = FileUtils.getFileFromUri(context, sourceFileUri, dir) ?: return null
        throwIfStopped()
        val compressed = if (inputData.getBoolean(COMPRESS_IMAGES, false)) {
            compressMediaIfPossible(sourceFileUri, original, dir)
        } else {
            null
        }
        throwIfStopped()
        return compressed ?: PreparedUpload(original, fileName)
    }

    /** Keeps a stopped run from storing its half-finished file as the prepared one (see [PreparationStopped]). */
    private fun throwIfStopped() {
        if (isStopped || isCancelled()) {
            throw PreparationStopped()
        }
    }

    private class PreparationStopped : RuntimeException("Stopped while preparing the file")

    // A work that WorkManager does not know, or knows as finished, cannot resume, so its workspace is garbage.
    @Suppress("Detekt.TooGenericExceptionCaught")
    private fun deleteFinishedWorkspaces() {
        UploadWorkspace.deleteFinished(File(context.noBackupFilesDir, WORKSPACE_DIR)) { name ->
            try {
                val info = WorkManager.getInstance(context).getWorkInfoById(UUID.fromString(name)).get()
                info != null && !info.state.isFinished
            } catch (e: Exception) {
                Log.w(TAG, "Could not look up work $name, keeping its files", e)
                true
            }
        }
    }

    /**
     * Returns null when the file cannot be prepared and waiting does not help (full disk, unreadable source). Any
     * other IOException, e.g. a lost network while a cloud provider streams the file, is thrown to the retry policy.
     */
    private fun prepareOnce(sourceFileUri: Uri): PreparedUpload? =
        try {
            workspace.prepareOnce { dir -> prepareFile(sourceFileUri, dir) }
        } catch (e: IOException) {
            if (UploadRetryPolicy.classifyPreparation(e) != UploadRetryPolicy.FailureKind.OTHER) {
                throw e
            }
            Log.e(TAG, "Could not prepare the file", e)
            null
        }

    /**
     * Ends the work as success, not failure: the failure is already shown (notification, FAILED placeholder), and
     * a failed work would make WorkManager fail the uploads queued behind it in the conversation without running
     * them, leaving their placeholders "sending" for ever.
     */
    private fun failUpload(): Result {
        abortRemote()
        showFailedToUploadNotification()
        updatePlaceholderStatus(SendStatus.FAILED)
        return Result.success()
    }

    /**
     * Returns a compressed copy of [originalFile] inside [dir] if [sourceFileUri] points to a compressible image or
     * video, or null when there is nothing to compress.
     */
    private fun compressMediaIfPossible(sourceFileUri: Uri, originalFile: File, dir: File): PreparedUpload? {
        val mimeType = FileUtils.resolveMimeType(context, sourceFileUri)

        val compressedFile = when {
            ImageCompressor.isCompressible(mimeType) -> ImageCompressor.compress(context, originalFile, dir)
            VideoCompressor.isCompressible(mimeType) -> VideoCompressor.compress(context, originalFile, dir)
            else -> null
        } ?: return null

        if (originalFile.parentFile == dir) {
            originalFile.delete()
        }
        return PreparedUpload(compressedFile, compressedFile.name)
    }

    private fun uploadFile(sourceFileUri: Uri, remotePath: String): Boolean =
        if (file == null) {
            false
        } else if (isChunkedUploading) {
            Log.d(TAG, "starting chunked upload because size is " + file!!.length())
            val mimeType = FileUtils.resolveMimeType(context, sourceFileUri)?.toMediaTypeOrNull()
            startChunkedUpload(newChunkedUploader(), mimeType, remotePath)
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

    private fun uploadUsingConversationSubfolders(
        sourceFileUri: Uri?,
        metaData: String?,
        allowUpdate: Boolean
    ): Boolean =
        runBlocking {
            val credentials = ApiUtils.getCredentials(
                currentUser.username,
                currentUser.token
            ) ?: return@runBlocking false
            val fileNames = ProbeConversationAttachmentRequestDto().apply {
                fileNames = listOf(fileName)
                this.allowUpdate = allowUpdate
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
            // The same id on every run, so a run after the upload does not upload to another path.
            val tempRemotePath = workspace.uploadedPath() ?: "/$draftFolderPath/${workspace.uploadId()}-$fileName"

            if (workspace.uploadedPath() == null) {
                if (!uploadToDraftFolder(checkNotNull(sourceFileUri), tempRemotePath)) {
                    return@runBlocking false
                }
                workspace.markUploaded(tempRemotePath, fileName)
            }
            if (isCancelled()) {
                return@runBlocking false
            }

            val params = PostConversationAttachmentRequestDto().apply {
                filePath = tempRemotePath
                referenceId = this@UploadAndShareFilesWorker.referenceId.orEmpty()
                talkMetaData = metaData
                fileName = predictedName
                this.allowUpdate = allowUpdate
            }

            // Errors are not caught: an IOException of a lost network must reach the retry policy.
            ncApiCoroutines.postConversationAttachment(
                credentials,
                ApiUtils.getUrlForChatAttachment(ApiUtils.API_V1, currentUser.baseUrl, roomToken),
                params
            )
            true
        }

    private suspend fun uploadToDraftFolder(sourceFileUri: Uri, tempRemotePath: String): Boolean =
        if (isChunkedUploading) {
            val mimeType = FileUtils.resolveMimeType(context, sourceFileUri)?.toMediaTypeOrNull()
            startChunkedUpload(newChunkedUploader(), mimeType, tempRemotePath)
        } else {
            FileUploader(okHttpClient, context, currentUser, roomToken, ncApi, file!!, ncApiCoroutines)
                .uploadToConversationSubfolder(sourceFileUri, tempRemotePath)
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

    private fun resolveFinalFileName(originalName: String, probeData: ChatProbeAttachmentDataDto): String =
        probeData.renames?.get(originalName) ?: originalName

    private fun getRemotePath(currentUser: User): String {
        val remotePath = CapabilitiesUtil.getAttachmentFolder(
            currentUser.capabilities!!.spreedCapability!!
        ) + "/" + fileName
        return RemoteFileUtils.getNewPathIfFileExists(ncApi, currentUser, remotePath)
    }

    override fun onTransferProgress(percentage: Int) {
        setProgressAsync(Data.Builder().putInt(PROGRESS_KEY, percentage).build())
        if (progressThrottle.shouldUpdate(percentage)) {
            if (isCancelled()) {
                // Interrupts the upload; doWork then sees the flag and aborts.
                chunkedFileUploader?.stop()
            } else if (isForeground && !isStopped) {
                notificationManager.notify(notificationId, progressNotification(percentage))
            }
        }
    }

    private fun updatePlaceholderStatus(status: SendStatus) {
        val refId = referenceId ?: return
        val convId = internalConversationId ?: return
        val entity = runBlocking { chatDao.getTempMessageForConversation(convId, refId, null).firstOrNull() }
        entity?.let { chatDao.updateChatMessage(it.copy(sendStatus = status)) }
    }

    /**
     * WorkManager runs this inside a coroutine cancellation handler: an exception thrown here crashes the process,
     * and a network call blocks WorkManager's own thread. So it only interrupts the upload and keeps the parts on
     * the server, so the next run resumes. The user's cancel does not come here, see [isCancelled].
     */
    @Suppress("Detekt.TooGenericExceptionCaught")
    override fun onStopped() {
        try {
            chunkedFileUploader?.stop()
            uploadDisposable?.dispose()
            uploadLatch?.countDown()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to interrupt the upload", e)
        }
        super.onStopped()
    }

    private fun showFailedToUploadNotification() {
        val failureTitle = getResourceString(context, R.string.nc_upload_failed_notification_title)
        val failureText = String.format(
            getResourceString(context, R.string.nc_upload_failed_notification_text),
            failedFileName()
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

        notificationManager.notify(SystemClock.uptimeMillis().toInt(), failureNotification)
    }

    // The upload can fail before the file name is known, e.g. when its account does not exist anymore.
    private fun failedFileName(): String =
        if (::fileName.isInitialized) {
            fileName
        } else {
            inputData.getString(DEVICE_SOURCE_FILE)?.toUri()?.lastPathSegment.orEmpty()
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
        private const val ALLOW_UPDATE = "ALLOW_UPDATE"
        private const val CHUNK_UPLOAD_THRESHOLD_SIZE: Long = 1024 * 1024

        private const val WORKSPACE_DIR = "uploads"
        const val REQUEST_PERMISSION = 3123

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
            userId: Long,
            fileUri: String,
            roomToken: String,
            conversationName: String,
            metaData: String?,
            referenceId: String = "",
            internalConversationId: String = "",
            compressImages: Boolean = false,
            allowUpdate: Boolean = false
        ): UUID {
            val data: Data = Data.Builder()
                .putLong(KEY_INTERNAL_USER_ID, userId)
                .putString(DEVICE_SOURCE_FILE, fileUri)
                .putString(ROOM_TOKEN, roomToken)
                .putString(CONVERSATION_NAME, conversationName)
                .putString(META_DATA, metaData)
                .putString(KEY_REFERENCE_ID, referenceId)
                .putString(KEY_INTERNAL_CONVERSATION_ID, internalConversationId)
                .putBoolean(COMPRESS_IMAGES, compressImages)
                .putBoolean(ALLOW_UPDATE, allowUpdate)
                .build()
            val uploadWorker: OneTimeWorkRequest = OneTimeWorkRequest.Builder(UploadAndShareFilesWorker::class.java)
                .setInputData(data)
                .apply {
                    if (referenceId.isNotEmpty()) {
                        addTag(referenceTag(referenceId))
                    }
                }
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .setBackoffCriteria(
                    BackoffPolicy.LINEAR,
                    WorkRequest.MIN_BACKOFF_MILLIS,
                    TimeUnit.MILLISECONDS
                )
                .build()
            // Chained per conversation (not enqueueUniqueWork(fileUri, ...), which ran every upload
            // fully independently) so multiple files - whether from one multi-file share or several
            // sends in a row - actually upload and share to the server in the order they were sent,
            // instead of each finishing (and so appearing in chat) whenever its own network calls
            // happen to complete. APPEND_OR_REPLACE rather than APPEND: if the file ahead in the
            // queue was cancelled or failed, this starts a fresh chain instead of cascading that
            // failure onto every file queued behind it.
            WorkManager.getInstance().enqueueUniqueWork(
                uploadQueueName(internalConversationId),
                ExistingWorkPolicy.APPEND_OR_REPLACE,
                uploadWorker
            )
            return uploadWorker.id
        }

        private fun uploadQueueName(internalConversationId: String) = "upload_queue_$internalConversationId"

        private fun referenceTag(referenceId: String) = "upload_ref:$referenceId"

        private fun workspaceDir(context: Context, workId: UUID) =
            File(File(context.noBackupFilesDir, WORKSPACE_DIR), workId.toString())

        /**
         * Asks an upload to cancel itself. Only a flag in the workspace of the work is set: whether the work waits
         * for the network or runs right now, it sees the flag and aborts (see [isCancelled]). Nothing is cancelled
         * in WorkManager. The id is only known in memory of the chat, so after a restart the work is found by its
         * reference id tag. Blocks on WorkManager: call it off the main thread.
         */
        @Suppress("Detekt.TooGenericExceptionCaught")
        fun cancelUpload(referenceId: String, workId: UUID?) {
            // Called from the chat and from the notification right when an upload may be ending, so a failed
            // lookup or a workspace deleted meanwhile must not crash the app.
            try {
                val context = NextcloudTalkApplication.sharedApplication!!.applicationContext
                val workManager = WorkManager.getInstance(context)
                val infos = if (workId != null) {
                    listOfNotNull(workManager.getWorkInfoById(workId).get())
                } else if (referenceId.isNotEmpty()) {
                    workManager.getWorkInfosByTag(referenceTag(referenceId)).get()
                } else {
                    emptyList()
                }
                infos.filter { !it.state.isFinished }.forEach { info ->
                    UploadWorkspace(workspaceDir(context, info.id)).markCancelled()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to cancel upload", e)
            }
        }
    }
}
