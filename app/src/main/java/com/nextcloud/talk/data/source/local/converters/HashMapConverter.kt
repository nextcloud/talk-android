/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2024 Julius Linus <juliuslinus1@gmail.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.data.source.local.converters

import androidx.room.TypeConverter
import com.bluelinelabs.logansquare.LoganSquare

class HashMapConverter {
    @TypeConverter
    fun linkedHashMapToString(map: HashMap<String, Int>?): String? {
        return if (map == null) {
            LoganSquare.serialize(hashMapOf<String, Int>())
        } else {
            return LoganSquare.serialize(HashMap<String, Int>(map))
        }
    }

    @TypeConverter
    fun stringToLinkedHashMap(value: String?): HashMap<String, Int>? {
        if (value.isNullOrEmpty()) {
            return hashMapOf()
        }

        return LoganSquare.parseMap(value, HashMap::class.java) as HashMap<String, Int>?
    }
}
