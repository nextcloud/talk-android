/*
 * Nextcloud Talk application
 *
 * @author Mario Danic
 * Copyright (C) 2017-2018 Mario Danic <mario@lovelyhq.com>
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

import android.content.res.Resources
import android.icu.text.RelativeDateTimeFormatter
import android.icu.text.RelativeDateTimeFormatter.Direction
import android.icu.text.RelativeDateTimeFormatter.RelativeUnit
import android.os.Build
import com.nextcloud.talk.R
import java.text.DateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

object DateUtils {

    private const val TIMESTAMP_CORRECTION_MULTIPLIER = 1000

    fun getLocalDateTimeStringFromTimestamp(timestamp: Long): String {
        val cal = Calendar.getInstance()
        val tz = cal.timeZone

        /* date formatter in local timezone */
        val format = DateFormat.getDateTimeInstance(
            DateFormat.DEFAULT, DateFormat.SHORT,
            Locale.getDefault()
        )
        format.timeZone = tz

        return format.format(Date(timestamp))
    }

    fun getLocalDateStringFromTimestampForLobby(timestamp: Long): String {
        return getLocalDateTimeStringFromTimestamp(timestamp * TIMESTAMP_CORRECTION_MULTIPLIER)
    }

    fun relativeStartTimeForLobby(timestamp: Long, resources: Resources): String {

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            val fmt = RelativeDateTimeFormatter.getInstance()
            val timeLeftMillis = timestamp * TIMESTAMP_CORRECTION_MULTIPLIER - System.currentTimeMillis()
            val minutes = timeLeftMillis.toDouble() / DateConstants.SECOND_DIVIDER / DateConstants.MINUTES_DIVIDER
            val hours = minutes / DateConstants.HOURS_DIVIDER
            val days = hours / DateConstants.DAYS_DIVIDER

            val minutesInt = minutes.roundToInt()
            val hoursInt = hours.roundToInt()
            val daysInt = days.roundToInt()

            when {
                daysInt > 0 -> {
                    fmt.format(
                        daysInt.toDouble(),
                        Direction.NEXT,
                        RelativeUnit.DAYS
                    )
                }
                hoursInt > 0 -> {
                    fmt.format(
                        hoursInt.toDouble(),
                        Direction.NEXT,
                        RelativeUnit.HOURS
                    )
                }
                minutesInt > 1 -> {
                    fmt.format(
                        minutesInt.toDouble(),
                        Direction.NEXT,
                        RelativeUnit.MINUTES
                    )
                }
                else -> {
                    resources.getString(R.string.nc_lobby_start_soon)
                }
            }
        } else {
            ""
        }
    }
}
