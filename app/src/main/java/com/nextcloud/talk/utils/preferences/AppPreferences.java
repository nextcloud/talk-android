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

package com.nextcloud.talk.utils.preferences;

import com.nextcloud.talk.utils.preferences.json.ProxyPrefs;
import com.nextcloud.talk.utils.preferences.json.ProxyTypeAdapter;

import net.orange_box.storebox.annotations.method.ClearMethod;
import net.orange_box.storebox.annotations.method.KeyByString;
import net.orange_box.storebox.annotations.method.RemoveMethod;
import net.orange_box.storebox.annotations.method.TypeAdapter;
import net.orange_box.storebox.annotations.option.SaveOption;
import net.orange_box.storebox.enums.SaveMode;

@SaveOption(SaveMode.APPLY)
public interface AppPreferences {

    @KeyByString("proxy_server")
    @TypeAdapter(ProxyTypeAdapter.class)
    ProxyPrefs getProxyServer();

    @KeyByString("proxy_server")
    @TypeAdapter(ProxyTypeAdapter.class)
    void setProxyServer(ProxyPrefs proxyPrefsServer);

    @KeyByString("proxy_server")
    @RemoveMethod
    void removeProxyServer();

    @ClearMethod
    void clear();
}
