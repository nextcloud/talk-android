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

import android.app.Activity
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.PorterDuff
import android.graphics.drawable.Drawable
import android.view.View
import android.widget.CheckBox
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.RadioButton
import android.widget.SeekBar
import android.widget.TextView
import androidx.annotation.ColorInt
import androidx.appcompat.content.res.AppCompatResources
import androidx.appcompat.widget.SearchView
import androidx.appcompat.widget.SearchView.SearchAutoComplete
import androidx.appcompat.widget.SwitchCompat
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.core.view.children
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.chip.Chip
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.google.android.material.tabs.TabLayout
import com.google.android.material.textfield.TextInputLayout
import com.google.android.material.textview.MaterialTextView
import com.nextcloud.talk.R
import com.nextcloud.talk.utils.DisplayUtils
import com.nextcloud.talk.utils.DrawableUtils
import com.nextcloud.talk.utils.ui.ColorUtil
import com.nextcloud.talk.utils.ui.PlatformThemeUtil.isDarkMode
import com.yarolegovich.mp.MaterialPreferenceCategory
import com.yarolegovich.mp.MaterialSwitchPreference
import scheme.Scheme
import javax.inject.Inject

@Suppress("TooManyFunctions")
class ViewThemeUtils @Inject constructor(private val theme: ServerTheme, private val colorUtil: ColorUtil) {

    /**
     * Scheme for painting elements
     */
    fun getScheme(context: Context): Scheme = when {
        isDarkMode(context) -> theme.darkScheme
        else -> theme.lightScheme
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

    private fun withScheme(view: View, block: (Scheme) -> Unit) {
        block(getScheme(view.context))
    }

    fun themeToolbar(toolbar: MaterialToolbar) {
        withScheme(toolbar) { scheme ->
            toolbar.setBackgroundColor(scheme.surface)
            toolbar.setNavigationIconTint(scheme.onSurface)
            toolbar.setTitleTextColor(scheme.onSurface)
        }
    }

    fun themeSearchView(searchView: SearchView) {
        withScheme(searchView) { scheme ->
            // hacky as no default way is provided
            val editText = searchView.findViewById<SearchAutoComplete>(R.id.search_src_text)
            val searchPlate = searchView.findViewById<LinearLayout>(R.id.search_plate)
            editText.textSize = 16f
            editText.setHintTextColor(scheme.onSurfaceVariant)
            editText.setTextColor(scheme.onSurface)
            editText.setBackgroundColor(scheme.surface)
            searchPlate.setBackgroundColor(scheme.surface)
        }
    }

    fun themeSearchBarText(searchText: MaterialTextView) {
        withScheme(searchText) { scheme ->
            searchText.setHintTextColor(scheme.onSurfaceVariant)
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

    fun themeDialogDivider(view: View) {
        withScheme(view) { scheme ->
            view.setBackgroundColor(scheme.surfaceVariant)
        }
    }

    fun themeFAB(fab: FloatingActionButton) {
        withScheme(fab) { scheme ->
            fab.backgroundTintList = ColorStateList.valueOf(scheme.primaryContainer)
            fab.imageTintList = ColorStateList.valueOf(scheme.onPrimaryContainer)
        }
    }

    fun themeCardView(cardView: MaterialCardView) {
        withScheme(cardView) { scheme ->
            cardView.backgroundTintList = ColorStateList.valueOf(scheme.surface)
        }
    }

    fun themeHorizontalSeekBar(seekBar: SeekBar) {
        withElementColor(seekBar) { color ->
            themeHorizontalSeekBar(seekBar, color)
        }
    }

    fun themeHorizontalSeekBar(seekBar: SeekBar, @ColorInt color: Int) {
        themeHorizontalProgressBar(seekBar, color)
        seekBar.thumb.setColorFilter(color, PorterDuff.Mode.SRC_IN)
    }

    fun themeHorizontalProgressBar(progressBar: ProgressBar?, @ColorInt color: Int) {
        if (progressBar != null) {
            progressBar.indeterminateDrawable.setColorFilter(color, PorterDuff.Mode.SRC_IN)
            progressBar.progressDrawable.setColorFilter(color, PorterDuff.Mode.SRC_IN)
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

            val disabledColor = ContextCompat.getColor(button.context, R.color.disabled_text)
            val colorStateList = ColorStateList(
                arrayOf(
                    intArrayOf(android.R.attr.state_enabled),
                    intArrayOf(-android.R.attr.state_enabled)
                ),
                intArrayOf(theme.colorText, disabledColor)
            )

            button.setTextColor(colorStateList)
            button.iconTint = colorStateList
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

            val trackColor =
                Color.argb(SWITCHCOMPAT_TRACK_ALPHA, Color.red(color), Color.green(color), Color.blue(color))
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
                    intArrayOf(android.R.attr.state_checked)
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
                    intArrayOf(android.R.attr.state_checked)
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

    private fun progressColor(context: Context, color: Int): Int {
        val lightness = when (isDarkMode(context)) {
            true -> PROGRESS_LIGHTNESS_DARK_THEME
            false -> PROGRESS_LIGHTNESS_LIGHT_THEME
        }
        return colorUtil.setLightness(color, lightness)
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

    fun colorTextInputLayout(textInputLayout: TextInputLayout) {
        withScheme(textInputLayout) { scheme ->
            val errorColor = scheme.onSurfaceVariant

            val errorColorStateList = ColorStateList(
                arrayOf(
                    intArrayOf(-android.R.attr.state_focused),
                    intArrayOf(android.R.attr.state_focused)
                ),
                intArrayOf(
                    errorColor,
                    errorColor
                )
            )
            val coloredColorStateList = ColorStateList(
                arrayOf(
                    intArrayOf(-android.R.attr.state_focused),
                    intArrayOf(android.R.attr.state_focused)
                ),
                intArrayOf(
                    scheme.outline,
                    scheme.primary
                )
            )

            textInputLayout.setBoxStrokeColorStateList(coloredColorStateList)
            textInputLayout.setErrorIconTintList(errorColorStateList)
            textInputLayout.setErrorTextColor(errorColorStateList)
            textInputLayout.boxStrokeErrorColor = errorColorStateList
            textInputLayout.defaultHintTextColor = coloredColorStateList
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

    companion object {
        private val THEMEABLE_PLACEHOLDER_IDS = listOf(
            R.drawable.ic_mimetype_package_x_generic,
            R.drawable.ic_mimetype_folder
        )
        private const val SWITCHCOMPAT_TRACK_ALPHA: Int = 77
        private const val PROGRESS_LIGHTNESS_LIGHT_THEME: Float = 0.76f
        private const val PROGRESS_LIGHTNESS_DARK_THEME: Float = 0.28f
    }
}
