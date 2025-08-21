/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2025 Sowjanya Kota <sowjanya.kch@gmail.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.serverstatus

import kotlinx.coroutines.flow.StateFlow

interface ServerStatusRepository {
    val isServerReachable: StateFlow<Boolean>
    suspend fun getServerStatus()
}
