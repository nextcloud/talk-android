/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2023 Andy Scherzinger <info@andy-scherzinger.de>
 * SPDX-FileCopyrightText: 2023 Alper Ozturk <alper_ozturk@proton.me>
 * SPDX-FileCopyrightText: 2023 Nextcloud GmbH
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.utils

import android.content.Context
import android.os.Build
import androidx.annotation.RequiresApi

enum class ReceiverFlag {
    NotExported;

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    fun getId(): Int {
        return Context.RECEIVER_NOT_EXPORTED
    }
}
