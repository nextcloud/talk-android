/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2024 Julius Linus <juliuslinus1@gmail.com>
 * SPDX-FileCopyrightText: 2024 Marcel Hibbe <dev@mhibbe.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.data.network

import androidx.lifecycle.LiveData
import kotlinx.coroutines.flow.StateFlow

/**
 * Utility for reporting app connectivity status.
 */
interface NetworkMonitor {
    /**
     * Whether the device has a network that can reach other hosts. It doesn't tell whether the internet or the
     * Nextcloud server can be reached, see [ServerConnectionMonitor] for the latter.
     */
    val isOnline: StateFlow<Boolean>

    /**
     * Returns the device's current connectivity status as LiveData for better interop with Java code.
     */
    val isOnlineLiveData: LiveData<Boolean>
}
