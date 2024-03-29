/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2017-2018 Mario Danic <mario@lovelyhq.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.models.json.converters;

import android.os.Parcel;
import org.parceler.ParcelConverter;
import org.parceler.Parcels;

public class ObjectParcelConverter implements ParcelConverter<Object> {
    @Override
    public void toParcel(Object input, Parcel parcel) {
        parcel.writeParcelable(Parcels.wrap(input), 0);
    }

    @Override
    public Object fromParcel(Parcel parcel) {
        return parcel.readParcelable(Object.class.getClassLoader());
    }
}
