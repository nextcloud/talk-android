/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2024 Julius Linus <juliuslinus1@gmail.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.data.source.local.converters

import android.util.Log
import androidx.room.TypeConverter
import com.bluelinelabs.logansquare.LoganSquare

class ArrayListConverter {

    @Suppress("Detekt.TooGenericExceptionCaught")
    @TypeConverter
    fun arrayListToString(list: ArrayList<String>?): String? {
        return if (list == null) {
            null
        } else {
            return try {
                LoganSquare.serialize(list)
            } catch (e: Exception) {
                Log.e("ArrayListConverter", "Error parsing array list $list to String $e")
                ""
            }
        }
    }

    @TypeConverter
    fun stringToArrayList(value: String?): ArrayList<String>? {
        if (value.isNullOrEmpty()) {
            return null
        }

        return LoganSquare.parseList(value, String::class.java) as ArrayList<String>?
    }

    @TypeConverter
    fun floatArrayListToString(list: ArrayList<Float>?): String? {
        return list?.joinToString(",")
    }

    @TypeConverter
    fun stringToFloatArrayList(value: String?): ArrayList<Float>? {
        return value?.let {
            if (it.isEmpty()) {
                ArrayList()
            } else {
                val list = it.drop(1).dropLast(1).split(',').map { str -> str.toFloat() }
                ArrayList(list)
            }
        }
    }
}
