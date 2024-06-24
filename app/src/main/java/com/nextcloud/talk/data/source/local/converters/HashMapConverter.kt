/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2024 Julius Linus <juliuslinus1@gmail.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.data.source.local.converters

import androidx.room.TypeConverter
import com.fasterxml.jackson.core.JsonFactory
import java.io.IOException

class HashMapConverter {
    private val converter = HashMapStringIntConverter()
    private val jsonFactory = JsonFactory()

    @TypeConverter
    fun hashMapToString(map: HashMap<String, Int>?): String {
        return try {
            val stringWriter = java.io.StringWriter()
            jsonFactory.createGenerator(stringWriter).use { generator ->
                converter.serialize(map ?: hashMapOf(), null, false, generator)
            }
            stringWriter.toString()
        } catch (e: IOException) {
            e.printStackTrace()
            ""
        }
    }

    @TypeConverter
    fun stringToHashMap(value: String?): HashMap<String, Int> {
        if (value.isNullOrEmpty()) {
            return hashMapOf()
        }
        return try {
            jsonFactory.createParser(value).use { parser ->
                converter.parse(parser)
            }
        } catch (e: IOException) {
            e.printStackTrace()
            hashMapOf()
        }
    }
}
