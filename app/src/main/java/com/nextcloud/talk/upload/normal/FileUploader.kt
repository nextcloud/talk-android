package com.nextcloud.talk.upload.normal

import android.content.Context
import android.net.Uri
import android.util.Log
import com.nextcloud.talk.api.NcApi
import com.nextcloud.talk.data.user.model.User
import com.nextcloud.talk.jobs.ShareOperationWorker
import com.nextcloud.talk.utils.ApiUtils
import com.nextcloud.talk.utils.FileUtils
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody
import java.io.InputStream

class FileUploader(
    val context: Context,
    val currentUser: User,
    val roomToken: String,
    val ncApi: NcApi
) {
    fun upload(
        sourceFileUri: Uri,
        fileName: String,
        remotePath: String,
        metaData: String?
    ): Observable<Boolean> {
        return ncApi.uploadFile(
            ApiUtils.getCredentials(currentUser.username, currentUser.token),
            ApiUtils.getUrlForFileUpload(currentUser.baseUrl, currentUser.userId, remotePath),
            createRequestBody(sourceFileUri)
        )
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread()).map { response ->
                if (response.isSuccessful) {
                    ShareOperationWorker.shareFile(
                        roomToken,
                        currentUser,
                        remotePath,
                        metaData
                    )
                    FileUtils.copyFileToCache(context, sourceFileUri, fileName)
                    true
                } else {
                    false
                }
            }
    }

    @Suppress("Detekt.TooGenericExceptionCaught")
    private fun createRequestBody(sourceFileUri: Uri): RequestBody? {
        var requestBody: RequestBody? = null
        try {
            val input: InputStream = context.contentResolver.openInputStream(sourceFileUri)!!
            input.use {
                val buf = ByteArray(input.available())
                while (it.read(buf) != -1) {
                    requestBody = RequestBody.create("application/octet-stream".toMediaTypeOrNull(), buf)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "failed to create RequestBody for $sourceFileUri", e)
        }
        return requestBody
    }

    companion object {
        private val TAG = FileUploader::class.simpleName
    }
}
