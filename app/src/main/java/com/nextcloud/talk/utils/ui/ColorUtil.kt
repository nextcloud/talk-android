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
package com.nextcloud.talk.utils.ui

import android.content.Context
import android.graphics.Color
import androidx.annotation.ColorInt
import androidx.annotation.ColorRes
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import com.nextcloud.talk.R
import javax.inject.Inject

class ColorUtil @Inject constructor(private val context: Context) {

    @ColorInt
    fun getNullSafeColor(color: String?, @ColorInt fallbackColor: Int): Int {
        return color.parseColorOrFallback { fallbackColor }
    }

    @ColorInt
    fun getNullSafeColorWithFallbackRes(color: String?, @ColorRes fallbackColorRes: Int): Int {
        return color.parseColorOrFallback { ContextCompat.getColor(context, fallbackColorRes) }
    }

    @ColorInt
    fun getTextColor(colorText: String?, @ColorInt backgroundColor: Int): Int {
        return colorText.parseColorOrFallback { getForegroundColorForBackgroundColor(backgroundColor) }
    }

    @ColorInt
    fun getForegroundColorForBackgroundColor(@ColorInt color: Int): Int {
        val hsl = FloatArray(HSL_SIZE)
        ColorUtils.RGBToHSL(Color.red(color), Color.green(color), Color.blue(color), hsl)

        return if (hsl[INDEX_LIGHTNESS] < LIGHTNESS_DARK_THRESHOLD) {
            Color.WHITE
        } else {
            ContextCompat.getColor(context, R.color.grey_900)
        }
    }

    fun setLightness(@ColorInt color: Int, lightness: Float): Int {
        require(lightness in 0.0..1.0) { "Lightness must be between 0 and 1" }
        val hsl = FloatArray(HSL_SIZE)
        ColorUtils.RGBToHSL(Color.red(color), Color.green(color), Color.blue(color), hsl)

        hsl[INDEX_LIGHTNESS] = lightness

        return ColorUtils.HSLToColor(hsl)
    }

    @ColorInt
    private fun String?.parseColorOrFallback(fallback: () -> Int): Int {
        return this?.let { Color.parseColor(this) } ?: fallback()
    }

    companion object {
        private const val HSL_SIZE: Int = 3
        private const val INDEX_LIGHTNESS: Int = 2
        private const val LIGHTNESS_DARK_THRESHOLD: Float = 0.6f
    }
}
