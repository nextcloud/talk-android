/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2022 Álvaro Brey <alvaro@alvarobrey.com>
 * SPDX-FileCopyrightText: 2022 Tim Krüger <t@timkrueger.me>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.shareditems.repositories

import com.nextcloud.talk.shareditems.model.SharedItemType
import com.nextcloud.talk.shareditems.model.SharedItems

interface SharedItemsRepository {

    /**
     * Throws a [retrofit2.HttpException] if the server responds with an error.
     */
    suspend fun media(parameters: Parameters, type: SharedItemType, lastKnownMessageId: Int? = null): SharedItems

    /**
     * Throws a [retrofit2.HttpException] if the server responds with an error.
     */
    suspend fun availableTypes(parameters: Parameters): Set<SharedItemType>

    /**
     * [userName] is the login name of the account, which authenticates the requests together with [userToken].
     */
    data class Parameters(val userName: String, val userToken: String, val baseUrl: String, val roomToken: String)
}
