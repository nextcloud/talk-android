/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2025 Your Name <your@email.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.serverstatus

import com.nextcloud.talk.models.json.generic.Status

interface ServerStatusRepository {
    val isServerReachable: Boolean?
    suspend fun getServerStatus()
}
