/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2023 Andy Scherzinger <info@andy-scherzinger.de>
 * SPDX-FileCopyrightText: 2023 Alper Ozturk <alper_ozturk@proton.me>
 * SPDX-FileCopyrightText: 2023 Nextcloud GmbH
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.utils

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Handler

@SuppressLint("UnspecifiedRegisterReceiverFlag")
fun Context.registerBroadcastReceiver(receiver: BroadcastReceiver?, filter: IntentFilter, flag: ReceiverFlag): Intent? =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        registerReceiver(receiver, filter, flag.value)
    } else {
        registerReceiver(receiver, filter)
    }

@SuppressLint("UnspecifiedRegisterReceiverFlag")
fun Context.registerPermissionHandlerBroadcastReceiver(
    receiver: BroadcastReceiver?,
    filter: IntentFilter,
    broadcastPermission: String?,
    scheduler: Handler?,
    flag: ReceiverFlag
): Intent? =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        registerReceiver(receiver, filter, broadcastPermission, scheduler, flag.value)
    } else {
        registerReceiver(receiver, filter, broadcastPermission, scheduler)
    }
