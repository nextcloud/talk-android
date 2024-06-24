/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2024 Marcel Hibbe <dev@mhibbe.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.data.source.local.converters

import androidx.room.TypeConverter
import com.fasterxml.jackson.core.JsonFactory
import java.io.IOException

class LinkedHashMapConverter {

    private val converter = LinkedHashMapStringIntConverter()
    private val jsonFactory = JsonFactory()

    @TypeConverter
    fun stringToLinkedHashMap(value: String?): LinkedHashMap<String, Int> {
        if (value.isNullOrEmpty()) {
            return linkedMapOf()
        }
        return try {
            jsonFactory.createParser(value).use { parser ->
                converter.parse(parser)
            }
        } catch (e: IOException) {
            e.printStackTrace()
            linkedMapOf()
        }
    }

    @TypeConverter
    fun linkedHashMapToString(map: LinkedHashMap<String, Int>?): String {
        return try {
            val stringWriter = java.io.StringWriter()
            jsonFactory.createGenerator(stringWriter).use { generator ->
                converter.serialize(map ?: linkedMapOf(), null, false, generator)
            }
            stringWriter.toString()
        } catch (e: IOException) {
            e.printStackTrace()
            ""
        }
    }
}
