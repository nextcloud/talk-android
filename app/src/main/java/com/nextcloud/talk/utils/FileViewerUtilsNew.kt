/*
 * Nextcloud Talk application
 *
 * @author Marcel Hibbe
 * Copyright (C) 2022 Marcel Hibbe <dev@mhibbe.de>
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
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.emoji.widget.EmojiTextView
import androidx.work.Data
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.facebook.drawee.view.SimpleDraweeView
import com.nextcloud.talk.R
import com.nextcloud.talk.activities.FullScreenImageActivity
import com.nextcloud.talk.activities.FullScreenMediaActivity
import com.nextcloud.talk.activities.FullScreenTextViewerActivity
import com.nextcloud.talk.adapters.messages.MagicPreviewMessageViewHolder
import com.nextcloud.talk.data.user.model.User
import com.nextcloud.talk.jobs.DownloadFileToCacheWorker
import com.nextcloud.talk.models.json.chat.ChatMessage
import com.nextcloud.talk.utils.AccountUtils.canWeOpenFilesApp
import com.nextcloud.talk.utils.Mimetype.AUDIO_MPEG
import com.nextcloud.talk.utils.Mimetype.AUDIO_OGG
import com.nextcloud.talk.utils.Mimetype.AUDIO_PREFIX
import com.nextcloud.talk.utils.Mimetype.AUDIO_WAV
import com.nextcloud.talk.utils.Mimetype.IMAGE_GIF
import com.nextcloud.talk.utils.Mimetype.IMAGE_JPEG
import com.nextcloud.talk.utils.Mimetype.IMAGE_PNG
import com.nextcloud.talk.utils.Mimetype.TEXT_MARKDOWN
import com.nextcloud.talk.utils.Mimetype.TEXT_PLAIN
import com.nextcloud.talk.utils.Mimetype.VIDEO_MP4
import com.nextcloud.talk.utils.Mimetype.VIDEO_OGG
import com.nextcloud.talk.utils.Mimetype.VIDEO_QUICKTIME
import com.nextcloud.talk.utils.bundle.BundleKeys.KEY_ACCOUNT
import com.nextcloud.talk.utils.bundle.BundleKeys.KEY_FILE_ID
import com.nextcloud.talk.utils.database.user.CapabilitiesUtilNew
import java.io.File
import java.util.concurrent.ExecutionException

/*
 * Usage of this class forces us to do things at one location which should be separated in a activity and view model.
 *
 * Example:
 *   - SharedItemsViewHolder
 */
class FileViewerUtilsNew(private val context: Context, private val userEntity: User) {

    fun openFile(
        message: ChatMessage,
        progressUi: ProgressUi
    ) {
        val fileName = message.selectedIndividualHashMap!![MagicPreviewMessageViewHolder.KEY_NAME]!!
        val mimetype = message.selectedIndividualHashMap!![MagicPreviewMessageViewHolder.KEY_MIMETYPE]!!
        val link = message.selectedIndividualHashMap!!["link"]!!

        val fileId = message.selectedIndividualHashMap!![MagicPreviewMessageViewHolder.KEY_ID]!!
        val path = message.selectedIndividualHashMap!![MagicPreviewMessageViewHolder.KEY_PATH]!!

        var size = message.selectedIndividualHashMap!!["size"]
        if (size == null) {
            size = "-1"
        }
        val fileSize = size.toLong()

        openFile(
            FileInfo(fileId, fileName, fileSize),
            path,
            link,
            mimetype,
            progressUi
        )
    }

