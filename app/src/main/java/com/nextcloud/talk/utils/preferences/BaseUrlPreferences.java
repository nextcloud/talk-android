/*
 * Nextcloud Talk application
 *
 * @author Mario Danic
 * Copyright (C) 2017 Mario Danic <mario@lovelyhq.com>
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

package com.nextcloud.talk.utils.preferences;

import com.nextcloud.talk.R;

import net.orange_box.storebox.annotations.method.ClearMethod;
import net.orange_box.storebox.annotations.method.DefaultValue;
import net.orange_box.storebox.annotations.method.KeyByResource;
import net.orange_box.storebox.annotations.method.KeyByString;
import net.orange_box.storebox.annotations.method.RegisterChangeListenerMethod;
import net.orange_box.storebox.annotations.method.RemoveMethod;
import net.orange_box.storebox.annotations.method.UnregisterChangeListenerMethod;
import net.orange_box.storebox.annotations.option.SaveOption;
import net.orange_box.storebox.enums.SaveMode;
import net.orange_box.storebox.listeners.OnPreferenceValueChangedListener;

@SaveOption(SaveMode.APPLY)
public interface BaseUrlPreferences {

    @KeyByString("base_url1")
    String getBaseUrl();

    @KeyByString("base_url1")
    void setBaseUrl(String server_url);

    @ClearMethod
    void clear();
}
