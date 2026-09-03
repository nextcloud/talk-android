/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Marcel Hibbe <dev@mhibbe.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.utils

import android.os.Build
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkRequest

/**
 * Marks the request as expedited, but only on API 31+ (Android 12), where expedited work is
 * backed by JobScheduler's expedited job support and does not require the Worker to implement
 * getForegroundInfo(). On older APIs, expedited work falls back to an implicit foreground
 * service and WorkManager throws IllegalStateException if getForegroundInfo() isn't overridden,
 * which none of our Workers do.
 */
fun <B : WorkRequest.Builder<B, W>, W : WorkRequest> B.setExpeditedIfSupported(): B =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
    } else {
        this
    }
