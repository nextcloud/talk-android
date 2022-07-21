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

import android.content.Context
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.widget.ImageView
import android.widget.TextView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import javax.inject.Inject

class ViewThemeUtils @Inject constructor(val theme: ServerTheme) {

    private fun isDarkMode(context: Context): Boolean = when (
        context.resources.configuration.uiMode and
            Configuration.UI_MODE_NIGHT_MASK
    ) {
        Configuration.UI_MODE_NIGHT_YES -> true
        else -> false
    }

    private fun getElementColor(context: Context): Int = when {
        isDarkMode(context) -> theme.colorElementDark
        else -> theme.colorElementBright
    }

    fun themeFAB(fab: FloatingActionButton) {
        fab.backgroundTintList = ColorStateList.valueOf(getElementColor(fab.context))
        fab.imageTintList = ColorStateList.valueOf(theme.colorText)
    }

    fun colorTextView(textView: TextView) {
        textView.setTextColor(getElementColor(textView.context))
    }

    /**
     * Colors the background as element color and the foreground as text color.
     */
    fun colorImageViewButton(imageView: ImageView) {
        imageView.imageTintList = ColorStateList.valueOf(theme.colorText)
        imageView.backgroundTintList = ColorStateList.valueOf(getElementColor(imageView.context))
    }
}
