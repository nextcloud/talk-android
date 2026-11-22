/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.jobs

import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.InterruptedIOException

internal object CacheFileWriter {
    @Suppress("LongParameterList")
    fun write(
        input: InputStream,
        target: File,
        expectedLength: Long,
        isStopped: () -> Boolean,
        onTemporaryFile: (File) -> Unit = {},
        publish: (File, File) -> Boolean = { temporary, destination -> temporary.renameTo(destination) },
        onProgress: (Long) -> Unit
    ) {
        val temporary = File.createTempFile("download-", ".part", target.parentFile)
        try {
            onTemporaryFile(temporary)
            val total = temporary.outputStream().use { output ->
                CancellableStreamCopy.copy(input, output, isStopped, onProgress)
            }
            validateComplete(total, expectedLength, isStopped())
            if (!publish(temporary, target)) throw IOException("Cannot publish downloaded file")
        } finally {
            temporary.delete()
        }
    }

    private fun validateComplete(total: Long, expectedLength: Long, stopped: Boolean) {
        if (expectedLength >= 0 && total != expectedLength) throw IOException("Incomplete download")
        if (stopped) throw InterruptedIOException("Download cancelled")
    }
}