    fun openFile(
        fileInfo: FileInfo,
        path: String,
        link: String?,
        mimetype: String?,
        progressUi: ProgressUi
    ) {
        if (isSupportedForInternalViewer(mimetype) || canBeHandledByExternalApp(mimetype, fileInfo.fileName)) {
            openOrDownloadFile(
                fileInfo,
                path,
                mimetype,
                progressUi
            )
        } else if (!link.isNullOrEmpty()) {
            openFileInFilesApp(link, fileInfo.fileId)
        } else {
            Log.e(
                TAG,
                "File with id " + fileInfo.fileId + " can't be opened because internal viewer doesn't " +
                    "support it, it can't be handled by an external app and there is no link " +
                    "to open it in the nextcloud files app"
            )
            Toast.makeText(context, R.string.nc_common_error_sorry, Toast.LENGTH_LONG).show()
        }
    }

    private fun canBeHandledByExternalApp(mimetype: String?, fileName: String): Boolean {
        val path: String = context.cacheDir.absolutePath + "/" + fileName
        val file = File(path)
        val intent = Intent(Intent.ACTION_VIEW)
        intent.setDataAndType(Uri.fromFile(file), mimetype)
        return intent.resolveActivity(context.packageManager) != null
    }

    private fun openOrDownloadFile(
        fileInfo: FileInfo,
        path: String,
        mimetype: String?,
        progressUi: ProgressUi
    ) {
        val file = File(context.cacheDir, fileInfo.fileName)
        if (file.exists()) {
            openFileByMimetype(fileInfo.fileName!!, mimetype)
        } else {
            downloadFileToCache(
                fileInfo,
                path,
                mimetype,
                progressUi
            )
        }
    }

    private fun openFileByMimetype(filename: String, mimetype: String?) {
        if (mimetype != null) {
            when (mimetype) {
                AUDIO_MPEG,
                AUDIO_WAV,
                AUDIO_OGG,
                VIDEO_MP4,
                VIDEO_QUICKTIME,
                VIDEO_OGG
                -> openMediaView(filename, mimetype)
                IMAGE_PNG,
                IMAGE_JPEG,
                IMAGE_GIF
                -> openImageView(filename, mimetype)
                TEXT_MARKDOWN,
                TEXT_PLAIN
                -> openTextView(filename, mimetype)
                else
                -> openFileByExternalApp(filename, mimetype)
            }
        } else {
            Log.e(TAG, "can't open file with unknown mimetype")
            Toast.makeText(context, R.string.nc_common_error_sorry, Toast.LENGTH_LONG).show()
        }
    }

