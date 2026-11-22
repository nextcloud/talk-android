/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.mediaviewer.stream

import java.util.concurrent.ConcurrentHashMap

object VideoDownloadRegistry {
    private val downloads = ConcurrentHashMap<String, ProgressiveVideoFile>()

    fun find(key: String): ProgressiveVideoFile? = downloads[key]

    fun register(key: String, download: ProgressiveVideoFile) {
        downloads[key] = download
    }

    fun remove(key: String, download: ProgressiveVideoFile) {
        downloads.remove(key, download)
    }
}
