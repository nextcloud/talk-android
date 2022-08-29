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

package com.nextcloud.talk.ui.theme.viewthemeutils

import android.app.Activity
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.PorterDuff
import android.view.MenuItem
import android.view.View
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.RadioButton
import android.widget.SeekBar
import android.widget.TextView
import androidx.annotation.ColorInt
import androidx.core.content.res.ResourcesCompat
import com.nextcloud.android.common.ui.color.ColorUtil
import com.nextcloud.android.common.ui.theme.MaterialSchemes
import com.nextcloud.android.common.ui.theme.ViewThemeUtilsBase
import com.nextcloud.talk.R
import com.nextcloud.talk.utils.DisplayUtils
import javax.inject.Inject

/**
 * View theme utils for platform views (android.widget.*, android.view.*)
 */
@Suppress("TooManyFunctions")
class AndroidViewThemeUtils @Inject constructor(schemes: MaterialSchemes, private val colorUtil: ColorUtil) :
    ViewThemeUtilsBase(schemes) {

    fun colorViewBackground(view: View) {
        withScheme(view) { scheme ->
            view.setBackgroundColor(scheme.surface)
        }
    }

    fun colorToolbarMenuIcon(context: Context, item: MenuItem) {
        withScheme(context) { scheme ->
            item.icon.setColorFilter(scheme.onSurface, PorterDuff.Mode.SRC_ATOP)
        }
    }

    fun themeStatusBar(activity: Activity, view: View) {
        withScheme(view) { scheme ->
            DisplayUtils.applyColorToStatusBar(activity, scheme.surface)
        }
    }

    fun resetStatusBar(activity: Activity, view: View) {
        DisplayUtils.applyColorToStatusBar(
            activity,
            ResourcesCompat.getColor(
                activity.resources,
                R.color.bg_default,
                activity.theme
            )
        )
    }

    fun themeDialog(view: View) {
        withScheme(view) { scheme ->
            view.setBackgroundColor(scheme.surface)
        }
    }

    fun themeDialogDark(view: View) {
        withSchemeDark { scheme ->
            view.setBackgroundColor(scheme.surface)
        }
    }

    fun themeDialogDivider(view: View) {
        withScheme(view) { scheme ->
            view.setBackgroundColor(scheme.surfaceVariant)
        }
    }

    fun themeHorizontalSeekBar(seekBar: SeekBar) {
        withScheme(seekBar) { scheme ->
            themeHorizontalProgressBar(seekBar, scheme.primary)
            seekBar.thumb.setColorFilter(scheme.primary, PorterDuff.Mode.SRC_IN)
        }
    }

    fun themeHorizontalProgressBar(progressBar: ProgressBar?, @ColorInt color: Int) {
        if (progressBar != null) {
            progressBar.indeterminateDrawable.setColorFilter(color, PorterDuff.Mode.SRC_IN)
            progressBar.progressDrawable.setColorFilter(color, PorterDuff.Mode.SRC_IN)
        }
    }

    fun colorPrimaryTextViewElement(textView: TextView) {
        withScheme(textView) { scheme ->
            textView.setTextColor(scheme.primary)
        }
    }

    fun colorPrimaryTextViewElementDarkMode(textView: TextView) {
        withSchemeDark { scheme ->
            textView.setTextColor(scheme.primary)
        }
    }

    fun colorPrimaryView(view: View) {
        withScheme(view) { scheme ->
            view.setBackgroundColor(scheme.primary)
        }
    }

    /**
     * Colors the background as element color and the foreground as text color.
     */
    fun colorImageViewButton(imageView: ImageView) {
        withScheme(imageView) { scheme ->
            imageView.imageTintList = ColorStateList.valueOf(scheme.onPrimaryContainer)
            imageView.backgroundTintList = ColorStateList.valueOf(scheme.primaryContainer)
        }
    }

    fun themeImageButton(imageButton: ImageButton) {
        withScheme(imageButton) { scheme ->
            imageButton.imageTintList = ColorStateList(
                arrayOf(
                    intArrayOf(android.R.attr.state_selected),
                    intArrayOf(-android.R.attr.state_selected),
                    intArrayOf(android.R.attr.state_enabled),
                    intArrayOf(-android.R.attr.state_enabled)
                ),
                intArrayOf(
                    scheme.primary,
                    scheme.onSurfaceVariant,
                    scheme.onSurfaceVariant,
                    colorUtil.adjustOpacity(scheme.onSurface, ON_SURFACE_OPACITY_BUTTON_DISABLED)
                )
            )
        }
    }

    /**
     * Tints the image with element color
     */
    fun colorImageView(imageView: ImageView) {
        withScheme(imageView) { scheme ->
            imageView.imageTintList = ColorStateList.valueOf(scheme.primary)
        }
    }

    fun colorTextButtons(vararg buttons: Button) {
        withScheme(buttons[0]) { scheme ->
            for (button in buttons) {
                button.setTextColor(
                    ColorStateList(
                        arrayOf(
                            intArrayOf(android.R.attr.state_enabled),
                            intArrayOf(-android.R.attr.state_enabled)
                        ),
                        intArrayOf(
                            scheme.primary,
                            colorUtil.adjustOpacity(scheme.onSurface, ON_SURFACE_OPACITY_BUTTON_DISABLED)
                        )
                    )
                )
            }
        }
    }

    fun colorCircularProgressBarOnPrimaryContainer(progressBar: ProgressBar) {
        withScheme(progressBar) { scheme ->
            progressBar.indeterminateDrawable.setColorFilter(scheme.onPrimaryContainer, PorterDuff.Mode.SRC_ATOP)
        }
    }

    fun colorCircularProgressBar(progressBar: ProgressBar) {
        withScheme(progressBar) { scheme ->
            progressBar.indeterminateDrawable.setColorFilter(scheme.primary, PorterDuff.Mode.SRC_ATOP)
        }
    }

    fun colorCircularProgressBarOnSurfaceVariant(progressBar: ProgressBar) {
        withScheme(progressBar) { scheme ->
            progressBar.indeterminateDrawable.setColorFilter(scheme.onSurfaceVariant, PorterDuff.Mode.SRC_ATOP)
        }
    }

    fun themeCheckbox(checkbox: CheckBox) {
        withScheme(checkbox) { scheme ->
            checkbox.buttonTintList = ColorStateList(
                arrayOf(
                    intArrayOf(-android.R.attr.state_checked),
                    intArrayOf(android.R.attr.state_checked)
                ),
                intArrayOf(Color.GRAY, scheme.primary)
            )
        }
    }

    fun themeRadioButton(radioButton: RadioButton) {
        withScheme(radioButton) { scheme ->
            radioButton.buttonTintList = ColorStateList(
                arrayOf(
                    intArrayOf(-android.R.attr.state_checked),
                    intArrayOf(android.R.attr.state_checked)
                ),
                intArrayOf(Color.GRAY, scheme.primary)
            )
        }
    }

    fun colorEditText(editText: EditText) {
        withScheme(editText) { scheme ->
            // TODO check API-level compatibility
            // editText.background.setColorFilter(color, PorterDuff.Mode.SRC_ATOP)
            editText.backgroundTintList = ColorStateList(
                arrayOf(
                    intArrayOf(-android.R.attr.state_focused),
                    intArrayOf(android.R.attr.state_focused)
                ),
                intArrayOf(
                    scheme.outline,
                    scheme.primary
                )
            )
            editText.setHintTextColor(scheme.onSurfaceVariant)
            editText.setTextColor(scheme.onSurface)
        }
    }

    companion object {
        private const val ON_SURFACE_OPACITY_BUTTON_DISABLED: Float = 0.38f
    }
}
