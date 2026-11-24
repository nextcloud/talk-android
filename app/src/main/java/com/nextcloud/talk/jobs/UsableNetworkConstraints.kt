/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.jobs

import android.net.NetworkCapabilities
import android.net.NetworkRequest
import androidx.work.Constraints
import androidx.work.NetworkType

/**
 * Constraints of work that sends requests to the Nextcloud server: it runs while the device has a usable network, as
 * defined by [com.nextcloud.talk.data.network.NetworkMonitor].
 *
 * [NetworkType.CONNECTED] alone would also require the network to be validated by Android, so the work would never
 * run on a network without internet even if the server is reachable there. WorkManager supports a custom network
 * request only from API 28, below that [NetworkType.CONNECTED] is required.
 */
fun usableNetworkConstraints(): Constraints =
    Constraints.Builder()
        .setRequiredNetworkRequest(
            NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .removeCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)
                .removeCapability(NetworkCapabilities.NET_CAPABILITY_NOT_RESTRICTED)
                .build(),
            NetworkType.CONNECTED
        )
        .build()
