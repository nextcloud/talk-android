/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2024 Marcel Hibbe <dev@mhibbe.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.data.source.local.converters

import android.util.Log
import androidx.room.TypeConverter
import com.fasterxml.jackson.core.JsonFactory
import com.nextcloud.talk.application.NextcloudTalkApplication
import java.io.IOException

class LinkedHashMapConverter {

    private val converter = LinkedHashMapStringIntConverter()
    private val jsonFactory = JsonFactory()

    @TypeConverter
    fun stringToLinkedHashMap(value: String?): LinkedHashMap<String, Int> {
        if (value.isNullOrEmpty() || value == "{}") {
            return linkedMapOf()
        }
        // "{"👍":1,"👎":1,"😃":1,"😯":1}" // pretend this is value
        return try {
            val map = linkedMapOf<String, Int>()
            val trimmed = value.replace("{", "").replace("}", "")
            // "👍":1,"👎":1,"😃":1,"😯":1
            val mapList = trimmed.split(",")
            // ["👍":1]["👎":1]["😃":1]["😯":1]
            for (mapStr in mapList) {
                val emojiMapList = mapStr.split(":")
                val emoji = emojiMapList[0].replace("\"", "") // removes double quotes
                val count = emojiMapList[1].toInt()
                map[emoji] = count
            }
            // [👍:1],[👎:1],[😃:1],[😯:1]
            return map
        } catch (e: IOException) {
            Log.e("LinkedHashMapConverter", "Error parsing string: $value to linkedHashMap $e")
            linkedMapOf()
        }
    }

    @TypeConverter
    fun linkedHashMapToString(map: LinkedHashMap<String, Int>?): String =
        try {
            val stringWriter = java.io.StringWriter()
            jsonFactory.createGenerator(stringWriter).use { generator ->
                converter.serialize(map ?: linkedMapOf(), null, false, generator)
            }
            stringWriter.toString()
        } catch (e: IOException) {
            NextcloudTalkApplication.sharedApplication?.logger?.w(
                "LinkedHashMapConverter",
                "Error serializing linkedHashMap to string",
                e
            )
            ""
        }
}
