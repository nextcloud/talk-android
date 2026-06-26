/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.errorhandling

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.util.Log
import com.nextcloud.talk.dagger.modules.UtilsModule
import java.io.File

class ExceptionHandler(
    private val context: Context,
    private val defaultExceptionHandler: Thread.UncaughtExceptionHandler,
    private val diagnosisSupplier: (() -> String)? = null,
    private val logCrash: ((String, Throwable) -> Unit)? = null,
    private val logFlusher: (() -> Unit)? = null
) : Thread.UncaughtExceptionHandler {

    companion object {
        private const val TAG = "ExceptionHandler"
        private const val CRASH_ACTIVITY_START_DELAY_MS = 500L
    }

    override fun uncaughtException(thread: Thread, exception: Throwable) {
        Log.e(TAG, "uncaughtException called on thread=${thread.name}", exception)
        @Suppress("TooGenericExceptionCaught")
        try {
            logCrash?.invoke("Uncaught exception in thread \"${thread.name}\"", exception)
            val diagnosis = try {
                diagnosisSupplier?.invoke()?.takeIf { it.isNotEmpty() }
            } catch (
                _: Exception
            ) {
                null
            }
            val report = buildReport(diagnosis)
            val summary = exception.javaClass.simpleName +
                (exception.message?.let { ": $it" } ?: "")
            val stackTrace = "Exception in thread \"${thread.name}\"\n" +
                Log.getStackTraceString(exception)
            // FLAG_ACTIVITY_CLEAR_TASK is intentionally omitted: the app uses
            // android:taskAffinity="" globally, so CLEAR_TASK never finds a task to
            // clear and ShowErrorActivity always lands in a new task regardless.
            val intent = Intent(context, ShowErrorActivity::class.java).apply {
                putExtra(ShowErrorActivity.EXTRA_CRASH_REPORT, report)
                putExtra(ShowErrorActivity.EXTRA_CRASH_TITLE, summary)
                putExtra(ShowErrorActivity.EXTRA_CRASH_STACKTRACE, stackTrace)
                if (diagnosis != null) putExtra(ShowErrorActivity.EXTRA_CRASH_DIAGNOSIS, diagnosis)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            Log.e(TAG, "Starting ShowErrorActivity")
            context.startActivity(intent)
            // Wait for ShowErrorActivity to become visible before we remove the old task.
            try {
                Thread.sleep(CRASH_ACTIVITY_START_DELAY_MS)
            } catch (_: InterruptedException) {
            }

            // Now that the crash screen is in the foreground, remove all other app tasks
            // from recents so the user only sees the crash screen. This must happen after
            // the sleep so there is no visual flash to the home screen.
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val crashClass = ShowErrorActivity::class.java.name
            for (task in am.appTasks) {
                val info = task.taskInfo ?: continue
                val isCrashTask = info.topActivity?.className == crashClass ||
                    info.baseActivity?.className == crashClass
                if (!isCrashTask) {
                    task.finishAndRemoveTask()
                }
            }
        } catch (fatal: Exception) {
            Log.e(TAG, "Fatal error in ExceptionHandler itself", fatal)
            defaultExceptionHandler.uncaughtException(thread, fatal)
            return
        }
        // Forward to the default handler so AMS records the crash as REASON_CRASH and
        // Play Console collects it. This runs after the crash screen is visible and
        // recents have been cleaned up, so the default handler's own process kill is
        // the last thing that happens.
        defaultExceptionHandler.uncaughtException(thread, exception)
    }

    private fun buildReport(diagnosis: String?): String =
        buildString {
            logFlusher?.invoke()
            val logs = readRecentLogs()
            if (logs.isNotEmpty()) {
                appendLine("# Recent logs")
                appendLine("```")
                appendLine(logs)
                appendLine("```")
                appendLine()
            }
            if (diagnosis != null) {
                appendLine("# Diagnosis report")
                append(diagnosis)
            }
        }

    private fun readRecentLogs(maxLines: Int = 200): String {
        val logFile = File(context.filesDir, "${UtilsModule.LOG_DIR_NAME}/${UtilsModule.LOG_FILE_NAME}")
        return try {
            val lines = logFile.readLines(Charsets.UTF_8)
            val recent = if (lines.size > maxLines) lines.takeLast(maxLines) else lines
            recent.joinToString("\n")
        } catch (_: Exception) {
            ""
        }
    }
}
