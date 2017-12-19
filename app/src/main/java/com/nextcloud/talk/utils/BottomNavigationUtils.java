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

package com.nextcloud.talk.utils;

public class BottomNavigationUtils {

    /**
     * Copy/paste from {@link java.util.Objects#equals(Object, Object)} to support lower API version
     *
     * @param a
     * @param b
     * @return
     */
    public static boolean equals(Object a, Object b) {
        return (a == b) || (a != null && a.equals(b));
    }
}
