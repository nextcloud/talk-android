/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2022 Álvaro Brey <alvaro@alvarobrey.com>
 * SPDX-FileCopyrightText: 2022 Nextcloud GmbH
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.ui.theme

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.LayerDrawable
import android.text.Spannable
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.RelativeLayout
import android.widget.TextView
import androidx.annotation.ColorRes
import androidx.annotation.DrawableRes
import androidx.appcompat.content.res.AppCompatResources
import androidx.appcompat.widget.SearchView
import androidx.appcompat.widget.SwitchCompat
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.ColorUtils
import androidx.core.graphics.drawable.DrawableCompat
import androidx.core.view.ViewCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.chip.Chip
import com.google.android.material.materialswitch.MaterialSwitch
import com.nextcloud.android.common.ui.theme.MaterialSchemes
import com.nextcloud.android.common.ui.theme.ViewThemeUtilsBase
import com.nextcloud.android.common.ui.theme.utils.AndroidXViewThemeUtils
import com.nextcloud.android.common.ui.util.buildColorStateList
import com.nextcloud.talk.R
import com.nextcloud.talk.chat.data.model.ChatMessage
import com.nextcloud.talk.databinding.ReactionsInsideMessageBinding
import com.nextcloud.talk.ui.MicInputCloud
import com.nextcloud.talk.ui.StatusDrawable
import com.nextcloud.talk.ui.WaveformSeekBar
import com.nextcloud.talk.utils.DisplayUtils
import com.nextcloud.talk.utils.DrawableUtils
import com.nextcloud.talk.utils.message.MessageUtils
import com.vanniktech.emoji.EmojiTextView
import com.wooplr.spotlight.SpotlightView
import dynamiccolor.DynamicScheme
import dynamiccolor.MaterialDynamicColors
import eu.davidea.flexibleadapter.utils.FlexibleUtils
import javax.inject.Inject
import kotlin.math.roundToInt

/**
 * View theme utils specific for the Talk app.
 *
 */
