/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2024 Marcel Hibbe <dev@mhibbe.de>
 * SPDX-FileCopyrightText: 2022 Tim Krüger <t@timkrueger.me>
 * SPDX-FileCopyrightText: 2021 Andy Scherzinger <info@andy-scherzinger.de>
 * SPDX-FileCopyrightText: 2017-2020 Mario Danic <mario@lovelyhq.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.utils

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.text.Spannable
import android.text.SpannableString
import android.text.Spanned
import android.text.TextPaint
import android.text.TextUtils
import android.text.format.DateUtils
import android.text.method.LinkMovementMethod
import android.text.style.AbsoluteSizeSpan
import android.text.style.ClickableSpan
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.util.TypedValue
import android.view.View
import android.view.Window
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import androidx.annotation.ColorInt
import androidx.annotation.ColorRes
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.annotation.XmlRes
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.ColorUtils
import androidx.core.graphics.createBitmap
import androidx.core.graphics.drawable.DrawableCompat
import androidx.core.net.toUri
import androidx.emoji2.text.EmojiCompat
import coil.Coil.imageLoader
import coil.request.ImageRequest
import coil.target.Target
import coil.transform.CircleCropTransformation
import com.google.android.material.chip.ChipDrawable
import com.nextcloud.talk.PhoneUtils.isPhoneNumber
import com.nextcloud.talk.R
import com.nextcloud.talk.application.NextcloudTalkApplication.Companion.sharedApplication
import com.nextcloud.talk.data.user.model.User
import com.nextcloud.talk.events.UserMentionClickEvent
import com.nextcloud.talk.extensions.loadUserAvatar
import com.nextcloud.talk.ui.theme.ViewThemeUtils
import com.nextcloud.talk.utils.ApiUtils.getUrlForAvatar
import com.nextcloud.talk.utils.ApiUtils.getUrlForFederatedAvatar
import com.nextcloud.talk.utils.ApiUtils.getUrlForGuestAvatar
import com.nextcloud.talk.utils.text.Spans.MentionChipSpan
import org.greenrobot.eventbus.EventBus
import third.parties.fresco.BetterImageSpan
import java.text.DateFormat
import java.util.Date
import java.util.regex.Pattern

object DisplayUtils {
    private val TAG = DisplayUtils::class.java.getSimpleName()
    private const val INDEX_LUMINATION = 2
    private const val HSL_SIZE = 3
    private const val MAX_LIGHTNESS = 0.92
    private const val TWITTER_HANDLE_PREFIX = "@"
    private const val HTTP_PROTOCOL = "http://"
    private const val HTTPS_PROTOCOL = "https://"
    private const val HTTP_MIN_LENGTH: Int = 7
    private const val HTTPS_MIN_LENGTH: Int = 7
    private const val DATE_TIME_PARTS_SIZE = 2
    private const val ONE_MINUTE_IN_MILLIS: Int = 60000
    private const val ROUND_UP_BUMP: Float = 0.5f
    fun isDarkModeOn(context: Context): Boolean {
        val currentNightMode = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        return currentNightMode == Configuration.UI_MODE_NIGHT_YES
    }

    fun setClickableString(string: String, url: String, textView: TextView) {
        val spannableString = SpannableString(string)
        spannableString.setSpan(
            object : ClickableSpan() {
                override fun onClick(widget: View) {
                    val browserIntent = Intent(Intent.ACTION_VIEW, url.toUri())
                    browserIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    sharedApplication!!.applicationContext.startActivity(browserIntent)
                }

                override fun updateDrawState(ds: TextPaint) {
                    super.updateDrawState(ds)
                    ds.isUnderlineText = false
                }
            },
            0,
            string.length,
            Spanned.SPAN_INCLUSIVE_EXCLUSIVE
        )
        textView.text = spannableString
        textView.movementMethod = LinkMovementMethod.getInstance()
    }

