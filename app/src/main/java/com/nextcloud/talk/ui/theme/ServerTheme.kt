/*
 * Nextcloud Talk application
 *
 * @author Álvaro Brey
 * Copyright (C) 2022 Álvaro Brey
 * Copyright (C) 2022 Nextcloud GmbH
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

package com.nextcloud.talk.ui.theme

import androidx.annotation.ColorInt
import scheme.Scheme

interface ServerTheme {
    @get:ColorInt
    val primaryColor: Int

    /**
     * Default element color
     */
    @get:ColorInt
    val colorElement: Int

    /**
     * Element color for bright backgrounds
     */
    @get:ColorInt
    val colorElementBright: Int

    /**
     * Element color for dark backgrounds
     */
    @get:ColorInt
    val colorElementDark: Int

    /**
     * Text color for elements
     */
    @get:ColorInt
    val colorText: Int

    /**
     * Schema for light theme
     */
    val lightScheme: Scheme

    /**
     * Schema for light theme
     */
    val darkScheme: Scheme
}
