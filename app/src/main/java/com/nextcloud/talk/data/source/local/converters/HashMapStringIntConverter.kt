/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2024 Your Name <your@email.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.data.source.local.converters

import com.bluelinelabs.logansquare.typeconverters.TypeConverter
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.core.JsonGenerator
import java.io.IOException

class HashMapStringIntConverter : TypeConverter<HashMap<String, Int>> {

    @Throws(IOException::class)
    override fun parse(jsonParser: JsonParser?): HashMap<String, Int> {
        val map: HashMap<String, Int> = hashMapOf()
        jsonParser?.apply {
            while (nextToken() != null) {
                val key = text
                nextToken()
                val value = intValue
                map[key] = value
            }
        }
        return map
    }

    @Throws(IOException::class)
    override fun serialize(
        `object`: HashMap<String, Int>?,
        fieldName: String?,
        writeFieldNameForObject: Boolean,
        jsonGenerator: JsonGenerator?
    ) {
        jsonGenerator?.apply {
            if (fieldName != null) {
                writeFieldName(fieldName)
            }
            writeStartObject()
            `object`?.forEach { (key, value) ->
                writeFieldName(key)
                writeNumber(value)
            }
            writeEndObject()
        }
    }
}