    @Suppress("Detekt.TooGenericExceptionCaught")
    private fun openFileByExternalApp(fileName: String, mimetype: String) {
        val path = context.cacheDir.absolutePath + "/" + fileName
        val file = File(path)
        val intent: Intent
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
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
                ?.replace("https://", "")
                ?.replace("http://", "")

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
            IMAGE_PNG,
            IMAGE_JPEG,
            IMAGE_GIF,
            AUDIO_MPEG,
            AUDIO_WAV,
            AUDIO_OGG,
            VIDEO_MP4,
            VIDEO_QUICKTIME,
            VIDEO_OGG,
            TEXT_MARKDOWN,
            TEXT_PLAIN -> true
            else -> false
        }
    }

    private fun isGif(mimetype: String): Boolean {
        return IMAGE_GIF == mimetype
    }

    private fun isMarkdown(mimetype: String): Boolean {
        return TEXT_MARKDOWN == mimetype
    }

    private fun isAudioOnly(mimetype: String): Boolean {
        return mimetype.startsWith(AUDIO_PREFIX)
    }

    @SuppressLint("LongLogTag")
    private fun downloadFileToCache(
        fileInfo: FileInfo,
        path: String,
        mimetype: String?,
        progressUi: ProgressUi
    ) {
        // check if download worker is already running
        val workers = WorkManager.getInstance(context).getWorkInfosByTag(fileInfo.fileId!!)
        try {
            for (workInfo in workers.get()) {
                if (workInfo.state == WorkInfo.State.RUNNING || workInfo.state == WorkInfo.State.ENQUEUED) {
                    Log.d(TAG, "Download worker for $fileInfo.fileId is already running or scheduled")
                    return
                }
            }
        } catch (e: ExecutionException) {
            Log.e(TAG, "Error when checking if worker already exists", e)
        } catch (e: InterruptedException) {
            Log.e(TAG, "Error when checking if worker already exists", e)
        }
        val downloadWorker: OneTimeWorkRequest

        val size: Long = if (fileInfo.fileSize == null) {
            -1
        } else {
            fileInfo.fileSize!!
        }

        val data: Data = Data.Builder()
            .putString(DownloadFileToCacheWorker.KEY_BASE_URL, userEntity.baseUrl)
            .putString(DownloadFileToCacheWorker.KEY_USER_ID, userEntity.userId)
            .putString(
                DownloadFileToCacheWorker.KEY_ATTACHMENT_FOLDER,
                CapabilitiesUtilNew.getAttachmentFolder(userEntity)
            )
            .putString(DownloadFileToCacheWorker.KEY_FILE_NAME, fileInfo.fileName)
            .putString(DownloadFileToCacheWorker.KEY_FILE_PATH, path)
            .putLong(DownloadFileToCacheWorker.KEY_FILE_SIZE, size)
            .build()

        downloadWorker = OneTimeWorkRequest.Builder(DownloadFileToCacheWorker::class.java)
            .setInputData(data)
            .addTag(fileInfo.fileId)
            .build()
        WorkManager.getInstance().enqueue(downloadWorker)
        progressUi.progressBar?.visibility = View.VISIBLE
        WorkManager.getInstance(context).getWorkInfoByIdLiveData(downloadWorker.id)
            .observeForever { workInfo: WorkInfo? ->
                updateViewsByProgress(
                    fileInfo.fileName,
                    mimetype,
                    workInfo!!,
                    progressUi
                )
            }
    }

    private fun updateViewsByProgress(
        fileName: String,
        mimetype: String?,
        workInfo: WorkInfo,
        progressUi: ProgressUi
    ) {
        when (workInfo.state) {
            WorkInfo.State.RUNNING -> {
                val progress = workInfo.progress.getInt(DownloadFileToCacheWorker.PROGRESS, -1)
                if (progress > -1) {
                    progressUi.messageText?.text = String.format(
                        context.resources.getString(R.string.filename_progress),
                        fileName,
                        progress
                    )
                }
            }
            WorkInfo.State.SUCCEEDED -> {
                if (progressUi.previewImage.isShown) {
                    openFileByMimetype(fileName, mimetype)
                } else {
                    Log.d(
                        TAG,
                        "file " + fileName +
                            " was downloaded but it's not opened because view is not shown on screen"
                    )
                }
                progressUi.messageText?.text = fileName
                progressUi.progressBar?.visibility = View.GONE
            }
            WorkInfo.State.FAILED -> {
                progressUi.messageText?.text = fileName
                progressUi.progressBar?.visibility = View.GONE
            }
            else -> {
            }
        }
    }

    fun resumeToUpdateViewsByProgress(
        fileName: String,
        fileId: String,
        mimeType: String?,
        progressUi: ProgressUi
    ) {
        val workers = WorkManager.getInstance(context).getWorkInfosByTag(fileId)

        try {
            for (workInfo in workers.get()) {
                if (workInfo.state == WorkInfo.State.RUNNING ||
                    workInfo.state == WorkInfo.State.ENQUEUED
                ) {
                    progressUi.progressBar?.visibility = View.VISIBLE
                    WorkManager
                        .getInstance(context)
                        .getWorkInfoByIdLiveData(workInfo.id)
                        .observeForever { info: WorkInfo? ->
                            updateViewsByProgress(
                                fileName,
                                mimeType,
                                info!!,
                                progressUi
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

    data class ProgressUi(
        val progressBar: ProgressBar?,
        val messageText: EmojiTextView?,
        val previewImage: SimpleDraweeView
    )

    data class FileInfo(
        val fileId: String,
        val fileName: String,
        var fileSize: Long?
    )

    companion object {
        private val TAG = FileViewerUtilsNew::class.simpleName
    }
}
