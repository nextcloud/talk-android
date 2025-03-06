/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2017-2018 Mario Danic <mario@lovelyhq.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.filebrowser.models

import android.net.Uri
import android.os.Parcelable
import android.text.TextUtils
import at.bitfire.dav4jvm.Property
import at.bitfire.dav4jvm.Response
import at.bitfire.dav4jvm.property.DisplayName
import at.bitfire.dav4jvm.property.GetContentType
import at.bitfire.dav4jvm.property.GetLastModified
import at.bitfire.dav4jvm.property.ResourceType
import at.bitfire.dav4jvm.property.ResourceType.Companion.COLLECTION
import com.bluelinelabs.logansquare.annotation.JsonObject
import com.nextcloud.talk.filebrowser.models.properties.NCEncrypted
import com.nextcloud.talk.filebrowser.models.properties.NCPermission
import com.nextcloud.talk.filebrowser.models.properties.NCPreview
import com.nextcloud.talk.filebrowser.models.properties.OCFavorite
import com.nextcloud.talk.filebrowser.models.properties.OCId
import com.nextcloud.talk.filebrowser.models.properties.OCSize
import com.nextcloud.talk.utils.Mimetype.FOLDER
import kotlinx.parcelize.Parcelize
import java.io.File

@Parcelize
@JsonObject
data class BrowserFile(
    var path: String? = null,
    var displayName: String? = null,
    var mimeType: String? = null,
    var modifiedTimestamp: Long = 0,
    var size: Long = 0,
    var isFile: Boolean = false,

    // Used for remote files
    var remoteId: String? = null,
    var hasPreview: Boolean = false,
    var isFavorite: Boolean = false,
    var isEncrypted: Boolean = false,
    var permissions: String? = null,
    var isAllowedToReShare: Boolean = false
) : Parcelable {
    // This constructor is added to work with the 'com.bluelinelabs.logansquare.annotation.JsonObject'
    constructor() : this(null, null, null, 0, 0, false, null, false, false, false, null, false)

    companion object {
        fun getModelFromResponse(response: Response, remotePath: String): BrowserFile {
            val browserFile = BrowserFile()
            browserFile.path = Uri.decode(remotePath)
            browserFile.displayName = Uri.decode(File(remotePath).name)
            val properties = response.properties
            for (property in properties) {
                mapPropertyToBrowserFile(property, browserFile)
            }
            if (browserFile.permissions != null && browserFile.permissions!!.contains("R")) {
                browserFile.isAllowedToReShare = true
            }
            if (TextUtils.isEmpty(browserFile.mimeType) && !browserFile.isFile) {
                browserFile.mimeType = FOLDER
            }

            return browserFile
        }

        @Suppress("Detekt.ComplexMethod")
        private fun mapPropertyToBrowserFile(property: Property, browserFile: BrowserFile) {
            when (property) {
                is OCId -> {
                    browserFile.remoteId = property.ocId
                }
                is ResourceType -> {
                    browserFile.isFile = !property.types.contains(COLLECTION)
                }
                is GetLastModified -> {
                    browserFile.modifiedTimestamp = property.lastModified
                }
                is GetContentType -> {
                    browserFile.mimeType = property.type
                }
                is OCSize -> {
                    browserFile.size = property.ocSize
                }
                is NCPreview -> {
                    browserFile.hasPreview = property.isNcPreview
                }
                is OCFavorite -> {
                    browserFile.isFavorite = property.isOcFavorite
                }
                is DisplayName -> {
                    browserFile.displayName = property.displayName
                }
                is NCEncrypted -> {
                    browserFile.isEncrypted = property.isNcEncrypted
                }
                is NCPermission -> {
                    browserFile.permissions = property.ncPermission
                }
            }
        }
    }
}
