/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2022 Andy Scherzinger <info@andy-scherzinger.de>
 * SPDX-FileCopyrightText: 2017-2019 Mario Danic <mario@lovelyhq.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.filebrowser.webdav

import android.net.Uri
import android.text.TextUtils
import android.util.Log
import at.bitfire.dav4jvm.DavResource
import at.bitfire.dav4jvm.Property
import at.bitfire.dav4jvm.Response
import at.bitfire.dav4jvm.Response.HrefRelation
import at.bitfire.dav4jvm.exception.DavException
import at.bitfire.dav4jvm.property.DisplayName
import at.bitfire.dav4jvm.property.GetContentType
import at.bitfire.dav4jvm.property.GetLastModified
import at.bitfire.dav4jvm.property.ResourceType
import com.nextcloud.talk.filebrowser.models.DavResponse
import com.nextcloud.talk.filebrowser.models.properties.NCEncrypted
import com.nextcloud.talk.filebrowser.models.properties.NCPermission
import com.nextcloud.talk.filebrowser.models.properties.NCPreview
import com.nextcloud.talk.filebrowser.models.properties.OCFavorite
import com.nextcloud.talk.filebrowser.models.properties.OCId
import com.nextcloud.talk.filebrowser.models.properties.OCSize
import com.nextcloud.talk.dagger.modules.RestModule.HttpAuthenticator
import com.nextcloud.talk.data.user.model.User
import com.nextcloud.talk.remotefilebrowser.model.RemoteFileBrowserItem
import com.nextcloud.talk.utils.ApiUtils
import com.nextcloud.talk.utils.Mimetype.FOLDER
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import java.io.File
import java.io.IOException

class ReadFolderListingOperation(okHttpClient: OkHttpClient, currentUser: User, path: String, depth: Int) {
    private val okHttpClient: OkHttpClient
    private val url: String
    private val depth: Int
    private val basePath: String

    init {
        val okHttpClientBuilder: OkHttpClient.Builder = okHttpClient.newBuilder()
        okHttpClientBuilder.followRedirects(false)
        okHttpClientBuilder.followSslRedirects(false)
        okHttpClientBuilder.authenticator(
            HttpAuthenticator(
                ApiUtils.getCredentials(
                    currentUser.username,
                    currentUser.token
                )!!,
                "Authorization"
            )
        )
        this.okHttpClient = okHttpClientBuilder.build()
        basePath = currentUser.baseUrl + DavUtils.DAV_PATH + currentUser.userId
        url = basePath + path
        this.depth = depth
    }

    fun readRemotePath(): DavResponse {
        val davResponse = DavResponse()
        val memberElements: MutableList<Response> = ArrayList()
        val rootElement = arrayOfNulls<Response>(1)
        val remoteFiles: MutableList<RemoteFileBrowserItem> = ArrayList()
        try {
            DavResource(
                okHttpClient,
                url.toHttpUrlOrNull()!!
            ).propfind(
                depth = depth,
                reqProp = DavUtils.getAllPropSet()
            ) { response: Response, hrefRelation: HrefRelation? ->
                davResponse.setResponse(response)
                when (hrefRelation) {
                    HrefRelation.MEMBER -> memberElements.add(response)
                    HrefRelation.SELF -> rootElement[0] = response
                    HrefRelation.OTHER -> {}
                    else -> {}
                }
                Unit
            }
        } catch (e: IOException) {
            Log.w(TAG, "Error reading remote path")
        } catch (e: DavException) {
            Log.w(TAG, "Error reading remote path")
        }
        for (memberElement in memberElements) {
            remoteFiles.add(
                getModelFromResponse(
                    memberElement,
                    memberElement
                        .href
                        .toString()
                        .substring(basePath.length)
                )
            )
        }
        davResponse.setData(remoteFiles)
        return davResponse
    }

    private fun getModelFromResponse(response: Response, remotePath: String): RemoteFileBrowserItem {
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
            remoteFileBrowserItem.mimeType = FOLDER
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
        private const val TAG = "ReadFilesystemOperation"
        private const val READ_PERMISSION = "R"
    }
}
