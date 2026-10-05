/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2022 Nextcloud GmbH
 * SPDX-FileCopyrightText: 2015 ownCloud GmbH
 * SPDX-License-Identifier: MIT
 */
package com.nextcloud.talk.upload.chunked

import android.net.Uri
import android.text.TextUtils
import android.util.Log
import at.bitfire.dav4jvm.DavResource
import at.bitfire.dav4jvm.Property
import at.bitfire.dav4jvm.exception.DavException
import at.bitfire.dav4jvm.exception.HttpException
import at.bitfire.dav4jvm.exception.NotFoundException
import at.bitfire.dav4jvm.property.DisplayName
import at.bitfire.dav4jvm.property.GetContentLength
import at.bitfire.dav4jvm.property.GetContentType
import at.bitfire.dav4jvm.property.GetLastModified
import at.bitfire.dav4jvm.property.ResourceType
import autodagger.AutoInjector
import com.nextcloud.talk.api.NcApiCoroutines
import com.nextcloud.talk.application.NextcloudTalkApplication
import com.nextcloud.talk.dagger.modules.RestModule
import com.nextcloud.talk.data.user.model.User
import com.nextcloud.talk.filebrowser.models.DavResponse
import com.nextcloud.talk.filebrowser.models.properties.NCEncrypted
import com.nextcloud.talk.filebrowser.models.properties.NCPermission
import com.nextcloud.talk.filebrowser.models.properties.NCPreview
import com.nextcloud.talk.filebrowser.models.properties.OCFavorite
import com.nextcloud.talk.filebrowser.models.properties.OCId
import com.nextcloud.talk.filebrowser.models.properties.OCSize
import com.nextcloud.talk.remotefilebrowser.model.RemoteFileBrowserItem
import com.nextcloud.talk.utils.ApiUtils
import com.nextcloud.talk.utils.FileUtils
import com.nextcloud.talk.utils.Mimetype
import okhttp3.Dispatcher
import okhttp3.HttpUrl.Companion.toHttpUrl
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

