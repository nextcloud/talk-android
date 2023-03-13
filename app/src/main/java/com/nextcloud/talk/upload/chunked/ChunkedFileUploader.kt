/*
 *   Nextcloud Talk application
 *
 *   Copyright (C) 2015 ownCloud Inc.
 *   Copyright (C) 2022 Nextcloud GmbH
 *
 *   Permission is hereby granted, free of charge, to any person obtaining a copy
 *   of this software and associated documentation files (the "Software"), to deal
 *   in the Software without restriction, including without limitation the rights
 *   to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 *   copies of the Software, and to permit persons to whom the Software is
 *   furnished to do so, subject to the following conditions:
 *
 *   The above copyright notice and this permission notice shall be included in
 *   all copies or substantial portions of the Software.
 *
 *   THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
 *   EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
 *   MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
 *   NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS
 *   BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN
 *   ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN
 *   CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 *   THE SOFTWARE.
 *
 */

package com.nextcloud.talk.upload.chunked

import android.net.Uri
import android.text.TextUtils
import android.util.Log
import at.bitfire.dav4jvm.DavResource
import at.bitfire.dav4jvm.Property
import at.bitfire.dav4jvm.exception.DavException
import at.bitfire.dav4jvm.exception.HttpException
import at.bitfire.dav4jvm.property.DisplayName
import at.bitfire.dav4jvm.property.GetContentType
import at.bitfire.dav4jvm.property.GetLastModified
import at.bitfire.dav4jvm.property.ResourceType
import autodagger.AutoInjector
import com.nextcloud.talk.application.NextcloudTalkApplication
import com.nextcloud.talk.components.filebrowser.models.DavResponse
import com.nextcloud.talk.components.filebrowser.models.properties.NCEncrypted
import com.nextcloud.talk.components.filebrowser.models.properties.NCPermission
import com.nextcloud.talk.components.filebrowser.models.properties.NCPreview
import com.nextcloud.talk.components.filebrowser.models.properties.OCFavorite
import com.nextcloud.talk.components.filebrowser.models.properties.OCId
import com.nextcloud.talk.components.filebrowser.models.properties.OCSize
import com.nextcloud.talk.dagger.modules.RestModule
import com.nextcloud.talk.data.user.model.User
import com.nextcloud.talk.jobs.ShareOperationWorker
import com.nextcloud.talk.remotefilebrowser.model.RemoteFileBrowserItem
import com.nextcloud.talk.utils.ApiUtils
import com.nextcloud.talk.utils.FileUtils
import com.nextcloud.talk.utils.Mimetype
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.nio.channels.FileChannel
import java.util.Locale

