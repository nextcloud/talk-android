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
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.view.View
import android.widget.CheckBox
import android.widget.EditText
import android.widget.ImageView
import android.widget.RadioButton
import android.widget.TextView
import androidx.appcompat.content.res.AppCompatResources
import androidx.appcompat.widget.SwitchCompat
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.ColorUtils
import androidx.core.view.children
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.chip.Chip
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.google.android.material.tabs.TabLayout
import com.google.android.material.textfield.TextInputLayout
import com.nextcloud.talk.R
import com.nextcloud.talk.utils.DrawableUtils
import com.yarolegovich.mp.MaterialPreferenceCategory
import com.yarolegovich.mp.MaterialSwitchPreference
import javax.inject.Inject

@Suppress("Detekt.TooManyFunctions")
class ViewThemeUtils @Inject constructor(private val theme: ServerTheme) {

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
        withElementColor(button) { color ->
            val disabledColor = ContextCompat.getColor(button.context, R.color.disabled_text)
            val colorStateList = ColorStateList(
                arrayOf(
                    intArrayOf(android.R.attr.state_enabled),
                    intArrayOf(-android.R.attr.state_enabled)
                ),
                intArrayOf(color, disabledColor)
            )
            button.setTextColor(colorStateList)
            button.iconTint = colorStateList
        }
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

    fun colorSwitchPreference(preference: MaterialSwitchPreference) {
        val children = preference.children
        val switch = children.find { it is SwitchCompat }
        if (switch != null) {
            val switchCompat = (switch as SwitchCompat)
            colorSwitchCompat(switchCompat)
        }
    }

