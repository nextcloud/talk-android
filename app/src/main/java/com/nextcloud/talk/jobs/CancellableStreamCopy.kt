/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.jobs

import java.io.InputStream
import java.io.InterruptedIOException
import java.io.OutputStream

internal object CancellableStreamCopy {
    fun copy(
        input: InputStream,
        output: OutputStream,
        isStopped: () -> Boolean,
        onProgress: (Long) -> Unit = {}
    ): Long {
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var total = 0L
        while (true) {
            if (isStopped()) throw InterruptedIOException("File copy cancelled")
            val count = input.read(buffer)
            if (count < 0) break
            output.write(buffer, 0, count)
            total += count
            onProgress(total)
        }
        return total
    }
}
