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

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.PorterDuff
import android.os.Build
import androidx.core.content.ContextCompat
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.chip.Chip
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.google.android.material.shape.MaterialShapeDrawable
import com.google.android.material.tabs.TabLayout
import com.google.android.material.textfield.TextInputLayout
import com.google.android.material.textview.MaterialTextView
import com.nextcloud.android.common.ui.color.ColorUtil
import com.nextcloud.android.common.ui.theme.MaterialSchemes
import com.nextcloud.android.common.ui.theme.ViewThemeUtilsBase
import scheme.Scheme
import javax.inject.Inject

/**
 * View theme utils for Material views (com.google.android.material.*)
 */
@Suppress("TooManyFunctions")
class MaterialViewThemeUtils @Inject constructor(schemes: MaterialSchemes, private val colorUtil: ColorUtil) :
    ViewThemeUtilsBase(schemes) {
    fun colorToolbarOverflowIcon(toolbar: MaterialToolbar) {
        withScheme(toolbar) { scheme ->
            toolbar.overflowIcon?.setColorFilter(scheme.onSurface, PorterDuff.Mode.SRC_ATOP)
        }
    }

    fun themeSearchBarText(searchText: MaterialTextView) {
        withScheme(searchText) { scheme ->
            searchText.setHintTextColor(scheme.onSurfaceVariant)
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

    fun colorMaterialTextButton(button: MaterialButton) {
        withScheme(button) { scheme ->
            button.rippleColor = ColorStateList(
                arrayOf(
                    intArrayOf(android.R.attr.state_pressed)
                ),
                intArrayOf(
                    colorUtil.adjustOpacity(scheme.primary, SURFACE_OPACITY_BUTTON_DISABLED)
                )
            )
        }
    }

    fun colorMaterialButtonText(button: MaterialButton) {
        withScheme(button) { scheme ->
            val disabledColor = ContextCompat.getColor(button.context, com.nextcloud.talk.R.color.disabled_text)
            val colorStateList = ColorStateList(
                arrayOf(
                    intArrayOf(android.R.attr.state_enabled),
                    intArrayOf(-android.R.attr.state_enabled)
                ),
                intArrayOf(scheme.primary, disabledColor)
            )
            button.setTextColor(colorStateList)
            button.iconTint = colorStateList
        }
    }

    fun colorMaterialButtonPrimaryFilled(button: MaterialButton) {
        withScheme(button) { scheme ->
            button.backgroundTintList =
                ColorStateList(
                    arrayOf(
                        intArrayOf(android.R.attr.state_enabled),
                        intArrayOf(-android.R.attr.state_enabled)
                    ),
                    intArrayOf(
                        scheme.primary,
                        colorUtil.adjustOpacity(scheme.onSurface, SURFACE_OPACITY_BUTTON_DISABLED)
                    )
                )

            button.setTextColor(
                ColorStateList(
                    arrayOf(
                        intArrayOf(android.R.attr.state_enabled),
                        intArrayOf(-android.R.attr.state_enabled)
                    ),
                    intArrayOf(
                        scheme.onPrimary,
                        colorUtil.adjustOpacity(scheme.onSurface, ON_SURFACE_OPACITY_BUTTON_DISABLED)
                    )
                )
            )

            button.iconTint = ColorStateList(
                arrayOf(
                    intArrayOf(android.R.attr.state_enabled),
                    intArrayOf(-android.R.attr.state_enabled)
                ),
                intArrayOf(
                    scheme.onPrimary,
                    colorUtil.adjustOpacity(scheme.onSurface, ON_SURFACE_OPACITY_BUTTON_DISABLED)
                )
            )
        }
    }

    fun colorMaterialButtonPrimaryOutlined(button: MaterialButton) {
        withScheme(button) { scheme ->
            button.strokeColor = ColorStateList.valueOf(scheme.outline)
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
            button.iconTint = ColorStateList(
                arrayOf(
                    intArrayOf(android.R.attr.state_enabled),
                    intArrayOf(-android.R.attr.state_enabled)
                ),
                intArrayOf(
                    scheme.primary,
                    colorUtil.adjustOpacity(scheme.onSurface, ON_SURFACE_OPACITY_BUTTON_DISABLED)
                )
            )
        }
    }

    fun colorMaterialButtonPrimaryBorderless(button: MaterialButton) {
        withScheme(button) { scheme ->
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
            button.iconTint = ColorStateList(
                arrayOf(
                    intArrayOf(android.R.attr.state_enabled),
                    intArrayOf(-android.R.attr.state_enabled)
                ),
                intArrayOf(
                    scheme.primary,
                    colorUtil.adjustOpacity(scheme.onSurface, ON_SURFACE_OPACITY_BUTTON_DISABLED)
                )
            )
        }
    }

    fun themeToolbar(toolbar: MaterialToolbar) {
        withScheme(toolbar) { scheme ->
            toolbar.setBackgroundColor(scheme.surface)
            toolbar.setNavigationIconTint(scheme.onSurface)
            toolbar.setTitleTextColor(scheme.onSurface)
        }
    }

    fun colorCardViewBackground(card: MaterialCardView) {
        withScheme(card) { scheme ->
            card.setCardBackgroundColor(scheme.surfaceVariant)
        }
    }

    fun colorProgressBar(progressIndicator: LinearProgressIndicator) {
        withScheme(progressIndicator) { scheme ->
            progressIndicator.setIndicatorColor(scheme.primary)
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

    fun themeTabLayoutOnSurface(tabLayout: TabLayout) {
        withScheme(tabLayout) { scheme ->
            tabLayout.setBackgroundColor(scheme.surface)
            colorTabLayout(tabLayout, scheme)
        }
    }

    fun colorTabLayout(tabLayout: TabLayout, scheme: Scheme) {
        tabLayout.setSelectedTabIndicatorColor(scheme.primary)
        tabLayout.tabTextColors = ColorStateList(
            arrayOf(
                intArrayOf(android.R.attr.state_selected),
                intArrayOf(-android.R.attr.state_selected)
            ),
            intArrayOf(
                scheme.primary,
                ContextCompat.getColor(tabLayout.context, com.nextcloud.talk.R.color.high_emphasis_text)
            )
        )
        tabLayout.tabRippleColor = ColorStateList(
            arrayOf(
                intArrayOf(android.R.attr.state_pressed)
            ),
            intArrayOf(
                colorUtil.adjustOpacity(scheme.primary, SURFACE_OPACITY_BUTTON_DISABLED)
            )
        )
    }

    fun colorChipBackground(chip: Chip) {
        withScheme(chip) { scheme ->
            chip.chipBackgroundColor = ColorStateList.valueOf(scheme.primary)
            chip.setTextColor(scheme.onPrimary)
        }
    }

    fun colorChipOutlined(chip: Chip, strokeWidth: Float) {
        withScheme(chip) { scheme ->
            chip.chipBackgroundColor = ColorStateList.valueOf(Color.TRANSPARENT)
            chip.chipStrokeWidth = strokeWidth
            chip.chipStrokeColor = ColorStateList.valueOf(scheme.primary)
            chip.setTextColor(scheme.primary)
        }
    }

    fun colorMaterialAlertDialogBackground(context: Context, dialogBuilder: MaterialAlertDialogBuilder) {
        withScheme(dialogBuilder.context) { scheme ->
            val materialShapeDrawable = MaterialShapeDrawable(
                context,
                null,
                com.google.android.material.R.attr.alertDialogStyle,
                com.google.android.material.R.style.MaterialAlertDialog_MaterialComponents
            )
            materialShapeDrawable.initializeElevationOverlay(context)
            materialShapeDrawable.fillColor = ColorStateList.valueOf(scheme.surface)

            // dialogCornerRadius first appeared in Android Pie
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val radius = context.resources.getDimension(com.nextcloud.talk.R.dimen.dialogBorderRadius)
                materialShapeDrawable.setCornerSize(radius)
            }

            dialogBuilder.background = materialShapeDrawable
        }
    }

    companion object {
        private const val SURFACE_OPACITY_BUTTON_DISABLED: Float = 0.12f
        private const val ON_SURFACE_OPACITY_BUTTON_DISABLED: Float = 0.38f
    }
}
