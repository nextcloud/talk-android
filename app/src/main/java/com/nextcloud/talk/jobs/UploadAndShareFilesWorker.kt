/*
 * Nextcloud Talk application
 *
 * @author Marcel Hibbe
 * Copyright (C) 2021 Marcel Hibbe <dev@mhibbe.de>
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
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */

package com.nextcloud.talk.jobs

import android.Manifest
import android.content.Context
import android.net.Uri
import android.os.Build
import android.util.Log
import android.webkit.MimeTypeMap
import android.widget.Toast
import androidx.core.content.PermissionChecker
import androidx.work.Data
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import autodagger.AutoInjector
import com.bluelinelabs.conductor.Controller
import com.nextcloud.talk.api.NcApi
import com.nextcloud.talk.application.NextcloudTalkApplication
import com.nextcloud.talk.data.user.model.User
import com.nextcloud.talk.models.json.generic.GenericOverall
import com.nextcloud.talk.users.UserManager
import com.nextcloud.talk.utils.ApiUtils
import com.nextcloud.talk.utils.UriUtils
import com.nextcloud.talk.utils.bundle.BundleKeys.KEY_FILE_PATHS
import com.nextcloud.talk.utils.bundle.BundleKeys.KEY_INTERNAL_USER_ID
import com.nextcloud.talk.utils.bundle.BundleKeys.KEY_META_DATA
import com.nextcloud.talk.utils.bundle.BundleKeys.KEY_ROOM_TOKEN
import com.nextcloud.talk.utils.preferences.AppPreferences
import com.nextcloud.talk.utils.rx.ProgressRequestBody
import io.reactivex.Observer
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import io.reactivex.schedulers.Schedulers
import okhttp3.MultipartBody
import retrofit2.Response
import java.io.File
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import javax.inject.Inject