@Suppress("TooManyFunctions")
@AutoInjector(NextcloudTalkApplication::class)
class ChunkedFileUploader(
    okHttpClient: OkHttpClient,
    val currentUser: User,
    val listener: OnDataTransferProgressListener,
    val ncApiCoroutines: NcApiCoroutines,
    private val isRestarted: () -> Boolean = { false },
    private val markRestarted: () -> Unit = {}
) {

    private var okHttpClientNoRedirects: OkHttpClient? = null
    private var remoteChunkUrl: String
    private var uploadFolderUri: String = ""

    @Volatile
    private var isUploadAborted = false

    init {
        initHttpClient(okHttpClient, currentUser)
        remoteChunkUrl = ApiUtils.getUrlForChunkedUpload(currentUser.baseUrl!!, currentUser.userId!!)
    }

    /**
     * Uploads the parts of [localFile] that the server does not have yet, then assembles them into [targetPath].
     * The upload folder on the server is keyed by the file, so a repeated call resumes a previous one.
     *
     * @return true when the file was assembled, false when the upload was stopped or aborted
     * @throws Exception when a request fails; the parts already on the server are kept
     */
    fun upload(localFile: File, mimeType: MediaType?, targetPath: String): Boolean {
        uploadFolderUri = folderUriOf(localFile)
        return try {
            uploadParts(localFile, mimeType, targetPath)
        } catch (e: HttpException) {
            // The server refused the assembly (e.g. the length does not match its parts): the parts it holds are
            // not usable, so remove them once per upload (the worker remembers it) and send the file again.
            if (e.code != HTTP_BAD_REQUEST || isRestarted()) {
                throw e
            }
            Log.w(TAG, "Server rejected the assembly, uploading the file again", e)
            deleteUploadFolder()
            markRestarted()
            uploadParts(localFile, mimeType, targetPath)
        }
    }

    private fun folderUriOf(localFile: File) = remoteChunkUrl + "/" + FileUtils.md5Sum(localFile)

    private fun uploadParts(localFile: File, mimeType: MediaType?, targetPath: String): Boolean {
        val davResource = DavResource(
            okHttpClientNoRedirects!!,
            uploadFolderUri.toHttpUrlOrNull()!!
        )

        createFolder(davResource)

        val chunksOnServer: MutableList<Chunk> = getUploadedChunks(davResource, uploadFolderUri, localFile.length())
        Log.d(TAG, "chunksOnServer: " + chunksOnServer.size)

        val missingChunks: List<Chunk> = checkMissingChunks(chunksOnServer, localFile.length())
        Log.d(TAG, "missingChunks: " + missingChunks.size)

        for (missingChunk in missingChunks) {
            if (isUploadAborted) {
                break
            }
            uploadChunk(localFile, uploadFolderUri, mimeType, missingChunk, missingChunk.length())
        }

        val isComplete = !isUploadAborted
        if (isComplete) {
            assembleChunks(uploadFolderUri, targetPath, localFile.length())
        }
        return isComplete
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
            NextcloudTalkApplication.sharedApplication?.logger?.e(TAG, "Failed to create folder for chunked upload", e)
            throw IOException("failed to create folder", e)
        } catch (e: HttpException) {
            if (e.code == METHOD_NOT_ALLOWED_CODE) {
                Log.d(TAG, "Folder most probably already exists, that's okay, just continue..")
            } else {
                throw IOException("failed to create folder", e)
            }
        }
    }

    @Suppress("Detekt.ComplexMethod", "Detekt.ReturnCount")
    private fun getUploadedChunks(
        davResource: DavResource,
        uploadFolderUri: String,
        fileLength: Long
    ): MutableList<Chunk> {
        val davResponse = DavResponse()
        val memberElements: MutableList<at.bitfire.dav4jvm.Response> = ArrayList()
        val rootElement = arrayOfNulls<at.bitfire.dav4jvm.Response>(1)
        val remoteFiles: MutableList<RemoteFileBrowserItem> = ArrayList()
        try {
            davResource.propfind(
                1,
                ResourceType.NAME,
                GetContentLength.NAME,
                DisplayName.NAME
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
        } catch (e: DavException) {
            if (e is HttpException && e.code >= HTTP_SERVER_ERROR) {
                throw e
            }
            // An IOException is not caught on purpose: a lost network must repeat the request,
            // not send the whole file again.
            // PROPFIND on Nextcloud chunked-upload folders can return unexpected responses
            // (e.g. 200 instead of 207). Treat any such answer as "no chunks uploaded yet".
            Log.w(TAG, "PROPFIND failed — assuming no chunks on server, will upload from scratch: ${e.message}")
            return ArrayList()
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
            if (remoteFile.isFile) {
                parseUploadedChunk(remoteFile.displayName, remoteFile.size, fileLength)?.let { chunksOnServer.add(it) }
            }
        }
        return chunksOnServer
    }

    /**
     * Returns the part a file on the server stands for, or null when it is not a part (e.g. `.file`), or its size
     * differs from what its name says, so it has to be uploaded again. The last part is named one byte longer than
     * its content.
     */
    internal fun parseUploadedChunk(name: String?, size: Long?, fileLength: Long): Chunk? {
        if (name == null || !CHUNK_NAME_REGEX.matches(name)) {
            return null
        }
        val chunk =
            Chunk(name.substring(0, CHUNK_NUMBER_LENGTH).toLong(), name.substring(CHUNK_NUMBER_LENGTH + 1).toLong())
        val expectedSize = minOf(chunk.length(), fileLength - chunk.start)
        return if (size == expectedSize) chunk else null
    }

    internal fun checkMissingChunks(chunks: List<Chunk>, length: Long): List<Chunk> {
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

    private fun initHttpClient(okHttpClient: OkHttpClient, currentUser: User) {
        // Own dispatcher: stop() cancels all calls of the dispatcher, and the one of okHttpClient is shared with
        // the whole app (chat requests, signaling websocket, other uploads).
        val builder = okHttpClient.newBuilder()
            .dispatcher(Dispatcher())
            .followRedirects(false)
            .followSslRedirects(false)
            .protocols(listOf(Protocol.HTTP_1_1))
            .sslSocketFactory(okHttpClient.sslSocketFactory, okHttpClient.x509TrustManager!!)
            .hostnameVerifier(okHttpClient.hostnameVerifier)
            .authenticator(
                RestModule.HttpAuthenticator(
                    ApiUtils.getCredentials(
                        currentUser.username,
                        currentUser.token
                    )!!,
                    "Authorization"
                )
            )
        okHttpClient.proxy?.let { builder.proxy(it) }
        this.okHttpClientNoRedirects = builder.build()
    }

    private fun assembleChunks(uploadFolderUri: String, targetPath: String, totalLength: Long) {
        val destinationUri = ApiUtils.getUrlForFileUpload(
            currentUser.baseUrl!!,
            currentUser.userId!!,
            targetPath
        )

        createRemoteFolder(targetPath)
        val originUri = "$uploadFolderUri/.file"

        val client = okHttpClientNoRedirects!!.newBuilder()
            .addInterceptor { chain ->
                chain.proceed(chain.request().newBuilder().header(TOTAL_LENGTH_HEADER, totalLength.toString()).build())
            }
            .build()
        DavResource(
            client,
            originUri.toHttpUrlOrNull()!!
        ).move(
            destinationUri.toHttpUrlOrNull()!!,
            true
        ) { response: Response ->
            if (!response.isSuccessful) {
                throw IOException("Failed to assemble chunks. response code: " + response.code)
            }
        }
    }

    private fun createRemoteFolder(targetPath: String) {
        val folderPath = targetPath.substringBeforeLast('/')
        if (folderPath.isEmpty() || folderPath == "/") return
        val folderUri = ApiUtils.getUrlForFileUpload(currentUser.baseUrl!!, currentUser.userId!!, folderPath)
        try {
            DavResource(okHttpClientNoRedirects!!, folderUri.toHttpUrlOrNull()!!).mkCol(
                xmlBody = null
            ) { _ -> }
        } catch (e: HttpException) {
            if (e.code != METHOD_NOT_ALLOWED_CODE) {
                Log.w(TAG, "Unexpected error creating remote folder $folderPath: ${e.code}")
            }
        } catch (e: IOException) {
            Log.w(TAG, "Failed to create remote folder $folderPath", e)
        }
    }

    /**
     * Interrupts a running [upload] without touching the parts on the server, so a later [upload] can resume.
     * Called from the worker's onStopped, which WorkManager runs inside a coroutine cancellation handler:
     * anything thrown here crashes the process, so this function must never throw.
     */
    fun stop() {
        isUploadAborted = true
        okHttpClientNoRedirects?.dispatcher?.cancelAll()
    }

    /**
     * Interrupts a running [upload] and removes its parts from the server. Same contract as [stop]: never throws.
     */
    @Suppress("Detekt.TooGenericExceptionCaught")
    fun abortUpload(onSuccess: () -> Unit) {
        stop()
        val client = okHttpClientNoRedirects
        val folderUrl = uploadFolderUri.toHttpUrlOrNull()
        if (client == null || folderUrl == null) {
            Log.i(TAG, "Nothing to abort, chunk upload was not started")
            return
        }
        try {
            DavResource(client, folderUrl).delete { response: Response ->
                when {
                    response.isSuccessful -> onSuccess()
                    else -> isUploadAborted = false
                }
            }
        } catch (e: NotFoundException) {
            Log.i(TAG, "Chunk upload folder could not be found", e)
            onSuccess()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to remove chunk upload folder", e)
        }
    }

    /** Removes the parts of an earlier run of [localFile], for an uploader that has not started an upload. */
    fun abortUpload(localFile: File, onSuccess: () -> Unit) {
        uploadFolderUri = folderUriOf(localFile)
        abortUpload(onSuccess)
    }

    /**
     * Removes a file this uploader or an earlier run assembled on the server, e.g. when the user cancelled after
     * the upload and before the share. Never throws, like [abortUpload].
     */
    @Suppress("Detekt.TooGenericExceptionCaught")
    fun deleteUploadedFile(targetPath: String) {
        try {
            val url = ApiUtils.getUrlForFileUpload(currentUser.baseUrl!!, currentUser.userId!!, targetPath)
            DavResource(okHttpClientNoRedirects!!, url.toHttpUrl()).delete { _ -> }
        } catch (e: NotFoundException) {
            Log.i(TAG, "Uploaded file is already gone", e)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to remove the uploaded file", e)
        }
    }

    private fun deleteUploadFolder() {
        try {
            DavResource(okHttpClientNoRedirects!!, uploadFolderUri.toHttpUrlOrNull()!!).delete { _ -> }
        } catch (e: NotFoundException) {
            Log.i(TAG, "Chunk upload folder is already gone", e)
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

            is GetContentLength -> {
                remoteFileBrowserItem.size = property.contentLength
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
        private val TAG = ChunkedFileUploader::class.java.simpleName
        private const val READ_PERMISSION = "R"
        private const val CHUNK_SIZE: Long = 1024000
        private const val METHOD_NOT_ALLOWED_CODE: Int = 405
        private const val HTTP_BAD_REQUEST: Int = 400
        private const val HTTP_SERVER_ERROR: Int = 500
        private const val CHUNK_NUMBER_LENGTH = 16
        private val CHUNK_NAME_REGEX = Regex("^\\d{16}-\\d{16}$")
        private const val TOTAL_LENGTH_HEADER = "OC-Total-Length"
    }
}
