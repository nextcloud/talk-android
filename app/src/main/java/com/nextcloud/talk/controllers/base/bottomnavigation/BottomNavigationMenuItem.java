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
 *
 * The bottom navigation was taken from a PR to Conductor by Chris6647@gmail.com
 * https://github.com/bluelinelabs/Conductor/pull/316 and https://github.com/chris6647/Conductor/pull/1/files
 * and of course modified by yours truly.
 */

package com.nextcloud.talk.controllers.base.bottomnavigation;

import android.support.annotation.IdRes;

import com.bluelinelabs.conductor.Controller;
import com.nextcloud.talk.R;
import com.nextcloud.talk.controllers.CallsListController;
import com.nextcloud.talk.controllers.ContactsController;
import com.nextcloud.talk.controllers.SettingsController;
import com.nextcloud.talk.utils.BottomNavigationUtils;

/**
 * Enum representation of valid Bottom Navigation Menu Items
 */
public enum BottomNavigationMenuItem {
    CALLS(R.id.navigation_calls, CallsListController.class),
    CONTACTS(R.id.navigation_contacts, ContactsController.class),
    SETTINGS(R.id.navigation_settings, SettingsController.class);

    private int menuResId;
    private Class<? extends Controller> controllerClass;

    BottomNavigationMenuItem(@IdRes int menuResId, Class<? extends Controller> controllerClass) {
        this.menuResId = menuResId;
        this.controllerClass = controllerClass;
    }

    public static BottomNavigationMenuItem getEnum(@IdRes int menuResId) {
        for (BottomNavigationMenuItem type : BottomNavigationMenuItem.values()) {
            if (menuResId == type.getMenuResId()) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unable to map " + menuResId);
    }

    public static BottomNavigationMenuItem getEnum(Class<? extends Controller> controllerClass) {
        for (BottomNavigationMenuItem type : BottomNavigationMenuItem.values()) {
            if (BottomNavigationUtils.equals(controllerClass, type.getControllerClass())) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unable to map " + controllerClass);
    }

    public int getMenuResId() {
        return menuResId;
    }

    public Class<? extends Controller> getControllerClass() {
        return controllerClass;
    }

    @Override
    public String toString() {
        return "BottomNavigationMenuItem{"
                + "menuResId="
                + menuResId
                + ", controllerClass="
                + controllerClass
                + '}';
    }
}
