/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.logger

import java.io.File
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.IOException

class FileLogHandler(private val logDir: File, private val logFilename: String, private val maxSize: Long) {
    data class RawLogs(val lines: List<String>, val logSize: Long)

    companion object {
        const val ROTATED_LOGS_COUNT = 3
    }

    private var writer: FileOutputStream? = null
    private var size: Long = 0

    private val rotationList = listOf(
        "$logFilename.2",
        "$logFilename.1",
        "$logFilename.0",
        logFilename
    )

    val logFile: File get() = File(logDir, logFilename)

    val isOpened: Boolean get() = writer != null

    fun open() {
        try {
            writer = FileOutputStream(logFile, true)
            size = logFile.length()
        } catch (_: FileNotFoundException) {
            logFile.parentFile?.mkdirs()
            writer = FileOutputStream(logFile, true)
            size = logFile.length()
        }
    }

    fun write(logEntry: String) {
        val bytes = logEntry.toByteArray(Charsets.UTF_8)
        writer?.write(bytes)
        size += bytes.size
        if (size > maxSize) {
            rotateLogs()
        }
    }

    fun close() {
        writer?.close()
        writer = null
        size = 0L
    }

    fun deleteAll() {
        rotationList.map { File(logDir, it) }.forEach { it.delete() }
    }

    fun rotateLogs() {
        val wasOpen = isOpened
        if (wasOpen) close()

        val existingFiles = logDir.listFiles()?.associate { it.name to it } ?: emptyMap()
        existingFiles[rotationList.first()]?.delete()

        for (i in 0 until rotationList.size - 1) {
            val dest = File(logDir, rotationList[i])
            existingFiles[rotationList[i + 1]]?.renameTo(dest)
        }

        if (wasOpen) open()
    }

    fun loadLogFiles(rotated: Int = ROTATED_LOGS_COUNT): RawLogs {
        require(rotated >= 0) { "Negative index" }
        val allLines = mutableListOf<String>()
        var totalSize = 0L
        for (i in 0..minOf(rotated, rotationList.size - 1)) {
            val file = File(logDir, rotationList[i])
            if (!file.exists()) continue
            try {
                allLines.addAll(file.readLines(Charsets.UTF_8))
                totalSize += file.length()
            } catch (_: IOException) {
                // skip unreadable files
            }
        }
        return RawLogs(lines = allLines, logSize = totalSize)
    }
}
