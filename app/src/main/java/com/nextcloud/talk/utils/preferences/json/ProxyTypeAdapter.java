/*
 * Nextcloud Talk application
 *
 * @author Mario Danic
 * Copyright (C) 2017 Mario Danic
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

package com.nextcloud.talk.utils.preferences.json;

import android.support.annotation.Nullable;
import android.util.Log;

import com.bluelinelabs.logansquare.LoganSquare;

import net.orange_box.storebox.adapters.base.BaseStringTypeAdapter;

import java.io.IOException;

public class ProxyTypeAdapter extends BaseStringTypeAdapter<ProxyPrefs> {

    private static final String TAG = "ProxyTypeAdapter";

    @Nullable
    @Override
    public String adaptForPreferences(@Nullable ProxyPrefs value) {
        if (value != null) {
            try {
                return LoganSquare.serialize(value);
            } catch (IOException e) {
                Log.d(TAG, "Failed to serialize proxy from preferences");
            }
        }
        return "";
    }

    @Nullable
    @Override
    public ProxyPrefs adaptFromPreferences(@Nullable String value) {
        if (value != null) {
            try {
                return LoganSquare.parse(value, ProxyPrefs.class);
            } catch (IOException e) {
                Log.d(TAG, "Failed to parse proxy from preferences");
            }
        }
        return new ProxyPrefs();
    }
}
