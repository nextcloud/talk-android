/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.errorhandling

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import com.nextcloud.talk.BuildConfig
import com.nextcloud.talk.R
import com.nextcloud.talk.dagger.modules.UtilsModule
import com.nextcloud.talk.logger.LogEntry
import java.io.File
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

private const val MILLIS_PER_SECOND = 1000L
private const val NANOS_PER_MILLI = 1_000_000L
private const val LOG_SORT_BASE_FILE = 3

fun shareLogsAndDiagnosis(context: Context, subject: String, diagnosisText: String, crashInfo: String? = null) {
    val logDir = File(context.filesDir, UtilsModule.LOG_DIR_NAME)
    val jsonFile = buildLogcatJsonFile(context, logDir)

    val uris = ArrayList<Uri>()
    if (jsonFile != null) {
        uris.add(FileProvider.getUriForFile(context, BuildConfig.APPLICATION_ID, jsonFile))
    }

    val pleaseDescribe = context.getString(R.string.error_crash_please_describe)
    val body = buildString {
        appendLine("# $pleaseDescribe:")
        appendLine("...")
        appendLine()
        appendLine()
        appendLine()
        appendLine()
        if (crashInfo != null) {
            appendLine("# ${context.getString(R.string.nc_logs_share_latest_crash)}:")
            appendLine("```")
            appendLine(crashInfo)
            appendLine("```")
            appendLine()
        }
        append("# ${context.getString(R.string.nc_logs_share_diagnosis)}:")
        appendLine()
        append(diagnosisText)
    }

    val intent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
        selector = Intent(Intent.ACTION_SENDTO, "mailto:".toUri())
        putExtra(Intent.EXTRA_EMAIL, arrayOf(context.getString(R.string.nc_report_email)))
        putExtra(Intent.EXTRA_SUBJECT, subject)
        putExtra(Intent.EXTRA_TEXT, body)
        if (uris.isNotEmpty()) {
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
        }
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(intent)
}

fun saveLogsAsZip(context: Context, outputStream: OutputStream, diagnosisText: String) {
    val logDir = File(context.filesDir, UtilsModule.LOG_DIR_NAME)
    val entries = LogEntry.parseLines(loadAllLogLines(logDir))
    ZipOutputStream(outputStream).use { zip ->
        if (entries.isNotEmpty()) {
            zip.putNextEntry(ZipEntry("nc_talk_log_export.json"))
            zip.write(buildLogcatJson(context.packageName, entries).toByteArray(Charsets.UTF_8))
            zip.closeEntry()
        }
        zip.putNextEntry(ZipEntry("diagnosisReport.md"))
        zip.write(diagnosisText.toByteArray(Charsets.UTF_8))
        zip.closeEntry()
    }
}

// Reads all text log files, parses them, and writes a single JSON file in the format
// Android Studio's logcat panel produces — so the file can be directly imported.
private fun buildLogcatJsonFile(context: Context, logDir: File): File? {
    val entries = LogEntry.parseLines(loadAllLogLines(logDir))
    if (entries.isEmpty()) return null
    val json = buildLogcatJson(context.packageName, entries)
    return File(logDir, "nc_talk_log_export.json").also { it.writeText(json, Charsets.UTF_8) }
}

private fun loadAllLogLines(logDir: File): List<String> {
    val allLines = mutableListOf<String>()
    logDir.listFiles()
        ?.filter { it.isFile && it.name.startsWith("nc_talk_log") && it.name.endsWith(".txt") }
        ?.sortedBy { file ->
            // Match FileLogHandler.rotationList order: .2 (oldest) → .1 → .0 → base (newest)
            when {
                file.name.endsWith(".2") -> 0
                file.name.endsWith(".1") -> 1
                file.name.endsWith(".0") -> 2
                else -> LOG_SORT_BASE_FILE
            }
        }
        ?.forEach { file ->
            runCatching { allLines.addAll(file.readLines(Charsets.UTF_8)) }
        }
    return allLines
}

private fun buildLogcatJson(packageName: String, entries: List<LogEntry>): String =
    buildString {
        appendLine("{")
        appendLine("  \"metadata\": {")
        appendLine("    \"projectApplicationIds\": [\"$packageName\"]")
        appendLine("  },")
        appendLine("  \"logcatMessages\": [")
        entries.forEachIndexed { i, entry ->
            val seconds = entry.timestamp.time / MILLIS_PER_SECOND
            val nanos = (entry.timestamp.time % MILLIS_PER_SECOND) * NANOS_PER_MILLI
            appendLine("    {")
            appendLine("      \"header\": {")
            appendLine("        \"logLevel\": \"${entry.level.name}\",")
            appendLine("        \"pid\": ${entry.pid},")
            appendLine("        \"tid\": ${entry.tid},")
            appendLine("        \"applicationId\": \"$packageName\",")
            appendLine("        \"processName\": \"$packageName\",")
            appendLine("        \"tag\": \"${jsonEscape(entry.tag)}\",")
            appendLine("        \"timestamp\": { \"seconds\": $seconds, \"nanos\": $nanos }")
            appendLine("      },")
            append("      \"message\": \"${jsonEscape(entry.message)}\"")
            appendLine()
            append("    }")
            if (i < entries.size - 1) append(",")
            appendLine()
        }
        appendLine("  ]")
        append("}")
    }

private fun jsonEscape(s: String): String =
    s
        .replace("\\", "\\\\")
        .replace("\"", "\\\"")
        .replace("\n", "\\n")
        .replace("\r", "\\r")
        .replace("\t", "\\t")
