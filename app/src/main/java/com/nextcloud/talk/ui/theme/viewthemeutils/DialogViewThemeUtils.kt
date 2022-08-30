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
import android.graphics.drawable.Drawable
import android.os.Build
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.graphics.drawable.DrawableCompat
import com.google.android.material.R
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.shape.MaterialShapeDrawable
import com.nextcloud.android.common.ui.theme.MaterialSchemes
import com.nextcloud.android.common.ui.theme.ViewThemeUtilsBase
import javax.inject.Inject

/**
 * View theme utils for dialogs
 */
class DialogViewThemeUtils @Inject constructor(schemes: MaterialSchemes) :
    ViewThemeUtilsBase(schemes) {

    fun colorMaterialAlertDialogBackground(context: Context, dialogBuilder: MaterialAlertDialogBuilder) {
        withScheme(dialogBuilder.context) { scheme ->
            val materialShapeDrawable = MaterialShapeDrawable(
                context,
                null,
                R.attr.alertDialogStyle,
                R.style.MaterialAlertDialog_MaterialComponents
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

    fun colorDialogMenuText(button: MaterialButton) {
        withScheme(button) { scheme ->
            button.setTextColor(scheme.onSurface)
            button.iconTint = ColorStateList.valueOf(scheme.onSurface)
        }
    }

    fun colorDialogHeadline(textView: TextView) {
        withScheme(textView) { scheme ->
            textView.setTextColor(scheme.onSurface)
        }
    }

    fun colorDialogSupportingText(textView: TextView) {
        withScheme(textView) { scheme ->
            textView.setTextColor(scheme.onSurfaceVariant)
        }
    }

    fun colorDialogIcon(icon: ImageView) {
        withScheme(icon) { scheme ->
            icon.setColorFilter(scheme.secondary)
        }
    }

    fun colorMaterialAlertDialogIcon(context: Context, drawableId: Int): Drawable {
        val drawable = AppCompatResources.getDrawable(context, drawableId)!!
        withScheme(context) { scheme ->
            DrawableCompat.setTint(drawable, scheme.secondary)
        }
        return drawable
    }
}
