/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2016 Touch Instinct
 * SPDX-License-Identifier: Apache-2.0
 *
 * This file was part of RoboSwag library.
 */
package com.nextcloud.talk.models.json.converters;

import android.util.Log;

import com.bluelinelabs.logansquare.typeconverters.TypeConverter;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;

import org.joda.time.DateTime;

import java.io.IOException;

public class LoganSquareJodaTimeConverter implements TypeConverter<DateTime> {
    private static final String TAG = LoganSquareJodaTimeConverter.class.getSimpleName();

    @Override
    public DateTime parse(JsonParser jsonParser) throws IOException {
        final String dateString = jsonParser.getValueAsString();
        if (dateString == null) {
            return null;
        }
        try {
            return DateTime.parse(dateString);
        } catch (final RuntimeException exception) {
            Log.e(TAG, exception.getLocalizedMessage(), exception);
        }
        return null;
    }

    @Override
    public void serialize(DateTime object, String fieldName, boolean writeFieldNameForObject, JsonGenerator jsonGenerator) throws IOException {
        if (fieldName != null) {
            jsonGenerator.writeStringField(fieldName, object != null ? object.toString() : null);
        } else {
            jsonGenerator.writeString(object != null ? object.toString() : null);
        }
    }
}
