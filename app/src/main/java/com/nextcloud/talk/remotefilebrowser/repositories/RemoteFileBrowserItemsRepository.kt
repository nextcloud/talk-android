/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2022 Andy Scherzinger <info@andy-scherzinger.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.remotefilebrowser.repositories

import com.nextcloud.talk.remotefilebrowser.model.RemoteFileBrowserItem
import io.reactivex.Observable

interface RemoteFileBrowserItemsRepository {

    fun listFolder(path: String): Observable<List<RemoteFileBrowserItem>>
}
