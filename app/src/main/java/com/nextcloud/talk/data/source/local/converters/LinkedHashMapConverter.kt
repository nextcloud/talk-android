/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2024 Julius Linus <juliuslinus1@gmail.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.data.source.local.converters

import androidx.room.TypeConverter
import com.bluelinelabs.logansquare.LoganSquare

class LinkedHashMapConverter {
    @TypeConverter
    fun linkedHashMapToString(map: LinkedHashMap<String, Int>?): String? {
        return if (map == null) {
            LoganSquare.serialize(hashMapOf<String, Int>())
        } else {
            return LoganSquare.serialize(map)
        }
    }

    @TypeConverter
    fun stringToLinkedHashMap(value: String?): LinkedHashMap<String, Int>? {
        if (value.isNullOrEmpty()) {
            return hashMapOf<String, Int>() as LinkedHashMap<String, Int>
        }

        return LoganSquare.parseMap(value, HashMap::class.java) as LinkedHashMap<String, Int>?
    }
}