@AutoInjector(NextcloudTalkApplication::class)
class ChunkedFileUploader(
    okHttpClient: OkHttpClient,
    val currentUser: User,
    val roomToken: String,
    val metaData: String?,
    val listener: OnDataTransferProgressListener
) {

    private var okHttpClientNoRedirects: OkHttpClient? = null
    private var remoteChunkUrl: String

    init {
        initHttpClient(okHttpClient, currentUser)
        remoteChunkUrl = ApiUtils.getUrlForChunkedUpload(currentUser.baseUrl, currentUser.userId)
    }

    @Suppress("Detekt.TooGenericExceptionCaught")
    fun upload(
        localFile: File,
        mimeType: MediaType?,
        targetPath: String
    ): Boolean {
        try {
            val uploadFolderUri: String = remoteChunkUrl + "/" + FileUtils.md5Sum(localFile)
            val davResource = DavResource(
                okHttpClientNoRedirects!!,
                uploadFolderUri.toHttpUrlOrNull()!!
            )

            createFolder(davResource)

            val chunksOnServer: MutableList<Chunk> = getUploadedChunks(davResource, uploadFolderUri)
            Log.d(TAG, "chunksOnServer: " + chunksOnServer.size)

            val missingChunks: List<Chunk> = checkMissingChunks(chunksOnServer, localFile.length())
            Log.d(TAG, "missingChunks: " + missingChunks.size)

            for (missingChunk in missingChunks) {
                uploadChunk(localFile, uploadFolderUri, mimeType, missingChunk, missingChunk.length())
            }

            assembleChunks(uploadFolderUri, targetPath)
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Something went wrong in ChunkedFileUploader", e)
            return false
        }
    }

    @Suppress("Detekt.ThrowsCount")
    private fun createFolder(davResource: DavResource) {
        try {
            davResource.mkCol(
                xmlBody = null
            ) { response: Response ->
                if (!response.isSuccessful) {
                    throw IOException("failed to create folder. response code: " + response.code)
                }
            }
        } catch (e: IOException) {
            throw IOException("failed to create folder", e)
        } catch (e: HttpException) {
            if (e.code == METHOD_NOT_ALLOWED_CODE) {
                Log.d(TAG, "Folder most probably already exists, that's okay, just continue..")
            } else {
                throw IOException("failed to create folder", e)
            }
        }
    }

    @Suppress("Detekt.ComplexMethod")
    private fun getUploadedChunks(
        davResource: DavResource,
        uploadFolderUri: String
    ): MutableList<Chunk> {
        val davResponse = DavResponse()
        val memberElements: MutableList<at.bitfire.dav4jvm.Response> = ArrayList()
        val rootElement = arrayOfNulls<at.bitfire.dav4jvm.Response>(1)
        val remoteFiles: MutableList<RemoteFileBrowserItem> = ArrayList()
        try {
            davResource.propfind(
                1
            ) { response: at.bitfire.dav4jvm.Response, hrefRelation: at.bitfire.dav4jvm.Response.HrefRelation? ->
                davResponse.setResponse(response)
                when (hrefRelation) {
                    at.bitfire.dav4jvm.Response.HrefRelation.MEMBER -> memberElements.add(response)
                    at.bitfire.dav4jvm.Response.HrefRelation.SELF -> rootElement[0] = response
                    at.bitfire.dav4jvm.Response.HrefRelation.OTHER -> {}
                    else -> {}
                }
                Unit
            }
        } catch (e: IOException) {
            throw IOException("Error reading remote path", e)
        } catch (e: DavException) {
            throw IOException("Error reading remote path", e)
        }
        for (memberElement in memberElements) {
            remoteFiles.add(
                getModelFromResponse(
                    memberElement,
                    memberElement
                        .href
                        .toString()
                        .substring(uploadFolderUri.length)
                )
            )
        }

        val chunksOnServer: MutableList<Chunk> = ArrayList()

        for (remoteFile in remoteFiles) {
            if (!".file".equals(remoteFile.displayName, ignoreCase = true) && remoteFile.isFile) {
                val part: List<String> = remoteFile.displayName!!.split("-")
                chunksOnServer.add(
                    Chunk(
                        part[0].toLong(),
                        part[1].toLong()
                    )
                )
            }
        }
        return chunksOnServer
    }

    private fun checkMissingChunks(chunks: List<Chunk>, length: Long): List<Chunk> {
        val missingChunks: MutableList<Chunk> = java.util.ArrayList()
        var start: Long = 0
        while (start <= length) {
            val nextChunk: Chunk? = findNextFittingChunk(chunks, start)
            if (nextChunk == null) {
                // create new chunk
                val end: Long = if (start + CHUNK_SIZE <= length) {
                    start + CHUNK_SIZE - 1
                } else {
                    length
                }
                missingChunks.add(Chunk(start, end))
                start = end + 1
            } else if (nextChunk.start == start) {
                // go to next
                start += nextChunk.length()
            } else {
                // fill the gap
                missingChunks.add(Chunk(start, nextChunk.start - 1))
                start = nextChunk.start
            }
        }
        return missingChunks
    }

    private fun findNextFittingChunk(chunks: List<Chunk>, start: Long): Chunk? {
        for (chunk in chunks) {
            if (chunk.start >= start && chunk.start - start <= CHUNK_SIZE) {
                return chunk
            }
        }
        return null
    }

    private fun uploadChunk(
        localFile: File,
        uploadFolderUri: String,
        mimeType: MediaType?,
        chunk: Chunk,
        chunkSize: Long
    ) {
        val startString = java.lang.String.format(Locale.ROOT, "%016d", chunk.start)
        val endString = java.lang.String.format(Locale.ROOT, "%016d", chunk.end)

        var raf: RandomAccessFile? = null
        var channel: FileChannel? = null
        try {
            raf = RandomAccessFile(localFile, "r")
            channel = raf.channel

            // Log.d(TAG, "chunkSize:$chunkSize")
            // Log.d(TAG, "chunk.length():${chunk.length()}")
            // Log.d(TAG, "chunk.start:${chunk.start}")
            // Log.d(TAG, "chunk.end:${chunk.end}")

            val chunkFromFileRequestBody = ChunkFromFileRequestBody(
                localFile,
                mimeType,
                channel,
                chunkSize,
                chunk.start,
                listener
            )

            val chunkUri = "$uploadFolderUri/$startString-$endString"

            val davResource = DavResource(
                okHttpClientNoRedirects!!,
                chunkUri.toHttpUrlOrNull()!!
            )
            davResource.put(
                chunkFromFileRequestBody
            ) { response: Response ->
                if (!response.isSuccessful) {
                    throw IOException("Failed to upload chunk. response code: " + response.code)
                }
            }
        } finally {
            if (channel != null) {
                try {
                    channel.close()
                } catch (e: IOException) {
                    Log.e(TAG, "Error closing file channel!", e)
                }
            }
            if (raf != null) {
                try {
                    raf.close()
                } catch (e: IOException) {
                    Log.e(TAG, "Error closing file access!", e)
                }
            }
        }
    }

    // @RequiresApi(Build.VERSION_CODES.O)
    private fun initHttpClient(okHttpClient: OkHttpClient, currentUser: User) {
        val okHttpClientBuilder: OkHttpClient.Builder = okHttpClient.newBuilder()
        okHttpClientBuilder.followRedirects(false)
        okHttpClientBuilder.followSslRedirects(false)
        // okHttpClientBuilder.readTimeout(Duration.ofMinutes(30)) // TODO set timeout
        okHttpClientBuilder.protocols(listOf(Protocol.HTTP_1_1))
        okHttpClientBuilder.authenticator(
            RestModule.HttpAuthenticator(
                ApiUtils.getCredentials(
                    currentUser.username,
                    currentUser.token
                ),
                "Authorization"
            )
        )
        this.okHttpClientNoRedirects = okHttpClientBuilder.build()
    }

    private fun assembleChunks(uploadFolderUri: String, targetPath: String) {
        val destinationUri: String = ApiUtils.getUrlForFileUpload(
            currentUser.baseUrl,
            currentUser.userId,
            targetPath
        )
        val originUri = "$uploadFolderUri/.file"

        DavResource(
            okHttpClientNoRedirects!!,
            originUri.toHttpUrlOrNull()!!
        ).move(
            destinationUri.toHttpUrlOrNull()!!,
            true
        ) { response: Response ->
            if (response.isSuccessful) {
                ShareOperationWorker.shareFile(
                    roomToken,
                    currentUser,
                    targetPath,
                    metaData
                )
            } else {
                throw IOException("Failed to assemble chunks. response code: " + response.code)
            }
        }
    }

    private fun getModelFromResponse(response: at.bitfire.dav4jvm.Response, remotePath: String): RemoteFileBrowserItem {
        val remoteFileBrowserItem = RemoteFileBrowserItem()
        remoteFileBrowserItem.path = Uri.decode(remotePath)
        remoteFileBrowserItem.displayName = Uri.decode(File(remotePath).name)
        val properties = response.properties
        for (property in properties) {
            mapPropertyToBrowserFile(property, remoteFileBrowserItem)
        }
        if (remoteFileBrowserItem.permissions != null &&
            remoteFileBrowserItem.permissions!!.contains(READ_PERMISSION)
        ) {
            remoteFileBrowserItem.isAllowedToReShare = true
        }
        if (TextUtils.isEmpty(remoteFileBrowserItem.mimeType) && !remoteFileBrowserItem.isFile) {
            remoteFileBrowserItem.mimeType = Mimetype.FOLDER
        }

        return remoteFileBrowserItem
    }

    @Suppress("Detekt.ComplexMethod")
    private fun mapPropertyToBrowserFile(property: Property, remoteFileBrowserItem: RemoteFileBrowserItem) {
        when (property) {
            is OCId -> {
                remoteFileBrowserItem.remoteId = property.ocId
            }
            is ResourceType -> {
                remoteFileBrowserItem.isFile = !property.types.contains(ResourceType.COLLECTION)
            }
            is GetLastModified -> {
                remoteFileBrowserItem.modifiedTimestamp = property.lastModified
            }
            is GetContentType -> {
                remoteFileBrowserItem.mimeType = property.type
            }
            is OCSize -> {
                remoteFileBrowserItem.size = property.ocSize
            }
            is NCPreview -> {
                remoteFileBrowserItem.hasPreview = property.isNcPreview
            }
            is OCFavorite -> {
                remoteFileBrowserItem.isFavorite = property.isOcFavorite
            }
            is DisplayName -> {
                remoteFileBrowserItem.displayName = property.displayName
            }
            is NCEncrypted -> {
                remoteFileBrowserItem.isEncrypted = property.isNcEncrypted
            }
            is NCPermission -> {
                remoteFileBrowserItem.permissions = property.ncPermission
            }
        }
    }

    companion object {
        private val TAG = ChunkedFileUploader::class.simpleName
        private const val READ_PERMISSION = "R"
        private const val CHUNK_SIZE: Long = 1024000
        private const val METHOD_NOT_ALLOWED_CODE: Int = 405
    }
}
