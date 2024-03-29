/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2017-2018 Mario Danic <mario@lovelyhq.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
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