@AutoInjector(NextcloudTalkApplication::class)
class UploadAndShareFilesWorker(val context: Context, workerParameters: WorkerParameters) :
    Worker(context, workerParameters) {

    @Inject
    lateinit var ncApi: NcApi

    @Inject
    lateinit var userManager: UserManager

    @Inject
    lateinit var appPreferences: AppPreferences

    override fun doWork(): Result {
        NextcloudTalkApplication.sharedApplication!!.componentApplication.inject(this)

        if (!isStoragePermissionGranted(context)) {
            Log.w(
                TAG,
                "Storage permission is not granted. As a developer please make sure you check for" +
                    "permissions via UploadAndShareFilesWorker.isStoragePermissionGranted() and " +
                    "UploadAndShareFilesWorker.requestStoragePermission() beforehand. If you already " +
                    "did but end up with this warning, the user most likely revoked the permission"
            )
        }

        return try {
            val currentUser = userManager.currentUser.blockingGet()
            val sourcefiles = inputData.getStringArray(DEVICE_SOURCEFILES)
            val ncTargetpath = inputData.getString(NC_TARGETPATH)
            val roomToken = inputData.getString(ROOM_TOKEN)
            val metaData = inputData.getString(META_DATA)

            checkNotNull(currentUser)
            checkNotNull(sourcefiles)
            require(sourcefiles.isNotEmpty())
            checkNotNull(ncTargetpath)
            checkNotNull(roomToken)

            for (index in sourcefiles.indices) {
                val sourceFileUri = Uri.parse(sourcefiles[index])
                val fileName = UriUtils.getFileName(sourceFileUri, context)
                uploadFile(
                    currentUser,
                    UploadItem(
                        sourceFileUri,
                        fileName,
                        createRequestBody(sourceFileUri, fileName)
                    ),
                    ncTargetpath,
                    roomToken,
                    metaData
                )
            }
            Result.success()
        } catch (e: IllegalStateException) {
            Log.e(javaClass.simpleName, "Something went wrong when trying to upload file", e)
            Result.failure()
        } catch (e: IllegalArgumentException) {
            Log.e(javaClass.simpleName, "Something went wrong when trying to upload file", e)
            Result.failure()
        }
    }

    private fun createRequestBody(sourceFileUri: Uri, fileName: String): MultipartBody {
        // val inputStream: InputStream? = context.contentResolver.openInputStream(sourceFileUri)
        // return if (inputStream != null) {
        //
        //     val file = File(sourceFileUri.toString())
        //     val fileSize = (file.length() / 1024).toString().toInt()
        //
        //     // val mediaType = "multipart/form-data".toMediaTypeOrNull()
        //     val mediaType = context.contentResolver.getType(sourceFileUri)?.toMediaTypeOrNull()
        //
        //     MultipartBody.Part.createFormData(
        //         "file",
        //         fileName,
        //         inputStream.readBytes().toRequestBody(mediaType)
        //         // inputStream.readBytes().toRequestBody(mediaType, 0, fileSize)
        //     )
        // } else {
        //     throw IllegalArgumentException("inputStream was null when trying to create request body for file upload")
        // }


        // val file = File(sourceFileUri.toString())

        // val inputStream: InputStream? = context.contentResolver.openInputStream(sourceFileUri)

        val file = fileFromContentUri(context, sourceFileUri)
        val mimeType = context.contentResolver.getType(sourceFileUri)!!

        val filePart = ProgressRequestBody(file, mimeType)
        val requestBody = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            // .addFormDataPart("example[name]", "test")
            .addFormDataPart("testvideo",file.name, filePart)
            .build()

        filePart.getProgress()
            .subscribeOn(Schedulers.io())
            .subscribe { percentage ->
                Log.i("progress: ", "${percentage}%")
            }

        return requestBody
    }

    private fun fileFromContentUri(context: Context, contentUri: Uri): File {
        val fileExtension = getFileExtension(context, contentUri)
        val fileName = "temp_file" + if (fileExtension != null) ".$fileExtension" else ""

        val tempFile = File(context.cacheDir, fileName)
        tempFile.createNewFile()

        try {
            val oStream = FileOutputStream(tempFile)
            val inputStream = context.contentResolver.openInputStream(contentUri)

            inputStream?.let {
                copy(inputStream, oStream)
            }

            oStream.flush()
        } catch (e: Exception) {
            Log.e(TAG, "Error while copy stream", e)
        }

        return tempFile
    }

    private fun getFileExtension(context: Context, uri: Uri): String? {
        val fileType: String? = context.contentResolver.getType(uri)
        return MimeTypeMap.getSingleton().getExtensionFromMimeType(fileType)
    }

    @Throws(IOException::class)
    private fun copy(source: InputStream, target: OutputStream) {
        val buf = ByteArray(8192)
        var length: Int
        while (source.read(buf).also { length = it } > 0) {
            target.write(buf, 0, length)
        }
    }

    private fun uploadFile(
        currentUser: User,
        uploadItem: UploadItem,
        ncTargetPath: String?,
        roomToken: String?,
        metaData: String?
    ) {
        ncApi.uploadFile(
            ApiUtils.getCredentials(currentUser.username, currentUser.token),
            ApiUtils.getUrlForFileUpload(currentUser.baseUrl, currentUser.userId, ncTargetPath, uploadItem.fileName),
            uploadItem.multiBody
        )
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(object : Observer<Response<GenericOverall>> {
                override fun onSubscribe(d: Disposable) {
                    // unused atm
                }

                override fun onNext(t: Response<GenericOverall>) {
                    Log.d(TAG, "onNext")
                }

                override fun onError(e: Throwable) {
                    Log.e(TAG, "failed to upload file ${uploadItem.fileName}", e)
                }

                override fun onComplete() {
                    Toast.makeText(context,"Upload SUCCESS!!",Toast.LENGTH_LONG).show()
                    shareFile(roomToken, currentUser, ncTargetPath, uploadItem.fileName, metaData)
                    copyFileToCache(uploadItem.uri, uploadItem.fileName)
                }
            })
    }

    private fun copyFileToCache(sourceFileUri: Uri, filename: String) {
        val cachedFile = File(context.cacheDir, filename)

        if (cachedFile.exists()) {
            Log.d(TAG, "file is already in cache")
        } else {
            val outputStream = FileOutputStream(cachedFile)
            try {
                val inputStream: InputStream? = context.contentResolver.openInputStream(sourceFileUri)
                inputStream?.use { input ->
                    outputStream.use { output ->
                        input.copyTo(output)
                    }
                }
            } catch (e: FileNotFoundException) {
                Log.w(TAG, "failed to copy file to cache", e)
            }
        }
    }

    private fun shareFile(
        roomToken: String?,
        currentUser: User,
        ncTargetpath: String?,
        filename: String?,
        metaData: String?
    ) {
        val paths: MutableList<String> = ArrayList()
        paths.add("$ncTargetpath/$filename")

        val data = Data.Builder()
            .putLong(KEY_INTERNAL_USER_ID, currentUser.id!!)
            .putString(KEY_ROOM_TOKEN, roomToken)
            .putStringArray(KEY_FILE_PATHS, paths.toTypedArray())
            .putString(KEY_META_DATA, metaData)
            .build()
        val shareWorker = OneTimeWorkRequest.Builder(ShareOperationWorker::class.java)
            .setInputData(data)
            .build()
        WorkManager.getInstance().enqueue(shareWorker)
    }

    companion object {
        const val TAG = "UploadFileWorker"
        const val REQUEST_PERMISSION = 3123
        const val DEVICE_SOURCEFILES = "DEVICE_SOURCEFILES"
        const val NC_TARGETPATH = "NC_TARGETPATH"
        const val ROOM_TOKEN = "ROOM_TOKEN"
        const val META_DATA = "META_DATA"

        fun isStoragePermissionGranted(context: Context): Boolean {
            return when {
                Build.VERSION.SDK_INT > Build.VERSION_CODES.Q -> {
                    if (PermissionChecker.checkSelfPermission(
                            context,
                            Manifest.permission.READ_EXTERNAL_STORAGE
                        ) == PermissionChecker.PERMISSION_GRANTED
                    ) {
                        Log.d(TAG, "Permission is granted (SDK 30 or greater)")
                        true
                    } else {
                        Log.d(TAG, "Permission is revoked (SDK 30 or greater)")
                        false
                    }
                }
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.M -> {
                    if (PermissionChecker.checkSelfPermission(
                            context,
                            Manifest.permission.WRITE_EXTERNAL_STORAGE
                        ) == PermissionChecker.PERMISSION_GRANTED
                    ) {
                        Log.d(TAG, "Permission is granted")
                        true
                    } else {
                        Log.d(TAG, "Permission is revoked")
                        false
                    }
                }
                else -> { // permission is automatically granted on sdk<23 upon installation
                    Log.d(TAG, "Permission is granted")
                    true
                }
            }
        }

        fun requestStoragePermission(controller: Controller) {
            when {
                Build.VERSION.SDK_INT > Build.VERSION_CODES.Q -> {
                    controller.requestPermissions(
                        arrayOf(
                            Manifest.permission.READ_EXTERNAL_STORAGE
                        ),
                        REQUEST_PERMISSION
                    )
                }
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.M -> {
                    controller.requestPermissions(
                        arrayOf(
                            Manifest.permission.WRITE_EXTERNAL_STORAGE
                        ),
                        REQUEST_PERMISSION
                    )
                }
                else -> { // permission is automatically granted on sdk<23 upon installation
                }
            }
        }
    }

    private data class UploadItem(
        val uri: Uri,
        val fileName: String,
        val multiBody: MultipartBody
    )
}
