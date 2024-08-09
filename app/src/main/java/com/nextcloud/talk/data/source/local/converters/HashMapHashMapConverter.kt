/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2022 Andy Scherzinger <info@andy-scherzinger.de>
 * SPDX-FileCopyrightText: 2017-2020 Mario Danic <mario@lovelyhq.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.data.source.local.converters

import androidx.room.TypeConverter
import com.bluelinelabs.logansquare.LoganSquare

class HashMapHashMapConverter {
    @TypeConverter
    fun fromDoubleHashMapToString(map: HashMap<String?, HashMap<String?, String?>>?): String? {
        return if (map == null) {
            LoganSquare.serialize(hashMapOf<String, HashMap<String, String>>())
        } else {
            return LoganSquare.serialize(map)
        }
    }

    @TypeConverter
    fun fromStringToDoubleHashMap(value: String?): HashMap<String?, HashMap<String?, String?>>? {
        if (value.isNullOrEmpty()) {
            return hashMapOf()
        }

        return LoganSquare.parseMap(value, HashMap::class.java) as HashMap<String?, HashMap<String?, String?>>?
    }
}
