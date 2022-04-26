package com.nextcloud.talk.utils

import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.util.Log
import android.view.View
import android.widget.ProgressBar
import androidx.core.content.FileProvider
import androidx.work.Data
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.nextcloud.talk.R
import com.nextcloud.talk.activities.FullScreenImageActivity
import com.nextcloud.talk.activities.FullScreenMediaActivity
import com.nextcloud.talk.activities.FullScreenTextViewerActivity
import com.nextcloud.talk.adapters.messages.MagicPreviewMessageViewHolder
import com.nextcloud.talk.jobs.DownloadFileToCacheWorker
import com.nextcloud.talk.models.database.CapabilitiesUtil
import com.nextcloud.talk.models.json.chat.ChatMessage
import com.nextcloud.talk.utils.AccountUtils.canWeOpenFilesApp
import com.nextcloud.talk.utils.bundle.BundleKeys.KEY_ACCOUNT
import com.nextcloud.talk.utils.bundle.BundleKeys.KEY_FILE_ID
import androidx.emoji.widget.EmojiTextView
import com.facebook.drawee.view.SimpleDraweeView
import com.nextcloud.talk.models.database.UserEntity
import java.io.File
import java.util.concurrent.ExecutionException

class FileViewerUtils(private val context: Context, private val userEntity: UserEntity) {

    fun openFile(
        message: ChatMessage,
        progressBar: ProgressBar?,
        messageText: EmojiTextView?,
        previewImage: SimpleDraweeView
    ) {
        val fileName = message.getSelectedIndividualHashMap()[MagicPreviewMessageViewHolder.KEY_NAME]!!
        val mimetype = message.getSelectedIndividualHashMap()[MagicPreviewMessageViewHolder.KEY_MIMETYPE]!!
        val link = message.getSelectedIndividualHashMap()["link"]!!

        val fileId = message.getSelectedIndividualHashMap()[MagicPreviewMessageViewHolder.KEY_ID]!!
        val path = message.getSelectedIndividualHashMap()[MagicPreviewMessageViewHolder.KEY_PATH]!!

        var size = message.getSelectedIndividualHashMap()["size"]
        if (size == null) {
            size = "-1"
        }
        val fileSize = Integer.valueOf(size)

        openFile(
            fileId,
            fileName,
            fileSize,
            path,
            link,
            mimetype,
            progressBar,
            messageText,
            previewImage
        )
    }

    fun openFile(
        fileId: String,
        fileName: String,
        fileSize: Int,
        path: String,
        link: String,
        mimetype: String,
        progressBar: ProgressBar?,
        messageText: EmojiTextView?,
        previewImage: SimpleDraweeView
    ) {
        if (isSupportedForInternalViewer(mimetype) || canBeHandledByExternalApp(mimetype, fileName)) {
            openOrDownloadFile(
                fileName,
                fileId,
                path,
                fileSize,
                mimetype,
                progressBar,
                messageText,
                previewImage
            )
        } else {
            openFileInFilesApp(link, fileId)
        }
    }

    private fun canBeHandledByExternalApp(mimetype: String, fileName: String): Boolean {
        val path: String = context.cacheDir.absolutePath + "/" + fileName
        val file = File(path)
        val intent = Intent(Intent.ACTION_VIEW)
        intent.setDataAndType(Uri.fromFile(file), mimetype)
        return intent.resolveActivity(context.packageManager) != null
    }

    private fun openOrDownloadFile(
        fileName: String,
        fileId: String,
        path: String,
        fileSize: Int,
        mimetype: String,
        progressBar: ProgressBar?,
        messageText: EmojiTextView?,
        previewImage: SimpleDraweeView
    ) {
        val file = File(context.cacheDir, fileName)
        if (file.exists()) {
            openFileByMimetype(fileName!!, mimetype!!)
        } else {
            downloadFileToCache(
                fileName,
                fileId,
                path,
                fileSize,
                mimetype,
                progressBar,
                messageText,
                previewImage
            )
        }
    }

    private fun openFileByMimetype(filename: String, mimetype: String) {
        when (mimetype) {
            "audio/mpeg",
            "audio/wav",
            "audio/ogg",
            "video/mp4",
            "video/quicktime",
            "video/ogg"
            -> openMediaView(filename, mimetype)
            "image/png",
            "image/jpeg",
            "image/gif"
            -> openImageView(filename, mimetype)
            "text/markdown",
            "text/plain"
            -> openTextView(filename, mimetype)
            else
            -> openFileByExternalApp(filename, mimetype)
        }
    }

