/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.upload

/** Lets a progress update through only when the value changed and [minIntervalMs] passed since the last one. */
class UploadProgressThrottle(
    private val minIntervalMs: Long = DEFAULT_INTERVAL_MS,
    private val clock: () -> Long = System::currentTimeMillis
) {
    private var lastPercent = -1
    private var lastUpdateMs = 0L

    @Synchronized
    fun shouldUpdate(percent: Int): Boolean {
        val now = clock()
        val due = lastPercent < 0 || now - lastUpdateMs >= minIntervalMs
        if (percent == lastPercent || !due) {
            return false
        }
        lastPercent = percent
        lastUpdateMs = now
        return true
    }

    companion object {
        private const val DEFAULT_INTERVAL_MS = 1000L
    }
}
