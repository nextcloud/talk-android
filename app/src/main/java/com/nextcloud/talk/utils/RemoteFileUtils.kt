package com.nextcloud.talk.utils

/*
 * Nextcloud Talk application
 * @author Marcel Hibbe
 * @author David A. Velasco
 * @author Chris Narkiewicz
 * Copyright (C) 2021 Marcel Hibbe <dev@mhibbe.de>
 * Copyright (C) 2016 ownCloud GmbH.
 * Copyright (C) 2020 Chris Narkiewicz <hello@ezaquarii.com>
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
import com.nextcloud.talk.api.NcApi
import com.nextcloud.talk.data.user.model.User
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers

object RemoteFileUtils {
    private val TAG = RemoteFileUtils::class.java.simpleName

    fun getNewPathIfFileExists(
        ncApi: NcApi,
        currentUser: User,
        remotePath: String
    ): String {
        var finalPath = remotePath
        val fileExists = doesFileExist(
            ncApi,
            currentUser,
            remotePath,
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

    private fun doesFileExist(
        ncApi: NcApi,
        currentUser: User,
        remotePath: String
    ): Observable<Boolean> {
        return ncApi.checkIfFileExists(
            ApiUtils.getCredentials(currentUser.username, currentUser.token),
            ApiUtils.getUrlForFileUpload(
                currentUser.baseUrl,
                currentUser.userId,
                remotePath
            )
        )
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread()).map { response ->
                response.isSuccessful
            }
    }

    private fun getFileNameWithoutCollision(
        ncApi: NcApi,
        currentUser: User,
        remotePath: String
    ): String {
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
