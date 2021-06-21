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
import com.nextcloud.talk.models.database.UserEntity
import com.nextcloud.talk.models.json.generic.GenericOverall
import com.nextcloud.talk.utils.ApiUtils
import com.nextcloud.talk.utils.UriUtils
import com.nextcloud.talk.utils.bundle.BundleKeys.KEY_FILE_PATHS
import com.nextcloud.talk.utils.bundle.BundleKeys.KEY_INTERNAL_USER_ID
import com.nextcloud.talk.utils.bundle.BundleKeys.KEY_META_DATA
import com.nextcloud.talk.utils.bundle.BundleKeys.KEY_ROOM_TOKEN
import com.nextcloud.talk.utils.database.user.UserUtils
import com.nextcloud.talk.utils.preferences.AppPreferences
import io.reactivex.Observer
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import io.reactivex.schedulers.Schedulers
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody
import retrofit2.Response
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.ArrayList
import javax.inject.Inject

@AutoInjector(NextcloudTalkApplication::class)
class UploadAndShareFilesWorker(val context: Context, workerParameters: WorkerParameters) :
    Worker(context, workerParameters) {

    @Inject
    lateinit var ncApi: NcApi

    @Inject
    lateinit var userUtils: UserUtils

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

        try {
            val currentUser = userUtils.currentUser
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
                val sourcefileUri = Uri.parse(sourcefiles[index])
                val filename = UriUtils.getFileName(sourcefileUri, context)
                val requestBody = createRequestBody(sourcefileUri)
                uploadFile(currentUser, ncTargetpath, filename, roomToken, requestBody, sourcefileUri, metaData)
            }
        } catch (e: IllegalStateException) {
            Log.e(javaClass.simpleName, "Something went wrong when trying to upload file", e)
            return Result.failure()
        } catch (e: IllegalArgumentException) {
            Log.e(javaClass.simpleName, "Something went wrong when trying to upload file", e)
            return Result.failure()
        }
        return Result.success()
    }

    private fun createRequestBody(sourcefileUri: Uri): RequestBody? {
        var requestBody: RequestBody? = null
        try {
            val input: InputStream = context.contentResolver.openInputStream(sourcefileUri)!!
            val buf = ByteArray(input.available())
            while (input.read(buf) != -1)
                requestBody = RequestBody.create("application/octet-stream".toMediaTypeOrNull(), buf)
        } catch (e: Exception) {
            Log.e(javaClass.simpleName, "failed to create RequestBody for $sourcefileUri", e)
        }
        return requestBody
    }

    private fun uploadFile(
        currentUser: UserEntity,
        ncTargetpath: String?,
        filename: String,
        roomToken: String?,
        requestBody: RequestBody?,
        sourcefileUri: Uri,
        metaData: String?
    ) {
        ncApi.uploadFile(
            ApiUtils.getCredentials(currentUser.username, currentUser.token),
            ApiUtils.getUrlForFileUpload(currentUser.baseUrl, currentUser.userId, ncTargetpath, filename),
            requestBody
        )
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(object : Observer<Response<GenericOverall>> {
                override fun onSubscribe(d: Disposable) {
                }

                override fun onNext(t: Response<GenericOverall>) {
                }

                override fun onError(e: Throwable) {
                    Log.e(TAG, "failed to upload file $filename")
                }

                override fun onComplete() {
                    shareFile(roomToken, currentUser, ncTargetpath, filename, metaData)
                    copyFileToCache(sourcefileUri, filename)
                }
            })
    }

    private fun copyFileToCache(sourceFileUri: Uri, filename: String) {
        val cachedFile = File(context.cacheDir, filename)

        if (cachedFile.exists()) {
            Log.d(TAG, "file is already in cache")
        } else {
            val outputStream = FileOutputStream(cachedFile)
            val inputStream: InputStream = context.contentResolver.openInputStream(sourceFileUri)!!

            inputStream.use { input ->
                outputStream.use { output ->
                    input.copyTo(output)
                }
            }
        }
    }

    private fun shareFile(
        roomToken: String?,
        currentUser: UserEntity,
        ncTargetpath: String?,
        filename: String?,
        metaData: String?) {

        val paths: MutableList<String> = ArrayList()
        paths.add("$ncTargetpath/$filename")

        val data = Data.Builder()
            .putLong(KEY_INTERNAL_USER_ID, currentUser.id)
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
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                return if (PermissionChecker.checkSelfPermission(
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
            } else { // permission is automatically granted on sdk<23 upon installation
                Log.d(TAG, "Permission is granted")
                return true
            }
        }

        fun requestStoragePermission(controller: Controller) {
            controller.requestPermissions(
                arrayOf(
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
                ),
                REQUEST_PERMISSION
            )
        }
    }
}
