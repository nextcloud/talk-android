/*
 *  Copyright (c) 2016 Touch Instinct
 *
 *  This file was part of RoboSwag library.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */

package com.moyn.talk.models.json.converters;


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