    fun getBitmap(drawable: Drawable): Bitmap {
        val bitmap = createBitmap(drawable.intrinsicWidth, drawable.intrinsicHeight)
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, canvas.width, canvas.height)
        drawable.draw(canvas)
        return bitmap
    }

    @JvmStatic
    fun convertDpToPixel(dp: Float, context: Context): Float =
        Math.round(
            TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                dp,
                context.resources.displayMetrics
            ) + ROUND_UP_BUMP
        ).toFloat()

    fun convertPixelToDp(px: Float, context: Context): Float = px / context.resources.displayMetrics.density

    fun getTintedDrawable(res: Resources, @DrawableRes drawableResId: Int, @ColorRes colorResId: Int): Drawable? {
        val drawable = ResourcesCompat.getDrawable(res, drawableResId, null)
        val color = res.getColor(colorResId)
        drawable?.setTint(color)
        return drawable
    }

    @JvmStatic
    fun getDrawableForMentionChipSpan(
        context: Context,
        id: String?,
        roomToken: String?,
        label: CharSequence,
        conversationUser: User,
        type: String,
        @XmlRes chipResource: Int,
        emojiEditText: EditText?,
        viewThemeUtils: ViewThemeUtils,
        isFederated: Boolean
    ): Drawable {
        val chip = ChipDrawable.createFromResource(context, chipResource)
        chip.text = EmojiCompat.get().process(label)
        chip.ellipsize = TextUtils.TruncateAt.MIDDLE
        if (chipResource == R.xml.chip_you) {
            viewThemeUtils.material.colorChipDrawable(context, chip)
        }
        val config = context.resources.configuration
        chip.setLayoutDirection(config.layoutDirection)
        val drawable: Int
        val isCall = "call" == type || "calls" == type
        val isGroup = "groups" == type || "user-group" == type
        if (!isGroup && !isCall) {
            drawable = if (chipResource == R.xml.chip_you) {
                R.drawable.mention_chip
            } else {
                R.drawable.accent_circle
            }
            chip.setChipIconResource(drawable)
        } else {
            chip.setChipIconResource(R.drawable.ic_circular_group_mentions)
        }
        if (type == "circle" || type == "teams") {
            chip.setChipIconResource(R.drawable.icon_circular_team)
        }

        if (isCall && isPhoneNumber(label.toString())) {
            chip.setChipIconResource(R.drawable.icon_circular_phone)
        }
        chip.setBounds(0, 0, chip.intrinsicWidth, chip.intrinsicHeight)
        if (!isGroup) {
            var url = getUrlForAvatar(conversationUser.baseUrl, id, false)
            if ("guests" == type || "guest" == type || "email" == type) {
                url = getUrlForGuestAvatar(
                    conversationUser.baseUrl,
                    label.toString(),
                    true
                )
            }
            if (isFederated) {
                val darkTheme = if (isDarkModeOn(context)) 1 else 0
                url = getUrlForFederatedAvatar(
                    conversationUser.baseUrl!!,
                    roomToken!!,
                    id!!,
                    darkTheme,
                    false
                )
            }
            val imageRequest: ImageRequest = ImageRequest.Builder(context)
                .data(url)
                .crossfade(true)
                .transformations(CircleCropTransformation())
                .target(object : Target {
                    override fun onStart(placeholder: Drawable?) {
                        // unused atm
                    }
                    override fun onError(error: Drawable?) {
                        chip.chipIcon = error
                    }

                    override fun onSuccess(result: Drawable) {
                        chip.chipIcon = result
                        // A hack to refresh the chip icon
                        emojiEditText?.post {
                            emojiEditText.setTextKeepState(
                                emojiEditText.getText(),
                                TextView.BufferType.SPANNABLE
                            )
                        }
                    }
                })
                .build()
            imageLoader(context).enqueue(imageRequest)
        }
        return chip
    }

    fun searchAndReplaceWithMentionSpan(
        key: String,
        context: Context,
        text: Spanned,
        id: String,
        roomToken: String?,
        label: String,
        type: String,
        conversationUser: User,
        @XmlRes chipXmlRes: Int,
        viewThemeUtils: ViewThemeUtils,
        isFederated: Boolean
    ): Spannable {
        val spannableString: Spannable = SpannableString(text)
        val stringText = text.toString()
        val keyWithBrackets = "{$key}"
        val m = Pattern.compile(keyWithBrackets, Pattern.CASE_INSENSITIVE or Pattern.LITERAL or Pattern.MULTILINE)
            .matcher(spannableString)
        val clickableSpan: ClickableSpan = object : ClickableSpan() {
            override fun onClick(widget: View) {
                EventBus.getDefault().post(UserMentionClickEvent(id))
            }
        }
        var lastStartIndex = 0
        var mentionChipSpan: MentionChipSpan
        while (m.find()) {
            val start = stringText.indexOf(m.group(), lastStartIndex)
            val end = start + m.group().length
            lastStartIndex = end
            val drawableForChip = getDrawableForMentionChipSpan(
                context,
                id,
                roomToken,
                label,
                conversationUser,
                type,
                chipXmlRes,
                null,
                viewThemeUtils,
                isFederated
            )
            mentionChipSpan = MentionChipSpan(
                drawableForChip,
                BetterImageSpan.ALIGN_CENTER,
                id,
                label
            )
            spannableString.setSpan(mentionChipSpan, start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            if (chipXmlRes == R.xml.chip_you) {
                spannableString.setSpan(
                    viewThemeUtils.talk.themeForegroundColorSpan(context),
                    start,
                    end,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            }
            if ("user" == type && conversationUser.userId != id && !isFederated) {
                spannableString.setSpan(clickableSpan, start, end, Spannable.SPAN_INCLUSIVE_EXCLUSIVE)
            }
        }
        return spannableString
    }

    fun searchAndColor(text: Spannable, searchText: String, @ColorInt color: Int, textSize: Int): Spannable {
        val spannableString: Spannable = SpannableString(text)
        val stringText = text.toString()
        if (TextUtils.isEmpty(text) || TextUtils.isEmpty(searchText)) {
            return spannableString
        }
        val m = Pattern.compile(
            searchText,
            Pattern.CASE_INSENSITIVE or Pattern.LITERAL or Pattern.MULTILINE
        )
            .matcher(spannableString)
        var lastStartIndex = -1
        while (m.find()) {
            val start = stringText.indexOf(m.group(), lastStartIndex)
            val end = start + m.group().length
            lastStartIndex = end
            spannableString.setSpan(
                ForegroundColorSpan(color),
                start,
                end,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            spannableString.setSpan(StyleSpan(Typeface.BOLD), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            spannableString.setSpan(AbsoluteSizeSpan(textSize), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        return spannableString
    }

    fun getMessageSelector(
        @ColorInt normalColor: Int,
        @ColorInt selectedColor: Int,
        @ColorInt pressedColor: Int,
        @DrawableRes shape: Int
    ): Drawable {
        val vectorDrawable = ContextCompat.getDrawable(
            sharedApplication!!.applicationContext,
            shape
        )
        val drawable = DrawableCompat.wrap(vectorDrawable!!).mutate()
        DrawableCompat.setTintList(
            drawable,
            ColorStateList(
                arrayOf(
                    intArrayOf(android.R.attr.state_selected),
                    intArrayOf(android.R.attr.state_pressed),
                    intArrayOf(-android.R.attr.state_pressed, -android.R.attr.state_selected)
                ),
                intArrayOf(selectedColor, pressedColor, normalColor)
            )
        )
        return drawable
    }

    /**
     * Sets the color of the status bar to `color`.
     *
     * @param activity activity
     * @param color    the color
     */
    fun applyColorToStatusBar(activity: Activity, @ColorInt color: Int) {
        val window = activity.window
        val isLightTheme = lightTheme(color)
        if (window != null) {
            val decor = window.decorView
            if (isLightTheme) {
                val systemUiFlagLightStatusBar: Int =
                    View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or
                        View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR

                decor.systemUiVisibility = systemUiFlagLightStatusBar
            } else {
                decor.systemUiVisibility = 0
            }
            window.statusBarColor = color
        }
    }

    /**
     * Tests if light color is set
     *
     * @param color the color
     * @return true if primaryColor is lighter than MAX_LIGHTNESS
     */
    fun lightTheme(color: Int): Boolean {
        val hsl = colorToHSL(color)

        // spotbugs dislikes fixed index access
        // which is enforced by having such an
        // array from Android-API itself
        return hsl[INDEX_LUMINATION] >= MAX_LIGHTNESS
    }

    private fun colorToHSL(color: Int): FloatArray {
        val hsl = FloatArray(HSL_SIZE)
        ColorUtils.RGBToHSL(Color.red(color), Color.green(color), Color.blue(color), hsl)
        return hsl
    }

    fun applyColorToNavigationBar(window: Window, @ColorInt color: Int) {
        window.navigationBarColor = color
    }

    /**
     * beautifies a given URL by removing any http/https protocol prefix.
     *
     * @param url to be beautified url
     * @return beautified url
     */
    @Suppress("ReturnCount")
    fun beautifyURL(url: String?): String {
        if (TextUtils.isEmpty(url)) {
            return ""
        }
        if (url!!.length >= HTTP_MIN_LENGTH &&
            HTTP_PROTOCOL.equals(url.substring(0, HTTP_MIN_LENGTH), ignoreCase = true)
        ) {
            return url.substring(HTTP_PROTOCOL.length).trim()
        }
        return if (url.length >= HTTPS_MIN_LENGTH &&
            HTTPS_PROTOCOL.equals(url.substring(0, HTTPS_MIN_LENGTH), ignoreCase = true)
        ) {
            url.substring(HTTPS_PROTOCOL.length).trim()
        } else {
            url.trim()
        }
    }

    /**
     * beautifies a given twitter handle by prefixing it with an @ in case it is missing.
     *
     * @param handle to be beautified twitter handle
     * @return beautified twitter handle
     */
    fun beautifyTwitterHandle(handle: String?): String {
        return if (handle != null) {
            val trimmedHandle = handle.trim()
            if (TextUtils.isEmpty(trimmedHandle)) {
                return ""
            }
            if (trimmedHandle.startsWith(TWITTER_HANDLE_PREFIX)) {
                trimmedHandle
            } else {
                TWITTER_HANDLE_PREFIX + trimmedHandle
            }
        } else {
            ""
        }
    }

    fun loadAvatarImage(user: User?, avatarImageView: ImageView?, deleteCache: Boolean) {
        if (user != null && avatarImageView != null) {
            val avatarId: String? = if (!TextUtils.isEmpty(user.userId)) {
                user.userId
            } else {
                user.username
            }
            if (avatarId != null) {
                avatarImageView.loadUserAvatar(user, avatarId, true, deleteCache)
            }
        }
    }

    @StringRes
    fun getSortOrderStringId(sortOrder: FileSortOrder): Int =
        when (sortOrder.name) {
            FileSortOrder.SORT_Z_TO_A_ID -> R.string.menu_item_sort_by_name_z_a
            FileSortOrder.SORT_NEW_TO_OLD_ID -> R.string.menu_item_sort_by_date_newest_first
            FileSortOrder.SORT_OLD_TO_NEW_ID -> R.string.menu_item_sort_by_date_oldest_first
            FileSortOrder.SORT_BIG_TO_SMALL_ID -> R.string.menu_item_sort_by_size_biggest_first
            FileSortOrder.SORT_SMALL_TO_BIG_ID -> R.string.menu_item_sort_by_size_smallest_first
            FileSortOrder.SORT_A_TO_Z_ID -> R.string.menu_item_sort_by_name_a_z
            else -> R.string.menu_item_sort_by_name_a_z
        }

    /**
     * calculates the relative time string based on the given modification timestamp.
     *
     * @param context               the app's context
     * @param modificationTimestamp the UNIX timestamp of the file modification time in milliseconds.
     * @return a relative time string
     */
    fun getRelativeTimestamp(context: Context, modificationTimestamp: Long, showFuture: Boolean): CharSequence =
        getRelativeDateTimeString(
            context,
            modificationTimestamp,
            DateUtils.SECOND_IN_MILLIS,
            DateUtils.WEEK_IN_MILLIS,
            0,
            showFuture
        )

    @Suppress("ReturnCount")
    private fun getRelativeDateTimeString(
        c: Context,
        time: Long,
        minResolution: Long,
        transitionResolution: Long,
        flags: Int,
        showFuture: Boolean
    ): CharSequence {
        val dateString: CharSequence

        // in Future
        if (!showFuture && time > System.currentTimeMillis()) {
            return unixTimeToHumanReadable(time)
        }
        // < 60 seconds -> seconds ago
        val diff = System.currentTimeMillis() - time
        dateString = if (diff in 1..<ONE_MINUTE_IN_MILLIS && minResolution == DateUtils.SECOND_IN_MILLIS) {
            return c.getString(R.string.secondsAgo)
        } else {
            DateUtils.getRelativeDateTimeString(c, time, minResolution, transitionResolution, flags)
        }
        val parts = dateString.toString().split(",".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
        if (parts.size == DATE_TIME_PARTS_SIZE) {
            if (parts[1].contains(":") && !parts[0].contains(":")) {
                return parts[0]
            } else if (parts[0].contains(":") && !parts[1].contains(":")) {
                return parts[1]
            }
        }
        // dateString contains unexpected format. fallback: use relative date time string from android api as is.
        return dateString.toString()
    }

    /**
     * Converts Unix time to human readable format
     *
     * @param milliseconds that have passed since 01/01/1970
     * @return The human readable time for the users locale
     */
    fun unixTimeToHumanReadable(milliseconds: Long): String {
        val date = Date(milliseconds)
        val df = DateFormat.getDateTimeInstance()
        return df.format(date)
    }

    fun ellipsize(text: String, maxLength: Int): String =
        if (text.length > maxLength) {
            text.substring(0, maxLength - 1) + "…"
        } else {
            text
        }
}
