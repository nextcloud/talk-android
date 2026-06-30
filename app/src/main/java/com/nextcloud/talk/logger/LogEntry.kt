/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.logger

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

data class LogEntry(
    val timestamp: Date,
    val level: Level,
    val tag: String,
    val message: String,
    val pid: Int = 0,
    val tid: Int = 0
) {
    // Classic logcat threadtime format recognised by Android Studio:
    //   "MM-dd HH:mm:ss.SSS  pid  tid LEVEL TAG: message"
    // Each line of a multi-line message gets its own full header so the file is
    // a valid logcat file — Android Studio imports it without modification.
    override fun toString(): String {
        val prefix = "${TIMESTAMP_FORMATTER.format(timestamp)} " +
            "${pid.toString().padStart(PID_TID_WIDTH)} ${tid.toString().padStart(PID_TID_WIDTH)} " +
            "${level.tag} $tag: "
        return message.lines().joinToString("\n") { "$prefix$it" }
    }

    private fun sameHeader(other: LogEntry): Boolean =
        timestamp == other.timestamp &&
            pid == other.pid &&
            tid == other.tid &&
            level == other.level &&
            tag == other.tag

    companion object {
        private const val PID_TID_WIDTH = 5

        // MM-dd matches what `adb logcat -v threadtime` produces.
        val TIMESTAMP_FORMATTER: SimpleDateFormat = SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.ROOT).apply {
            timeZone = TimeZone.getDefault()
        }

        // Matches: "MM-dd HH:mm:ss.SSS  pid  tid LEVEL TAG: message"
        private val HEADER_REGEX = Regex(
            """^(\d{2}-\d{2} \d{2}:\d{2}:\d{2}\.\d{3})\s+(\d+)\s+(\d+)\s+([DIWE])\s+(.+?):\s?(.*)$"""
        )

        fun parseHeader(line: String): LogEntry? {
            val m = HEADER_REGEX.matchEntire(line) ?: return null
            val (ts, pid, tid, levelTag, tag, message) = m.destructured
            val level = Level.fromTag(levelTag)
            val parsed = runCatching { TIMESTAMP_FORMATTER.parse(ts) }.getOrNull()
            // MM-dd has no year; fill in the current year so display timestamps make sense.
            return if (level == null || parsed == null) {
                null
            } else {
                val currentYear = Calendar.getInstance().get(Calendar.YEAR)
                val timestamp = Calendar.getInstance().apply {
                    time = parsed
                    set(Calendar.YEAR, currentYear)
                }.time
                LogEntry(timestamp, level, tag.trim(), message, pid.toInt(), tid.toInt())
            }
        }

        // Consecutive lines with identical (timestamp, pid, tid, level, tag) were emitted by
        // a single logger call and are reassembled into one multi-line LogEntry for display.
        fun parseLines(lines: List<String>): List<LogEntry> {
            val result = mutableListOf<LogEntry>()
            var current: LogEntry? = null
            val msgs = mutableListOf<String>()

            fun flush() {
                val c = current ?: return
                result.add(c.copy(message = msgs.joinToString("\n")))
                current = null
                msgs.clear()
            }

            for (line in lines) {
                val entry = parseHeader(line) ?: continue
                val prev = current
                if (prev != null && entry.sameHeader(prev)) {
                    msgs.add(entry.message)
                } else {
                    flush()
                    current = entry
                    msgs.add(entry.message)
                }
            }
            flush()
            return result
        }
    }
}