@Suppress("TooManyFunctions")
class TalkSpecificViewThemeUtils @Inject constructor(
    schemes: MaterialSchemes,
    private val appcompat: AndroidXViewThemeUtils
) : ViewThemeUtilsBase(schemes) {
    private val dynamicColor = MaterialDynamicColors()
    fun themeIncomingMessageBubble(bubble: View, grouped: Boolean, deleted: Boolean, isPlayed: Boolean = false) {
        val resources = bubble.resources

        var bubbleResource = R.drawable.shape_incoming_message

        if (grouped) {
            bubbleResource = R.drawable.shape_grouped_incoming_message
        }

        val bgBubbleColor = if (deleted) {
            resources.getColor(R.color.bg_message_list_incoming_bubble_deleted, null)
        } else if (isPlayed) {
            resources.getColor(R.color.bg_message_list_incoming_bubble_audio_played, null)
        } else {
            resources.getColor(R.color.bg_message_list_incoming_bubble, null)
        }
        val bubbleDrawable = DisplayUtils.getMessageSelector(
            bgBubbleColor,
            resources.getColor(R.color.transparent, null),
            bgBubbleColor,
            bubbleResource
        )
        ViewCompat.setBackground(bubble, bubbleDrawable)
    }

    fun themeOutgoingMessageBubble(bubble: View, grouped: Boolean, deleted: Boolean, isPlayed: Boolean = false) {
        withScheme(bubble) { scheme ->
            val bgBubbleColor = if (deleted) {
                ColorUtils.setAlphaComponent(dynamicColor.surfaceVariant().getArgb(scheme), HALF_ALPHA_INT)
            } else if (isPlayed) {
                ContextCompat.getColor(bubble.context, R.color.bg_message_list_outgoing_bubble_audio_played)
            } else {
                dynamicColor.surfaceVariant().getArgb(scheme)
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
            textView.setTextColor(dynamicColor.onSurfaceVariant().getArgb(scheme))
        }
    }

    fun colorOutgoingQuoteAuthorText(textView: TextView) {
        withScheme(textView) { scheme ->
            ColorUtils.setAlphaComponent(dynamicColor.onSurfaceVariant().getArgb(scheme), ALPHA_80_INT)
        }
    }

    fun colorOutgoingQuoteBackground(view: View) {
        withScheme(view) { scheme ->
            view.setBackgroundColor(dynamicColor.onSurfaceVariant().getArgb(scheme))
        }
    }

    fun colorContactChatItemName(contactName: androidx.emoji2.widget.EmojiTextView) {
        withScheme(contactName) { scheme ->
            contactName.setTextColor(dynamicColor.onPrimaryContainer().getArgb(scheme))
        }
    }

    fun colorContactChatItemBackground(card: MaterialCardView) {
        withScheme(card) { scheme ->
            card.setCardBackgroundColor(dynamicColor.primaryContainer().getArgb(scheme))
        }
    }

    fun colorSwitch(preference: MaterialSwitch) {
        val switch = preference as SwitchCompat
        appcompat.colorSwitchCompat(switch)
    }

    fun setCheckedBackground(emoji: EmojiTextView) {
        withScheme(emoji) { scheme ->
            val drawable = AppCompatResources
                .getDrawable(emoji.context, R.drawable.reaction_self_bottom_sheet_background)!!
                .mutate()
            DrawableCompat.setTintList(
                drawable,
                ColorStateList.valueOf(dynamicColor.primary().getArgb(scheme))
            )
            emoji.background = drawable
        }
    }

    fun setCheckedBackground(linearLayout: LinearLayout, outgoing: Boolean, isBubbled: Boolean) {
        withScheme(linearLayout) { scheme ->
            val drawable = AppCompatResources
                .getDrawable(linearLayout.context, R.drawable.reaction_self_background)!!
                .mutate()
            val backgroundColor = if (outgoing && isBubbled) {
                ContextCompat.getColor(
                    linearLayout.context,
                    R.color.bg_message_list_incoming_bubble
                )
            } else {
                dynamicColor.primaryContainer().getArgb(scheme)
            }
            DrawableCompat.setTintList(
                drawable,
                ColorStateList.valueOf(backgroundColor)
            )
            linearLayout.background = drawable
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

    private fun colorDrawable(context: Context, drawable: Drawable) {
        withScheme(context) { scheme ->
            drawable.setTint(dynamicColor.primary().getArgb(scheme))
        }
    }

    fun themePlaceholderAvatar(avatar: View, @DrawableRes foreground: Int): Drawable? {
        var drawable: LayerDrawable? = null
        withScheme(avatar) { scheme ->
            val layers = arrayOfNulls<Drawable>(2)
            layers[0] = ContextCompat.getDrawable(avatar.context, R.drawable.ic_avatar_background)
            layers[0]?.setTint(dynamicColor.surfaceVariant().getArgb(scheme))
            layers[1] = ContextCompat.getDrawable(avatar.context, foreground)
            layers[1]?.setTint(dynamicColor.onSurfaceVariant().getArgb(scheme))
            drawable = LayerDrawable(layers)
        }

        return drawable
    }

    @SuppressLint("RestrictedApi")
    fun themeSearchView(searchView: SearchView) {
        withScheme(searchView) { scheme ->
            // hacky as no default way is provided
            val editText = searchView.findViewById<SearchView.SearchAutoComplete>(R.id.search_src_text)
            val searchPlate = searchView.findViewById<LinearLayout>(R.id.search_plate)
            editText.setHintTextColor(dynamicColor.onSurfaceVariant().getArgb(scheme))
            editText.setTextColor(dynamicColor.onSurface().getArgb(scheme))
            editText.setBackgroundColor(dynamicColor.surface().getArgb(scheme))
            searchPlate.setBackgroundColor(dynamicColor.surface().getArgb(scheme))
        }
    }

    fun themeStatusCardView(cardView: MaterialCardView) {
        withScheme(cardView) { scheme ->
            cardView.backgroundTintList =
                ColorStateList(
                    arrayOf(
                        intArrayOf(android.R.attr.state_checked),
                        intArrayOf(-android.R.attr.state_checked)
                    ),
                    intArrayOf(
                        dynamicColor.secondaryContainer().getArgb(scheme),
                        dynamicColor.surfaceContainerHigh().getArgb(scheme)
                    )
                )
            cardView.setStrokeColor(
                ColorStateList(
                    arrayOf(
                        intArrayOf(android.R.attr.state_checked),
                        intArrayOf(-android.R.attr.state_checked)
                    ),
                    intArrayOf(
                        dynamicColor.onSecondaryContainer().getArgb(scheme),
                        dynamicColor.surface().getArgb(scheme)
                    )
                )
            )
        }
    }

    fun themeMicInputCloud(micInputCloud: MicInputCloud) {
        withScheme(micInputCloud) { scheme ->
            micInputCloud.setColor(dynamicColor.primary().getArgb(scheme))
        }
    }

    fun themeWaveFormSeekBar(waveformSeekBar: WaveformSeekBar) {
        withScheme(waveformSeekBar) { scheme ->
            waveformSeekBar.thumb.colorFilter =
                PorterDuffColorFilter(dynamicColor.inversePrimary().getArgb(scheme), PorterDuff.Mode.SRC_IN)
            waveformSeekBar.setColors(
                dynamicColor.inversePrimary().getArgb(scheme),
                dynamicColor.onPrimaryContainer().getArgb(scheme)
            )
            waveformSeekBar.progressDrawable?.colorFilter =
                PorterDuffColorFilter(dynamicColor.primary().getArgb(scheme), PorterDuff.Mode.SRC_IN)
        }
    }

    fun themeForegroundColorSpan(context: Context): ForegroundColorSpan {
        return withScheme(context) { scheme ->
            return@withScheme ForegroundColorSpan(dynamicColor.primary().getArgb(scheme))
        }
    }

    fun themeSpotlightView(context: Context, builder: SpotlightView.Builder): SpotlightView.Builder {
        return withScheme(context) { scheme ->
            return@withScheme builder.headingTvColor(dynamicColor.primary().getArgb(scheme))
                .lineAndArcColor(dynamicColor.primary().getArgb(scheme))
        }
    }

    fun themeAndHighlightText(textView: TextView, originalText: String?, c: String?) {
        withScheme(textView) { scheme ->
            var constraint = c
            constraint = FlexibleUtils.toLowerCase(constraint)
            var start = FlexibleUtils.toLowerCase(originalText).indexOf(constraint)
            if (start != -1) {
                val spanText = Spannable.Factory.getInstance().newSpannable(originalText)
                do {
                    val end = start + constraint.length
                    spanText.setSpan(
                        ForegroundColorSpan(dynamicColor.primary().getArgb(scheme)),
                        start,
                        end,
                        Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                    spanText.setSpan(StyleSpan(Typeface.BOLD), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                    start = FlexibleUtils.toLowerCase(originalText)
                        .indexOf(constraint, end + 1) // +1 skips the consecutive span
                } while (start != -1)
                textView.setText(spanText, TextView.BufferType.SPANNABLE)
            } else {
                textView.setText(originalText, TextView.BufferType.NORMAL)
            }
        }
    }

    fun themeSortButton(sortButton: MaterialButton) {
        withScheme(sortButton) { scheme ->
            sortButton.iconTint = ColorStateList.valueOf(dynamicColor.onSurface().getArgb(scheme))
            sortButton.setTextColor(dynamicColor.onSurface().getArgb(scheme))
        }
    }

    fun themePathNavigationButton(navigationBtn: MaterialButton) {
        withScheme(navigationBtn) { scheme ->
            navigationBtn.iconTint = ColorStateList.valueOf(dynamicColor.onSurface().getArgb(scheme))
            navigationBtn.setTextColor(dynamicColor.onSurface().getArgb(scheme))
        }
    }

    fun themeSortListButtonGroup(relativeLayout: RelativeLayout) {
        withScheme(relativeLayout) { scheme ->
            relativeLayout.setBackgroundColor(dynamicColor.surface().getArgb(scheme))
        }
    }

    fun themeStatusDrawable(context: Context, statusDrawable: StatusDrawable) {
        withScheme(context) { scheme ->
            statusDrawable.colorStatusDrawable(dynamicColor.surface().getArgb(scheme))
        }
    }

    fun themeMessageCheckMark(imageView: ImageView) {
        withScheme(imageView) { scheme ->
            imageView.setColorFilter(
                dynamicColor.onSurfaceVariant().getArgb(scheme),
                PorterDuff.Mode.SRC_ATOP
            )
        }
    }

    fun themeMarkdown(context: Context, message: String, incoming: Boolean): Spanned {
        return withScheme(context) { scheme ->
            return@withScheme if (incoming) {
                MessageUtils(context).getRenderedMarkdownText(
                    context,
                    message,
                    context.getColor(R.color.nc_incoming_text_default)
                )
            } else {
                MessageUtils(context).getRenderedMarkdownText(
                    context,
                    message,
                    dynamicColor.onSurfaceVariant().getArgb(scheme)
                )
            }
        }
    }

    fun themeParentMessage(
        parentChatMessage: ChatMessage,
        message: ChatMessage,
        quoteColoredView: View,
        @ColorRes quoteColorNonSelf: Int = R.color.textColorMaxContrast
    ) {
        withScheme(quoteColoredView) { scheme ->
            if (parentChatMessage.actorId?.equals(message.activeUser!!.userId) == true) {
                quoteColoredView.setBackgroundColor(dynamicColor.primary().getArgb(scheme))
            } else {
                quoteColoredView.setBackgroundResource(quoteColorNonSelf)
            }
        }
    }

    fun getTextColor(isOutgoingMessage: Boolean, isSelfReaction: Boolean, binding: ReactionsInsideMessageBinding): Int {
        return withScheme(binding.root) { scheme ->
            return@withScheme if (!isOutgoingMessage || isSelfReaction) {
                ContextCompat.getColor(binding.root.context, R.color.high_emphasis_text)
            } else {
                dynamicColor.onSurfaceVariant().getArgb(scheme)
            }
        }
    }

    private fun chipOutlineFilterColorList(scheme: DynamicScheme) =
        buildColorStateList(
            android.R.attr.state_checked to dynamicColor.secondaryContainer().getArgb(scheme),
            -android.R.attr.state_checked to dynamicColor.outline().getArgb(scheme)
        )

    fun themeChipFilter(chip: Chip) {
        withScheme(chip.context) { scheme ->
            val backgroundColors =
                buildColorStateList(
                    android.R.attr.state_checked to dynamicColor.secondaryContainer().getArgb(scheme),
                    -android.R.attr.state_checked to dynamicColor.surface().getArgb(scheme),
                    android.R.attr.state_focused to dynamicColor.secondaryContainer().getArgb(scheme),
                    android.R.attr.state_hovered to dynamicColor.secondaryContainer().getArgb(scheme),
                    android.R.attr.state_pressed to dynamicColor.secondaryContainer().getArgb(scheme)
                )

            val iconColors =
                buildColorStateList(
                    android.R.attr.state_checked to dynamicColor.onSecondaryContainer().getArgb(scheme),
                    -android.R.attr.state_checked to dynamicColor.surfaceVariant().getArgb(scheme),
                    android.R.attr.state_focused to dynamicColor.onSecondaryContainer().getArgb(scheme),
                    android.R.attr.state_hovered to dynamicColor.onSecondaryContainer().getArgb(scheme),
                    android.R.attr.state_pressed to dynamicColor.onSecondaryContainer().getArgb(scheme)
                )

            val textColors =
                buildColorStateList(
                    android.R.attr.state_checked to dynamicColor.onSecondaryContainer().getArgb(scheme),
                    -android.R.attr.state_checked to dynamicColor.onSecondaryContainer().getArgb(scheme),
                    android.R.attr.state_hovered to dynamicColor.onSecondaryContainer().getArgb(scheme),
                    android.R.attr.state_focused to dynamicColor.onSecondaryContainer().getArgb(scheme),
                    android.R.attr.state_pressed to dynamicColor.onSecondaryContainer().getArgb(scheme)
                )

            chip.chipBackgroundColor = backgroundColors
            chip.chipStrokeColor = chipOutlineFilterColorList(scheme)
            chip.setTextColor(textColors)
            chip.checkedIconTint = iconColors
        }
    }

    companion object {
        private val THEMEABLE_PLACEHOLDER_IDS = listOf(
            R.drawable.ic_mimetype_package_x_generic,
            R.drawable.ic_mimetype_folder
        )

        private val ALPHA_80_INT: Int = (255 * 0.8).roundToInt()

        private const val HALF_ALPHA_INT: Int = 255 / 2
    }
}
