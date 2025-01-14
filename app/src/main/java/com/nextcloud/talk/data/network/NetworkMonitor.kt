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
     * Returns the device's current connectivity status.
     */
    val isOnline: StateFlow<Boolean>

    /**
     * Returns the device's current connectivity status as LiveData for better interop with Java code.
     */
    val isOnlineLiveData: LiveData<Boolean>
}
