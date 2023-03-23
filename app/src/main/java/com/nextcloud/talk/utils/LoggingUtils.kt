/*
 * Nextcloud Talk application
 *
 * @author Mario Danic
 * Copyright (C) 2017-2019 Mario Danic <mario@lovelyhq.com>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.nextcloud.talk.utils

import android.content.Context

// TODO improve log handling. https://github.com/nextcloud/talk-android/issues/1376
// writing logs to a file is temporarily disabled to avoid huge logfiles.

object LoggingUtils {
    fun writeLogEntryToFile(context: Context, logEntry: String) {
        // val dateFormat = SimpleDateFormat("yyyy/MM/dd HH:mm:ss", Locale.ROOT)
        // val date = Date()
        // val logEntryWithDateTime = dateFormat.format(date) + ": " + logEntry + "\n"
        //
        // try {
        //     val outputStream = context.openFileOutput(
        //         "nc_log.txt",
        //         Context.MODE_PRIVATE or Context.MODE_APPEND
        //     )
        //     outputStream.write(logEntryWithDateTime.toByteArray())
        //     outputStream.flush()
        //     outputStream.close()
        // } catch (e: FileNotFoundException) {
        //     e.printStackTrace()
        // } catch (e: IOException) {
        //     e.printStackTrace()
        // }
    }

    fun sendMailWithAttachment(context: Context) {
        // val logFile = context.getFileStreamPath("nc_log.txt")
        // val emailIntent = Intent(Intent.ACTION_SEND)
        // val mailto = "android@nextcloud.com"
        // emailIntent.putExtra(Intent.EXTRA_EMAIL, arrayOf(mailto))
        // emailIntent.putExtra(Intent.EXTRA_SUBJECT, "Talk logs")
        // emailIntent.type = TEXT_PLAIN
        // emailIntent.flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
        // val uri: Uri
        // uri = FileProvider.getUriForFile(context, BuildConfig.APPLICATION_ID, logFile)
        //
        // emailIntent.putExtra(Intent.EXTRA_STREAM, uri)
        // emailIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        // context.startActivity(emailIntent)
    }
}
