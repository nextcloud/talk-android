/*
 * Nextcloud Talk application
 *
 * @author Mario Danic
 * Copyright (C) 2017-2018 Mario Danic <mario@lovelyhq.com>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.nextcloud.talk.models.json.converters;

import android.net.Uri;
import android.text.TextUtils;
import com.bluelinelabs.logansquare.typeconverters.StringBasedTypeConverter;

public class UriTypeConverter extends StringBasedTypeConverter<Uri> {
    @Override
    public Uri getFromString(String string) {
        if (!TextUtils.isEmpty(string)) {
            return Uri.parse(string);
        } else {
            return null;
        }
    }

    @Override
    public String convertToString(Uri object) {
        if (object != null) {
            return object.toString();
        } else {
            return null;
        }
    }
}