    private fun openFileByExternalApp(fileName: String, mimetype: String) {
        val path = context.cacheDir.absolutePath + "/" + fileName
        val file = File(path)
        val intent: Intent
        if (Build.VERSION.SDK_INT < 24) {
            intent = Intent(Intent.ACTION_VIEW)
            intent.setDataAndType(Uri.fromFile(file), mimetype)
            intent.flags = Intent.FLAG_ACTIVITY_NO_HISTORY
        } else {
            intent = Intent()
            intent.action = Intent.ACTION_VIEW
            val pdfURI = FileProvider.getUriForFile(context, context.packageName, file)
            intent.setDataAndType(pdfURI, mimetype)
            intent.flags = Intent.FLAG_ACTIVITY_NO_HISTORY
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        try {
            if (intent.resolveActivity(context.packageManager) != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
            } else {
                Log.e(TAG, "No Application found to open the file. This should have been handled beforehand!")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error while opening file", e)
        }
    }

    fun openFileInFilesApp(link: String, keyID: String) {
        val accountString = userEntity.username + "@" +
            userEntity.baseUrl
                .replace("https://", "")
                .replace("http://", "")

        if (canWeOpenFilesApp(context, accountString)) {
            val filesAppIntent = Intent(Intent.ACTION_VIEW, null)
            val componentName = ComponentName(
                context.getString(R.string.nc_import_accounts_from),
                "com.owncloud.android.ui.activity.FileDisplayActivity"
            )
            filesAppIntent.component = componentName
            filesAppIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            filesAppIntent.setPackage(context.getString(R.string.nc_import_accounts_from))
            filesAppIntent.putExtra(KEY_ACCOUNT, accountString)
            filesAppIntent.putExtra(KEY_FILE_ID, keyID)
            context.startActivity(filesAppIntent)
        } else {
            val browserIntent = Intent(
                Intent.ACTION_VIEW,
                Uri.parse(link)
            )
            browserIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(browserIntent)
        }
    }

    private fun openImageView(filename: String, mimetype: String) {
        val fullScreenImageIntent = Intent(context, FullScreenImageActivity::class.java)
        fullScreenImageIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        fullScreenImageIntent.putExtra("FILE_NAME", filename)
        fullScreenImageIntent.putExtra("IS_GIF", isGif(mimetype))
        context.startActivity(fullScreenImageIntent)
    }

    private fun openMediaView(filename: String, mimetype: String) {
        val fullScreenMediaIntent = Intent(context, FullScreenMediaActivity::class.java)
        fullScreenMediaIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        fullScreenMediaIntent.putExtra("FILE_NAME", filename)
        fullScreenMediaIntent.putExtra("AUDIO_ONLY", isAudioOnly(mimetype))
        context.startActivity(fullScreenMediaIntent)
    }

    private fun openTextView(filename: String, mimetype: String) {
        val fullScreenTextViewerIntent = Intent(context, FullScreenTextViewerActivity::class.java)
        fullScreenTextViewerIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        fullScreenTextViewerIntent.putExtra("FILE_NAME", filename)
        fullScreenTextViewerIntent.putExtra("IS_MARKDOWN", isMarkdown(mimetype))
        context.startActivity(fullScreenTextViewerIntent)
    }

    fun isSupportedForInternalViewer(mimetype: String?): Boolean {
        return when (mimetype) {
            "image/png", "image/jpeg", "image/gif", "audio/mpeg", "audio/wav", "audio/ogg", "video/mp4", "video/quicktime", "video/ogg", "text/markdown", "text/plain" -> true
            else -> false
        }
    }

    private fun isGif(mimetype: String): Boolean {
        return "image/gif" == mimetype
    }

    private fun isMarkdown(mimetype: String): Boolean {
        return "text/markdown" == mimetype
    }

    private fun isAudioOnly(mimetype: String): Boolean {
        return mimetype.startsWith("audio")
    }

    @SuppressLint("LongLogTag")
    private fun downloadFileToCache(
        fileName: String,
        fileId: String,
        path: String,
        fileSize: Int,
        mimetype: String,
        progressBar: ProgressBar?,
        messageText: EmojiTextView?,
        previewImage: SimpleDraweeView
    ) {
        // check if download worker is already running
        val workers = WorkManager.getInstance(context).getWorkInfosByTag(
            fileId!!
        )
        try {
            for (workInfo in workers.get()) {
                if (workInfo.state == WorkInfo.State.RUNNING || workInfo.state == WorkInfo.State.ENQUEUED) {
                    Log.d(TAG, "Download worker for $fileId is already running or scheduled")
                    return
                }
            }
        } catch (e: ExecutionException) {
            Log.e(TAG, "Error when checking if worker already exsists", e)
        } catch (e: InterruptedException) {
            Log.e(TAG, "Error when checking if worker already exsists", e)
        }
        val downloadWorker: OneTimeWorkRequest
        val data: Data = Data.Builder()
            .putString(DownloadFileToCacheWorker.KEY_BASE_URL, userEntity.baseUrl)
            .putString(DownloadFileToCacheWorker.KEY_USER_ID, userEntity.userId)
            .putString(
                DownloadFileToCacheWorker.KEY_ATTACHMENT_FOLDER,
                CapabilitiesUtil.getAttachmentFolder(userEntity)
            )
            .putString(DownloadFileToCacheWorker.KEY_FILE_NAME, fileName)
            .putString(DownloadFileToCacheWorker.KEY_FILE_PATH, path)
            .putInt(DownloadFileToCacheWorker.KEY_FILE_SIZE, fileSize)
            .build()
        downloadWorker = OneTimeWorkRequest.Builder(DownloadFileToCacheWorker::class.java)
            .setInputData(data)
            .addTag(fileId)
            .build()
        WorkManager.getInstance().enqueue(downloadWorker)
        progressBar?.visibility = View.VISIBLE
        WorkManager.getInstance(context).getWorkInfoByIdLiveData(downloadWorker.id)
            .observeForever { workInfo: WorkInfo? ->
                updateViewsByProgress(
                    fileName,
                    mimetype,
                    workInfo!!,
                    progressBar,
                    messageText,
                    previewImage
                )
            }
    }

    private fun updateViewsByProgress(
        fileName: String,
        mimetype: String,
        workInfo: WorkInfo,
        progressBar: ProgressBar?,
        messageText: EmojiTextView?,
        previewImage: SimpleDraweeView
    ) {
        when (workInfo.state) {
            WorkInfo.State.RUNNING -> {
                val progress = workInfo.progress.getInt(DownloadFileToCacheWorker.PROGRESS, -1)
                if (progress > -1) {
                    messageText?.text = String.format(
                        context.resources.getString(R.string.filename_progress),
                        fileName,
                        progress
                    )
                }
            }
            WorkInfo.State.SUCCEEDED -> {
                if (previewImage.isShown) {
                    openFileByMimetype(fileName, mimetype)
                } else {
                    Log.d(
                        TAG, "file " + fileName +
                            " was downloaded but it's not opened because view is not shown on screen"
                    )
                }
                messageText?.text = fileName
                progressBar?.visibility = View.GONE
            }
            WorkInfo.State.FAILED -> {
                messageText?.text = fileName
                progressBar?.visibility = View.GONE
            }
            else -> {
            }
        }
    }

    fun resumeToUpdateViewsByProgress(
        fileName: String,
        fileId: String,
        mimeType: String,
        progressBar: ProgressBar,
        messageText: EmojiTextView?,
        previewImage: SimpleDraweeView
    ) {
        val workers = WorkManager.getInstance(context).getWorkInfosByTag(fileId)

        try {
            for (workInfo in workers.get()) {
                if (workInfo.state == WorkInfo.State.RUNNING ||
                    workInfo.state == WorkInfo.State.ENQUEUED
                ) {
                    progressBar.visibility = View.VISIBLE
                    WorkManager
                        .getInstance(context)
                        .getWorkInfoByIdLiveData(workInfo.id)
                        .observeForever { info: WorkInfo? ->
                            updateViewsByProgress(
                                fileName,
                                mimeType,
                                info!!,
                                progressBar,
                                messageText,
                                previewImage
                            )
                        }
                }
            }
        } catch (e: ExecutionException) {
            Log.e(TAG, "Error when checking if worker already exists", e)
        } catch (e: InterruptedException) {
            Log.e(TAG, "Error when checking if worker already exists", e)
        }
    }

    companion object {
        private val TAG = FileViewerUtils::class.simpleName

        const val KEY_ID = "id"
    }
}