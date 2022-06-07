/*
 * Nextcloud Talk application
 *
 * @author Andy Scherzinger
 * @author Mario Danic
 * Copyright (C) 2022 Andy Scherzinger <info@andy-scherzinger.de>
 * Copyright (C) 2017-2019 Mario Danic <mario@lovelyhq.com>
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
package com.nextcloud.talk.components.filebrowser.webdav

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
import com.nextcloud.talk.components.filebrowser.models.DavResponse
import com.nextcloud.talk.components.filebrowser.models.properties.NCEncrypted
import com.nextcloud.talk.components.filebrowser.models.properties.NCPermission
import com.nextcloud.talk.components.filebrowser.models.properties.NCPreview
import com.nextcloud.talk.components.filebrowser.models.properties.OCFavorite
import com.nextcloud.talk.components.filebrowser.models.properties.OCId
import com.nextcloud.talk.components.filebrowser.models.properties.OCSize
import com.nextcloud.talk.dagger.modules.RestModule.MagicAuthenticator
import com.nextcloud.talk.models.database.UserEntity
import com.nextcloud.talk.remotefilebrowser.model.RemoteFileBrowserItem
import com.nextcloud.talk.utils.ApiUtils
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import java.io.File
import java.io.IOException

class ReadFilesystemOperation(okHttpClient: OkHttpClient, currentUser: UserEntity, path: String, depth: Int) {
    private val okHttpClient: OkHttpClient
    private val url: String
    private val depth: Int
    private val basePath: String
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

    companion object {
        private const val TAG = "ReadFilesystemOperation"
    }

    init {
        val okHttpClientBuilder: OkHttpClient.Builder = okHttpClient.newBuilder()
        okHttpClientBuilder.followRedirects(false)
        okHttpClientBuilder.followSslRedirects(false)
        okHttpClientBuilder.authenticator(
            MagicAuthenticator(
                ApiUtils.getCredentials(
                    currentUser.username,
                    currentUser.token
                ),
                "Authorization"
            )
        )
        this.okHttpClient = okHttpClientBuilder.build()
        basePath = currentUser.baseUrl + DavUtils.DAV_PATH + currentUser.userId
        url = basePath + path
        this.depth = depth
    }

    private fun getModelFromResponse(response: Response, remotePath: String): RemoteFileBrowserItem {
        val remoteFileBrowserItem = RemoteFileBrowserItem()
        remoteFileBrowserItem.path = Uri.decode(remotePath)
        remoteFileBrowserItem.displayName = Uri.decode(File(remotePath).name)
        val properties = response.properties
        for (property in properties) {
            mapPropertyToBrowserFile(property, remoteFileBrowserItem)
        }
        if (remoteFileBrowserItem.permissions != null && remoteFileBrowserItem.permissions!!.contains("R")) {
            remoteFileBrowserItem.isAllowedToReShare = true
        }
        if (TextUtils.isEmpty(remoteFileBrowserItem.mimeType) && !remoteFileBrowserItem.isFile) {
            remoteFileBrowserItem.mimeType = "inode/directory"
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
}
