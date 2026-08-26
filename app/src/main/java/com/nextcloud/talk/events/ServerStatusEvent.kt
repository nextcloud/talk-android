/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.events

enum class ServerStatus {
    OK,
    UNAUTHORIZED,
    CLIENT_UPDATE_REQUIRED,
    MAINTENANCE_MODE
}

data class ServerStatusEvent(val accountId: Long, val status: ServerStatus)
