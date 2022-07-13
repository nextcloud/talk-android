/*
 * Nextcloud Talk application
 *
 * @author Andy Scherzinger
 * Copyright (C) 2022 Andy Scherzinger <info@andy-scherzinger.de>
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

package com.nextcloud.talk.remotefilebrowser.repositories

import com.nextcloud.talk.components.filebrowser.webdav.ReadFolderListingOperation
import com.nextcloud.talk.data.user.model.User
import com.nextcloud.talk.remotefilebrowser.model.RemoteFileBrowserItem
import com.nextcloud.talk.utils.database.user.CurrentUserProviderNew
import io.reactivex.Observable
import okhttp3.OkHttpClient
import javax.inject.Inject

class RemoteFileBrowserItemsRepositoryImpl @Inject constructor(
    private val okHttpClient: OkHttpClient,
    private val userProvider: CurrentUserProviderNew
) : RemoteFileBrowserItemsRepository {

    private val user: User
        get() = userProvider.currentUser.blockingGet()

    override fun listFolder(path: String):
        Observable<List<RemoteFileBrowserItem>> {
        return Observable.fromCallable {
            val operation =
                ReadFolderListingOperation(
                    okHttpClient,
                    user,
                    path,
                    1
                )
            val davResponse = operation.readRemotePath()
            if (davResponse.getData() != null) {
                return@fromCallable davResponse.getData() as List<RemoteFileBrowserItem>
            }
            return@fromCallable emptyList()
        }
    }
}
