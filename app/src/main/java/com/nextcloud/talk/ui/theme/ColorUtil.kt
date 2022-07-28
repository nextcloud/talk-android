/*
 * Nextcloud Talk application
 *
 * @author Andy Scherzinger
 * Copyright (C) 2022 Andy Scherzinger <info@andy-scherzinger.de>
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
package com.nextcloud.talk.ui.theme

import android.content.Context
import android.graphics.Color
import androidx.annotation.ColorInt
import androidx.annotation.ColorRes
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import com.nextcloud.talk.R

object ColorUtil {
    private const val HSL_SIZE: Int = 3
    private const val INDEX_LUMINATION: Int = 2
    private const val LUMINATION_DARK_THRESHOLD: Float = 0.6f

    fun getPrimaryColor(context: Context, primaryColor: String?, @ColorRes fallbackColor: Int): Int {
        return if (primaryColor != null) {
            Color.parseColor(primaryColor)
        } else {
            ContextCompat.getColor(context, fallbackColor)
        }
    }

    fun getNullsafeColor(color: String?, @ColorInt fallbackColor: Int): Int {
        return if (color != null) {
            Color.parseColor(color)
        } else {
            fallbackColor
        }
    }

    fun getTextColor(context: Context, colorText: String?, @ColorInt fallBackPrimaryColor: Int): Int {
        return if (colorText != null) {
            Color.parseColor(colorText)
        } else {
            getForegroundColorForBackgroundColor(context, fallBackPrimaryColor)
        }
    }

    @ColorInt
    public fun getForegroundColorForBackgroundColor(context: Context, @ColorInt color: Int): Int {
        val hsl = FloatArray(HSL_SIZE)
        ColorUtils.RGBToHSL(Color.red(color), Color.green(color), Color.blue(color), hsl)

        return if (hsl[INDEX_LUMINATION] < LUMINATION_DARK_THRESHOLD) {
            Color.WHITE
        } else {
            ContextCompat.getColor(context, R.color.grey_900)
        }
    }
}
