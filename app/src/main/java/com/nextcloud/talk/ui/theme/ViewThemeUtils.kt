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
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.yarolegovich.mp.MaterialPreferenceCategory
import javax.inject.Inject

class ViewThemeUtils @Inject constructor(val theme: ServerTheme) {

    private fun isDarkMode(context: Context): Boolean = when (
        context.resources.configuration.uiMode and
            Configuration.UI_MODE_NIGHT_MASK
    ) {
        Configuration.UI_MODE_NIGHT_YES -> true
        else -> false
    }

    /**
     * Color for painting elements
     */
    fun getElementColor(context: Context): Int = when {
        isDarkMode(context) -> theme.colorElementDark
        else -> theme.colorElementBright
    }

    private fun withElementColor(view: View, block: (Int) -> Unit) {
        block(getElementColor(view.context))
    }

    fun themeFAB(fab: FloatingActionButton) {
        withElementColor(fab) { color ->
            fab.backgroundTintList = ColorStateList.valueOf(color)
            fab.imageTintList = ColorStateList.valueOf(theme.colorText)
        }
    }

    fun colorTextViewElement(textView: TextView) {
        withElementColor(textView) { color ->
            textView.setTextColor(color)
        }
    }

    fun colorTextViewText(textView: TextView) {
        textView.setTextColor(theme.colorText)
    }

    /**
     * Colors the background as element color and the foreground as text color.
     */
    fun colorImageViewButton(imageView: ImageView) {
        withElementColor(imageView) { color ->
            imageView.imageTintList = ColorStateList.valueOf(theme.colorText)
            imageView.backgroundTintList = ColorStateList.valueOf(color)
        }
    }

    /**
     * Tints the image with element color
     */
    fun colorImageView(imageView: ImageView) {
        withElementColor(imageView) { color ->
            imageView.imageTintList = ColorStateList.valueOf(color)
        }
    }

    /**
     * Tints the image with text color
     */
    fun colorImageViewText(imageView: ImageView) {
        imageView.imageTintList = ColorStateList.valueOf(theme.colorText)
    }

    fun colorMaterialButtonText(button: MaterialButton) {
        colorTextViewElement(button)
    }

    fun colorMaterialButtonBackground(button: MaterialButton) {
        withElementColor(button) { color ->
            button.setBackgroundColor(color)
            button.setTextColor(theme.colorText)
        }
    }

    fun colorCardViewBackground(card: MaterialCardView) {
        withElementColor(card) { color ->
            card.setCardBackgroundColor(color)
        }
    }

    // TODO split this util into classes depending on framework views vs library views
    fun colorPreferenceCategory(category: MaterialPreferenceCategory) {
        withElementColor(category) { color ->
            category.setTitleColor(color)
        }
    }
}
