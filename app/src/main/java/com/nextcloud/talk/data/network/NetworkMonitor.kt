/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2024 Julius Linus <juliuslinus1@gmail.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.data.network

import kotlinx.coroutines.flow.Flow

/**
 * Utility for reporting app connectivity status.
 */
interface NetworkMonitor {
    val isOnline: Flow<Boolean>
}
