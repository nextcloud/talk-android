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

import android.content.Context
import android.content.res.Resources
import android.icu.text.RelativeDateTimeFormatter
import android.icu.text.RelativeDateTimeFormatter.Direction
import android.icu.text.RelativeDateTimeFormatter.RelativeUnit
import com.nextcloud.talk.R
import java.text.DateFormat
import java.util.Calendar
import java.util.Date
import kotlin.math.roundToInt

class DateUtils(val context: Context) {
    private val cal = Calendar.getInstance()
    private val tz = cal.timeZone

    /* date formatter in local timezone and locale */
    private var format: DateFormat = DateFormat.getDateTimeInstance(
        DateFormat.DEFAULT, // dateStyle
        DateFormat.SHORT, // timeStyle
        context.resources.configuration.locales[0]
    )

    /* date formatter in local timezone and locale */
    private var formatTime: DateFormat = DateFormat.getTimeInstance(
        DateFormat.SHORT, // timeStyle
        context.resources.configuration.locales[0]
    )

    init {
        format.timeZone = tz
        formatTime.timeZone = tz
    }

    fun getLocalDateTimeStringFromTimestamp(timestampMilliseconds: Long): String {
        return format.format(Date(timestampMilliseconds))
    }

    fun getLocalTimeStringFromTimestamp(timestampSeconds: Long): String {
        return formatTime.format(Date(timestampSeconds * DateConstants.SECOND_DIVIDER))
    }

    fun relativeStartTimeForLobby(timestampMilliseconds: Long, resources: Resources): String {
        val fmt = RelativeDateTimeFormatter.getInstance()
        val timeLeftMillis = timestampMilliseconds - System.currentTimeMillis()
        val minutes = timeLeftMillis.toDouble() / DateConstants.SECOND_DIVIDER / DateConstants.MINUTES_DIVIDER
        val hours = minutes / DateConstants.HOURS_DIVIDER
        val days = hours / DateConstants.DAYS_DIVIDER

        val minutesInt = minutes.roundToInt()
        val hoursInt = hours.roundToInt()
        val daysInt = days.roundToInt()

        return when {
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
    }
}