    // TODO cleanup
    fun colorSwitchCompat(switchCompat: SwitchCompat) {
        withElementColor(switchCompat) { color ->

            val context = switchCompat.context

            val thumbUncheckedColor = ResourcesCompat.getColor(
                context.resources,
                R.color.switch_thumb_color_unchecked,
                context.theme
            )
            val trackUncheckedColor = ResourcesCompat.getColor(
                context.resources,
                R.color.switch_track_color_unchecked,
                context.theme
            )

            val trackColor = Color.argb(TRACK_ALPHA, Color.red(color), Color.green(color), Color.blue(color))
            switchCompat.thumbTintList = ColorStateList(
                arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()),
                intArrayOf(color, thumbUncheckedColor)
            )

            switchCompat.trackTintList = ColorStateList(
                arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()),
                intArrayOf(trackColor, trackUncheckedColor)
            )
        }
    }

    fun colorDrawable(context: Context, drawable: Drawable) {
        val color = getElementColor(context)
        drawable.setTint(color)
    }

    fun themeCheckbox(checkbox: CheckBox) {
        withElementColor(checkbox) { color ->
            checkbox.buttonTintList = ColorStateList(
                arrayOf(
                    intArrayOf(-android.R.attr.state_checked),
                    intArrayOf(android.R.attr.state_checked),
                ),
                intArrayOf(Color.GRAY, color)
            )
        }
    }

    fun themeRadioButton(radioButton: RadioButton) {
        withElementColor(radioButton) { color ->
            radioButton.buttonTintList = ColorStateList(
                arrayOf(
                    intArrayOf(-android.R.attr.state_checked),
                    intArrayOf(android.R.attr.state_checked),
                ),
                intArrayOf(Color.GRAY, color)
            )
        }
    }

    fun themeSwipeRefreshLayout(swipeRefreshLayout: SwipeRefreshLayout) {
        withElementColor(swipeRefreshLayout) { color ->
            swipeRefreshLayout.setColorSchemeColors(color)
            swipeRefreshLayout.setProgressBackgroundColorSchemeResource(R.color.refresh_spinner_background)
        }
    }

    fun colorProgressBar(progressIndicator: LinearProgressIndicator) {
        withElementColor(progressIndicator) { color ->
            progressIndicator.setIndicatorColor(progressColor(progressIndicator.context, color))
        }
    }

    fun colorEditText(editText: EditText) {
        withElementColor(editText) { color ->
            editText.setTextColor(color)
            // TODO check API-level compatibility
            // editText.background.setColorFilter(color, PorterDuff.Mode.SRC_ATOP)
            editText.backgroundTintList = ColorStateList(
                arrayOf(
                    intArrayOf(-android.R.attr.state_focused),
                    intArrayOf(android.R.attr.state_focused)
                ),
                intArrayOf(
                    Color.GRAY,
                    color
                )
            )
        }
    }

    fun colorTextInputLayout(textInputLayout: TextInputLayout) {
        withElementColor(textInputLayout) { color ->
            // TODO calculate error color based on primary color, dark/light aware
            val errorColor = Color.GRAY
            textInputLayout.boxStrokeColor = color
            textInputLayout.setErrorIconTintList(
                ColorStateList(
                    arrayOf(
                        intArrayOf(-android.R.attr.state_focused),
                        intArrayOf(android.R.attr.state_focused)
                    ),
                    intArrayOf(
                        errorColor,
                        errorColor
                    )
                )
            )
            textInputLayout.setErrorTextColor(
                ColorStateList(
                    arrayOf(
                        intArrayOf(-android.R.attr.state_focused),
                        intArrayOf(android.R.attr.state_focused)
                    ),
                    intArrayOf(
                        errorColor,
                        errorColor
                    )
                )
            )
            textInputLayout.boxStrokeErrorColor =
                ColorStateList(
                    arrayOf(
                        intArrayOf(-android.R.attr.state_focused),
                        intArrayOf(android.R.attr.state_focused)
                    ),
                    intArrayOf(
                        errorColor,
                        errorColor
                    )
                )
            textInputLayout.defaultHintTextColor =
                ColorStateList(
                    arrayOf(
                        intArrayOf(-android.R.attr.state_focused),
                        intArrayOf(android.R.attr.state_focused)
                    ),
                    intArrayOf(
                        Color.GRAY,
                        color
                    )
                )
        }
    }

    fun colorTabLayout(tabLayout: TabLayout) {
        withElementColor(tabLayout) { color ->
            tabLayout.setSelectedTabIndicatorColor(color)
        }
    }

    fun getPlaceholderImage(context: Context, mimetype: String?): Drawable? {
        val drawableResourceId = DrawableUtils.getDrawableResourceIdForMimeType(mimetype)
        val drawable = AppCompatResources.getDrawable(
            context,
            drawableResourceId
        )
        if (drawable != null && THEMEABLE_PLACEHOLDER_IDS.contains(drawableResourceId)) {
            colorDrawable(context, drawable)
        }
        return drawable
    }

    fun colorChipBackground(chip: Chip) {
        withElementColor(chip) { color ->
            chip.chipBackgroundColor = ColorStateList.valueOf(color)
            chip.setTextColor(theme.colorText)
        }
    }

    fun colorChipOutlined(chip: Chip, strokeWidth: Float) {
        withElementColor(chip) { color ->
            chip.chipBackgroundColor = ColorStateList.valueOf(Color.TRANSPARENT)
            chip.chipStrokeWidth = strokeWidth
            chip.chipStrokeColor = ColorStateList.valueOf(color)
            chip.setTextColor(color)
        }
    }

    private fun progressColor(context: Context, color: Int): Int {
        val hsl = FloatArray(HSL_SIZE)
        ColorUtils.RGBToHSL(Color.red(color), Color.green(color), Color.blue(color), hsl)

        if (isDarkMode(context)) {
            hsl[INDEX_LUMINATION] = LUMINATION_DARK_THEME
        } else {
            hsl[INDEX_LUMINATION] = LUMINATION_LIGHT_THEME
        }

        return ColorUtils.HSLToColor(hsl)
    }

    companion object {
        private val THEMEABLE_PLACEHOLDER_IDS = listOf(
            R.drawable.ic_mimetype_package_x_generic,
            R.drawable.ic_mimetype_folder
        )
        private const val TRACK_ALPHA: Int = 77
        private const val HSL_SIZE: Int = 3
        private const val INDEX_LUMINATION: Int = 2
        private const val LUMINATION_LIGHT_THEME: Float = 0.76f
        private const val LUMINATION_DARK_THEME: Float = 0.28f
    }
}
