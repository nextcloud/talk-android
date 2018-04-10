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

package com.nextcloud.talk.controllers;

import android.support.annotation.IdRes;

import com.bluelinelabs.conductor.Controller;
import com.nextcloud.talk.R;
import com.nextcloud.talk.controllers.base.bottomnavigation.BottomNavigationController;
import com.nextcloud.talk.controllers.base.bottomnavigation.BottomNavigationMenuItem;

import java.lang.reflect.Constructor;

public class MagicBottomNavigationController extends BottomNavigationController {

    public MagicBottomNavigationController() {
        super(R.menu.menu_navigation);
    }

    /**
     * Supplied MenuItemId must match a {@link Controller} as defined in {@link
     * BottomNavigationMenuItem} or an {@link IllegalArgumentException} will be thrown.
     *
     * @param itemId
     */
    @Override
    protected Controller getControllerFor(@IdRes int itemId) {
        Constructor[] constructors =
                BottomNavigationMenuItem.getEnum(itemId).getControllerClass().getConstructors();
        Controller controller = null;
        try {
            /* Determine default or Bundle constructor */
            for (Constructor constructor : constructors) {
                if (constructor.getParameterTypes().length == 0) {
                    controller = (Controller) constructor.newInstance();
                }
            }
        } catch (Exception e) {
            throw new RuntimeException(
                    "An exception occurred while creating a new instance for mapping of "
                            + itemId
                            + ". "
                            + e.getMessage(),
                    e);
        }

        if (controller == null) {
            throw new RuntimeException(
                    "Controller must have a public empty constructor. "
                            + itemId);
        }
        return controller;
    }
}
