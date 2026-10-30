/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.models.json.signaling

import com.bluelinelabs.logansquare.LoganSquare
import com.bluelinelabs.logansquare.typeconverters.TypeConverter
import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.core.JsonToken

/**
 * The "key" of a key exchange payload: a String in "encryption.start" messages, an object with "type" and "body" in
 * the other ones. LoganSquare writes an untyped field without its name, so this converter handles both directions.
 */
class EncryptionKeyConverter : TypeConverter<Any?> {
    override fun parse(jsonParser: JsonParser): Any? =
        when (jsonParser.currentToken) {
            JsonToken.VALUE_STRING -> jsonParser.text
            JsonToken.START_OBJECT -> LoganSquare.mapperFor(Any::class.java).parse(jsonParser)
            else -> {
                jsonParser.skipChildren()
                null
            }
        }

    override fun serialize(
        value: Any?,
        fieldName: String,
        writeFieldNameForObject: Boolean,
        jsonGenerator: JsonGenerator
    ) {
        if (value == null) {
            return
        }
        if (writeFieldNameForObject) {
            jsonGenerator.writeFieldName(fieldName)
        }
        if (value is String) {
            jsonGenerator.writeString(value)
        } else {
            LoganSquare.mapperFor(Any::class.java).serialize(value, jsonGenerator, true)
        }
    }
}
