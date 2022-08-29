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

import android.annotation.TargetApi
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.PorterDuff
import android.graphics.drawable.Drawable
import android.graphics.drawable.LayerDrawable
import android.os.Build
import android.text.Spannable
import android.text.SpannableString
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.annotation.ColorInt
import androidx.annotation.DrawableRes
import androidx.appcompat.content.res.AppCompatResources
import androidx.appcompat.widget.SearchView
import androidx.appcompat.widget.SwitchCompat
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.ColorUtils
import androidx.core.graphics.drawable.DrawableCompat
import androidx.core.view.ViewCompat
import androidx.core.view.children
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipDrawable
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
import com.nextcloud.talk.R
import com.nextcloud.talk.ui.theme.viewthemeutils.AndroidViewThemeUtils
import com.nextcloud.talk.utils.DisplayUtils
import com.nextcloud.talk.utils.DrawableUtils
import com.vanniktech.emoji.EmojiTextView
import com.yarolegovich.mp.MaterialPreferenceCategory
import com.yarolegovich.mp.MaterialSwitchPreference
import eu.davidea.flexibleadapter.utils.FlexibleUtils
import scheme.Scheme
import javax.inject.Inject
import kotlin.math.roundToInt

@Suppress("TooManyFunctions")
class ViewThemeUtils @Inject constructor(
    schemes: MaterialSchemes,
    private val colorUtil: ColorUtil,
    @JvmField
    val androidViewThemeUtils: AndroidViewThemeUtils
) :
    ViewThemeUtilsBase(schemes) {

    fun colorToolbarOverflowIcon(toolbar: MaterialToolbar) {
        withScheme(toolbar) { scheme ->
            toolbar.overflowIcon?.setColorFilter(scheme.onSurface, PorterDuff.Mode.SRC_ATOP)
        }
    }

    fun themeSearchView(searchView: SearchView) {
        withScheme(searchView) { scheme ->
            // hacky as no default way is provided
            val editText = searchView.findViewById<SearchView.SearchAutoComplete>(R.id.search_src_text)
            val searchPlate = searchView.findViewById<LinearLayout>(R.id.search_plate)
            editText.textSize = SEARCH_TEXT_SIZE
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
            val disabledColor = ContextCompat.getColor(button.context, R.color.disabled_text)
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

    fun themeIncomingMessageBubble(bubble: ViewGroup, grouped: Boolean, deleted: Boolean) {
        val resources = bubble.resources

        var bubbleResource = R.drawable.shape_incoming_message

        if (grouped) {
            bubbleResource = R.drawable.shape_grouped_incoming_message
        }

        val bgBubbleColor = if (deleted) {
            resources.getColor(R.color.bg_message_list_incoming_bubble_deleted)
        } else {
            resources.getColor(R.color.bg_message_list_incoming_bubble)
        }
        val bubbleDrawable = DisplayUtils.getMessageSelector(
            bgBubbleColor,
            resources.getColor(R.color.transparent),
            bgBubbleColor,
            bubbleResource
        )
        ViewCompat.setBackground(bubble, bubbleDrawable)
    }

    fun themeToolbar(toolbar: MaterialToolbar) {
        withScheme(toolbar) { scheme ->
            toolbar.setBackgroundColor(scheme.surface)
            toolbar.setNavigationIconTint(scheme.onSurface)
            toolbar.setTitleTextColor(scheme.onSurface)
        }
    }

    fun themeOutgoingMessageBubble(bubble: ViewGroup, grouped: Boolean, deleted: Boolean) {
        withScheme(bubble) { scheme ->
            val bgBubbleColor = if (deleted) {
                ColorUtils.setAlphaComponent(scheme.surfaceVariant, HALF_ALPHA_INT)
            } else {
                scheme.surfaceVariant
            }

            val layout = if (grouped) {
                R.drawable.shape_grouped_outcoming_message
            } else {
                R.drawable.shape_outcoming_message
            }
            val bubbleDrawable = DisplayUtils.getMessageSelector(
                bgBubbleColor,
                ResourcesCompat.getColor(bubble.resources, R.color.transparent, null),
                bgBubbleColor,
                layout
            )
            ViewCompat.setBackground(bubble, bubbleDrawable)
        }
    }

    fun colorOutgoingQuoteText(textView: TextView) {
        withScheme(textView) { scheme ->
            textView.setTextColor(scheme.onSurfaceVariant)
        }
    }

    fun colorOutgoingQuoteAuthorText(textView: TextView) {
        withScheme(textView) { scheme ->
            ColorUtils.setAlphaComponent(scheme.onSurfaceVariant, ALPHA_80_INT)
        }
    }

    fun colorOutgoingQuoteBackground(view: View) {
        withScheme(view) { scheme ->
            view.setBackgroundColor(scheme.onSurfaceVariant)
        }
    }

    fun colorCardViewBackground(card: MaterialCardView) {
        withScheme(card) { scheme ->
            card.setCardBackgroundColor(scheme.surfaceVariant)
        }
    }

    fun colorContactChatItemName(contactName: androidx.emoji.widget.EmojiTextView) {
        withScheme(contactName) { scheme ->
            contactName.setTextColor(scheme.onPrimaryContainer)
        }
    }

    fun colorContactChatItemBackground(card: MaterialCardView) {
        withScheme(card) { scheme ->
            card.setCardBackgroundColor(scheme.primaryContainer)
        }
    }

    // TODO split this util into classes depending on framework views vs library views
    fun colorPreferenceCategory(category: MaterialPreferenceCategory) {
        withScheme(category) { scheme ->
            category.setTitleColor(scheme.primary)
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
        withScheme(switchCompat) { scheme ->

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

            val trackColor = Color.argb(
                SWITCH_COMPAT_TRACK_ALPHA,
                Color.red(scheme.primary),
                Color.green(scheme.primary),
                Color.blue(scheme.primary)
            )

            switchCompat.thumbTintList = ColorStateList(
                arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()),
                intArrayOf(scheme.primary, thumbUncheckedColor)
            )

            switchCompat.trackTintList = ColorStateList(
                arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()),
                intArrayOf(trackColor, trackUncheckedColor)
            )
        }
    }

    fun themeSwipeRefreshLayout(swipeRefreshLayout: SwipeRefreshLayout) {
        withScheme(swipeRefreshLayout) { scheme ->
            swipeRefreshLayout.setColorSchemeColors(scheme.primary)
            swipeRefreshLayout.setProgressBackgroundColorSchemeResource(R.color.refresh_spinner_background)
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
            intArrayOf(scheme.primary, ContextCompat.getColor(tabLayout.context, R.color.high_emphasis_text))
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

    private fun colorDrawable(context: Context, drawable: Drawable) {
        val scheme = getScheme(context)
        drawable.setTint(scheme.primary)
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

    @TargetApi(Build.VERSION_CODES.O)
    fun themePlaceholderAvatar(avatar: View, @DrawableRes foreground: Int): Drawable? {
        var drawable: LayerDrawable? = null
        withScheme(avatar) { scheme ->
            val layers = arrayOfNulls<Drawable>(2)
            layers[0] = ContextCompat.getDrawable(avatar.context, R.drawable.ic_avatar_background)
            layers[0]?.setTint(scheme.surfaceVariant)
            layers[1] = ContextCompat.getDrawable(avatar.context, foreground)
            layers[1]?.setTint(scheme.onSurfaceVariant)
            drawable = LayerDrawable(layers)
        }

        return drawable
    }

    fun themePrimaryMentionChip(context: Context, chip: ChipDrawable) {
        withScheme(context) { scheme ->
            chip.chipBackgroundColor = ColorStateList.valueOf(scheme.primary)
            chip.setTextColor(scheme.onPrimary)
        }
    }

    fun setCheckedBackground(emoji: EmojiTextView) {
        withScheme(emoji) { scheme ->
            val drawable = AppCompatResources
                .getDrawable(emoji.context, R.drawable.reaction_self_bottom_sheet_background)!!
                .mutate()
            DrawableCompat.setTintList(
                drawable,
                ColorStateList.valueOf(scheme.primary)
            )
            emoji.background = drawable
        }
    }

    fun setCheckedBackground(linearLayout: LinearLayout, @ColorInt backgroundColor: Int) {
        withScheme(linearLayout) { scheme ->
            val drawable = AppCompatResources
                .getDrawable(linearLayout.context, R.drawable.reaction_self_background)!!
                .mutate()
            DrawableCompat.setTintList(
                drawable,
                ColorStateList.valueOf(backgroundColor)
            )
            linearLayout.background = drawable
        }
    }

    fun colorDialogMenuText(button: MaterialButton) {
        withScheme(button) { scheme ->
            button.setTextColor(scheme.onSurface)
            button.iconTint = ColorStateList.valueOf(scheme.onSurface)
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
                val radius = context.resources.getDimension(R.dimen.dialogBorderRadius)
                materialShapeDrawable.setCornerSize(radius)
            }

            dialogBuilder.background = materialShapeDrawable
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

    companion object {
        private val THEMEABLE_PLACEHOLDER_IDS = listOf(
            R.drawable.ic_mimetype_package_x_generic,
            R.drawable.ic_mimetype_folder
        )

        private val ALPHA_80_INT: Int = (255 * 0.8).roundToInt()

        private const val SWITCH_COMPAT_TRACK_ALPHA: Int = 77
        private const val HALF_ALPHA_INT: Int = 255 / 2
        private const val SURFACE_OPACITY_BUTTON_DISABLED: Float = 0.12f
        private const val ON_SURFACE_OPACITY_BUTTON_DISABLED: Float = 0.38f
        private const val SEARCH_TEXT_SIZE: Float = 16f
    }
}
