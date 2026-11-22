/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.jobs

internal object DownloadProgress {
    fun percent(bytes: Long, total: Long): Int =
        if (total > 0) ((bytes.toDouble() / total) * COMPLETE).toInt().coerceIn(0, COMPLETE - 1) else -1

    private const val COMPLETE = 100
}
