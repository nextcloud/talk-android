/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2021 Marcel Hibbe <dev@mhibbe.de>
 * SPDX-FileCopyrightText: 2020 Chris Narkiewicz <hello@ezaquarii.com>
 * SPDX-FileCopyrightText: 2016 ownCloud GmbH
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.utils

import com.nextcloud.talk.api.NcApi
import com.nextcloud.talk.data.user.model.User
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers

object RemoteFileUtils {
    private val TAG = RemoteFileUtils::class.java.simpleName

    fun getNewPathIfFileExists(ncApi: NcApi, currentUser: User, remotePath: String): String {
        var finalPath = remotePath
        val fileExists = doesFileExist(
            ncApi,
            currentUser,
            remotePath
        ).blockingFirst()

        if (fileExists) {
            finalPath = getFileNameWithoutCollision(
                ncApi,
                currentUser,
                remotePath
            )
        }
        return finalPath
    }

    private fun doesFileExist(ncApi: NcApi, currentUser: User, remotePath: String): Observable<Boolean> =
        ncApi.checkIfFileExists(
            ApiUtils.getCredentials(currentUser.username, currentUser.token),
            ApiUtils.getUrlForFileUpload(
                currentUser.baseUrl!!,
                currentUser.userId!!,
                remotePath
            )
        )
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread()).map { response ->
                response.isSuccessful
            }

    private fun getFileNameWithoutCollision(ncApi: NcApi, currentUser: User, remotePath: String): String {
        val extPos = remotePath.lastIndexOf('.')
        var suffix: String
        var extension = ""
        var remotePathWithoutExtension = ""
        if (extPos >= 0) {
            extension = remotePath.substring(extPos + 1)
            remotePathWithoutExtension = remotePath.substring(0, extPos)
        }
        var count = 2
        var exists: Boolean
        var newPath: String
        do {
            suffix = " ($count)"
            newPath = if (extPos >= 0) "$remotePathWithoutExtension$suffix.$extension" else remotePath + suffix
            exists = doesFileExist(ncApi, currentUser, newPath).blockingFirst()
            count++
        } while (exists)
        return newPath
    }
}
