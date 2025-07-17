/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2025 Marcel Hibbe <dev@mhibbe.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.threadsoverview.data

import com.nextcloud.talk.api.NcApiCoroutines
import com.nextcloud.talk.data.user.model.User
import com.nextcloud.talk.models.json.threads.ThreadOverall
import com.nextcloud.talk.models.json.threads.ThreadsOverall
import com.nextcloud.talk.utils.database.user.CurrentUserProviderNew
import javax.inject.Inject

class ThreadsRepositoryImpl @Inject constructor(
    private val ncApiCoroutines: NcApiCoroutines,
    userProvider: CurrentUserProviderNew
) : ThreadsRepository {

    val currentUser: User = userProvider.currentUser.blockingGet()

    override suspend fun getThreads(credentials: String, url: String): ThreadsOverall =
        ncApiCoroutines.getThreads(credentials, url)

    override suspend fun getThread(credentials: String, url: String): ThreadOverall =
        ncApiCoroutines.getThread(credentials, url)

    companion object {
        val TAG = ThreadsRepositoryImpl::class.simpleName
    }
}
