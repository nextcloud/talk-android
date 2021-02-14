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

import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import androidx.work.*
import autodagger.AutoInjector
import com.nextcloud.talk.api.NcApi
import com.nextcloud.talk.application.NextcloudTalkApplication
import com.nextcloud.talk.models.database.UserEntity
import com.nextcloud.talk.models.json.generic.GenericOverall
import com.nextcloud.talk.utils.ApiUtils
import com.nextcloud.talk.utils.UriUtils
import com.nextcloud.talk.utils.bundle.BundleKeys.KEY_FILE_PATHS
import com.nextcloud.talk.utils.bundle.BundleKeys.KEY_INTERNAL_USER_ID
import com.nextcloud.talk.utils.bundle.BundleKeys.KEY_ROOM_TOKEN
import com.nextcloud.talk.utils.database.user.UserUtils
import com.nextcloud.talk.utils.preferences.AppPreferences
import io.reactivex.Observer
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import io.reactivex.schedulers.Schedulers
import okhttp3.MediaType
import okhttp3.RequestBody
import retrofit2.Response
import java.io.InputStream
import java.util.*
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

        try {
            val currentUser = userUtils.currentUser
            val sourcefiles = inputData.getStringArray(DEVICE_SOURCEFILES)
            val ncTargetpath = inputData.getString(NC_TARGETPATH)
            val roomToken = inputData.getString(ROOM_TOKEN)

            checkNotNull(currentUser)
            checkNotNull(sourcefiles)
            require(sourcefiles.isNotEmpty())
            checkNotNull(ncTargetpath)
            checkNotNull(roomToken)

            for (index in sourcefiles.indices) {
                val sourcefileUri = Uri.parse(sourcefiles[index])
                var filename = UriUtils.getFileName(sourcefileUri, context)
                val requestBody = createRequestBody(sourcefileUri)
                uploadFile(currentUser, ncTargetpath, filename, roomToken, requestBody)
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
            while (input.read(buf) != -1);
            requestBody = RequestBody.create(MediaType.parse("application/octet-stream"), buf)
        } catch (e: Exception) {
            Log.e(javaClass.simpleName, "failed to create RequestBody for $sourcefileUri", e)
        }
        return requestBody
    }

    private fun uploadFile(currentUser: UserEntity, ncTargetpath: String?, filename: String?, roomToken: String?, requestBody: RequestBody?) {
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
                        shareFile(roomToken, currentUser, ncTargetpath, filename)
                    }
                })
    }

    private fun shareFile(roomToken: String?, currentUser: UserEntity, ncTargetpath: String?, filename: String?) {
        val paths: MutableList<String> = ArrayList()
        paths.add("$ncTargetpath/$filename")

        val data = Data.Builder()
                .putLong(KEY_INTERNAL_USER_ID, currentUser.id)
                .putString(KEY_ROOM_TOKEN, roomToken)
                .putStringArray(KEY_FILE_PATHS, paths.toTypedArray())
                .build()
        val shareWorker = OneTimeWorkRequest.Builder(ShareOperationWorker::class.java)
                .setInputData(data)
                .build()
        WorkManager.getInstance().enqueue(shareWorker)
    }

    companion object {
        const val TAG = "UploadFileWorker"
        const val DEVICE_SOURCEFILES = "DEVICE_SOURCEFILES"
        const val NC_TARGETPATH = "NC_TARGETPATH"
        const val ROOM_TOKEN = "ROOM_TOKEN"
    }
}