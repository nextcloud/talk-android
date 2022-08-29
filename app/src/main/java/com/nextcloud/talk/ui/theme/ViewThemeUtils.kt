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
import android.graphics.drawable.Drawable
import android.text.Spannable
import android.text.SpannableString
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.graphics.drawable.DrawableCompat
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.google.android.material.button.MaterialButton
import com.nextcloud.android.common.ui.theme.MaterialSchemes
import com.nextcloud.android.common.ui.theme.ViewThemeUtilsBase
import com.nextcloud.talk.R
import com.nextcloud.talk.ui.theme.viewthemeutils.AndroidViewThemeUtils
import com.nextcloud.talk.ui.theme.viewthemeutils.AppCompatViewThemeUtils
import com.nextcloud.talk.ui.theme.viewthemeutils.MaterialViewThemeUtils
import com.nextcloud.talk.ui.theme.viewthemeutils.TalkSpecificViewThemeUtils
import com.nextcloud.talk.utils.DisplayUtils
import eu.davidea.flexibleadapter.utils.FlexibleUtils
import javax.inject.Inject

@Suppress("TooManyFunctions")
class ViewThemeUtils @Inject constructor(
    schemes: MaterialSchemes,
    @JvmField
    val platform: AndroidViewThemeUtils,
    @JvmField
    val material: MaterialViewThemeUtils,
    @JvmField
    val appcompat: AppCompatViewThemeUtils,
    @JvmField
    val talk: TalkSpecificViewThemeUtils
) : ViewThemeUtilsBase(schemes) {

    fun themeSwipeRefreshLayout(swipeRefreshLayout: SwipeRefreshLayout) {
        withScheme(swipeRefreshLayout) { scheme ->
            swipeRefreshLayout.setColorSchemeColors(scheme.primary)
            swipeRefreshLayout.setProgressBackgroundColorSchemeResource(R.color.refresh_spinner_background)
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

    fun highlightText(textView: TextView, originalText: String, constraint: String) {
        withScheme(textView) { scheme ->
            FlexibleUtils.highlightText(textView, originalText, constraint, scheme.primary)
        }
    }

    fun createHighlightedSpan(context: Context, messageSpannable: SpannableString, searchTerm: String): Spannable {
        var spannable: Spannable = messageSpannable
        withScheme(context) { scheme ->
            spannable = DisplayUtils.searchAndColor(messageSpannable, searchTerm, scheme.primary)
        }
        return spannable
    }

    fun colorMaterialAlertDialogIcon(context: Context, drawableId: Int): Drawable {
        val drawable = AppCompatResources.getDrawable(context, drawableId)!!
        withScheme(context) { scheme ->
            DrawableCompat.setTint(drawable, scheme.secondary)
        }
        return drawable
    }
}
