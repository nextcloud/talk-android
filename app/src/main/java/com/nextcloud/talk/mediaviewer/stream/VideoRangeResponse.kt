/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.mediaviewer.stream

import java.io.IOException

internal object VideoRangeResponse {
    fun validate(header: String?, start: Long, total: Long): Long {
        val values = Regex("bytes (\\d+)-(\\d+)/(\\d+)").matchEntire(header.orEmpty())
            ?.groupValues?.drop(1)?.mapNotNull { it.toLongOrNull() }
        if (values == null || values.size != RANGE_VALUES) throw IOException("Invalid video byte range")
        if (values[0] != start ||
            values[2] != total ||
            values[1] != total - 1 ||
            start >= total
        ) {
            throw IOException("Unexpected video byte range")
        }
        return total - start
    }

    private const val RANGE_VALUES = 3
}
