/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2024 Julius Linus <juliuslinus1@gmail.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.data.source.local.converters

import androidx.room.TypeConverter
import com.bluelinelabs.logansquare.LoganSquare

class ArrayListConverter {

    @TypeConverter
    fun arrayListToString(list: ArrayList<String>?): String? {
        return if (list == null) {
            LoganSquare.serialize(listOf<String>())
        } else {
            return LoganSquare.serialize(list)
        }
    }

    @TypeConverter
    fun stringToArrayList(value: String?): ArrayList<String>? {
        if (value.isNullOrEmpty()) {
            return listOf<String>() as ArrayList<String>
        }

        return LoganSquare.parseList(value, List::class.java) as ArrayList<String>?
    }
}
