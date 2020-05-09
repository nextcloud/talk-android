/*
 * Nextcloud Talk application
 *
 * @author Mario Danic
 * Copyright (C) 2017 Mario Danic <mario@lovelyhq.com>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.nextcloud.talk.utils

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Typeface
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Build
import android.text.*
import android.text.method.LinkMovementMethod
import android.text.style.AbsoluteSizeSpan
import android.text.style.ClickableSpan
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.util.Log
import android.util.TypedValue
import android.view.View
import android.widget.EditText
import android.widget.TextView
import androidx.annotation.ColorInt
import androidx.annotation.ColorRes
import androidx.annotation.DrawableRes
import androidx.annotation.XmlRes
import androidx.appcompat.widget.AppCompatDrawableManager
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.DrawableCompat
import androidx.emoji.text.EmojiCompat
import coil.Coil
import coil.api.load
import coil.bitmappool.BitmapPool
import coil.size.OriginalSize
import coil.target.Target
import coil.transform.CircleCropTransformation
import com.google.android.material.chip.ChipDrawable
import com.nextcloud.talk.R
import com.nextcloud.talk.application.NextcloudTalkApplication
import com.nextcloud.talk.events.UserMentionClickEvent
import com.nextcloud.talk.newarch.local.models.User
import com.nextcloud.talk.newarch.utils.Images
import com.nextcloud.talk.utils.text.Spans
import kotlinx.coroutines.runBlocking
import org.greenrobot.eventbus.EventBus
import java.lang.reflect.InvocationTargetException
import java.util.regex.Pattern

object DisplayUtils {

    private val TAG = "DisplayUtils"

    fun setClickableString(
            string: String,
            url: String,
            textView: TextView
    ) {
        val spannableString = SpannableString(string)
        spannableString.setSpan(object : ClickableSpan() {
            override fun onClick(widget: View) {
                val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                browserIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                NextcloudTalkApplication.sharedApplication!!
                        .applicationContext
                        .startActivity(browserIntent)
            }

            override fun updateDrawState(ds: TextPaint) {
                super.updateDrawState(ds)
                ds.isUnderlineText = false
            }
        }, 0, string.length, Spanned.SPAN_INCLUSIVE_EXCLUSIVE)
        textView.text = spannableString
        textView.movementMethod = LinkMovementMethod.getInstance()
    }

    fun getRoundedDrawableFromBitmap(bitmap: Bitmap): Drawable {
        return runBlocking {
            return@runBlocking BitmapDrawable(CircleCropTransformation().transform(BitmapPool(0), bitmap, OriginalSize))
        }
    }

    fun getRoundedDrawable(drawable: Drawable?): Drawable {
        val bitmap = getBitmap(drawable!!)

        return runBlocking {
            return@runBlocking BitmapDrawable(CircleCropTransformation().transform(BitmapPool(0), bitmap, OriginalSize))
        }
    }


    private fun getBitmap(drawable: Drawable): Bitmap {
        val bitmap = Bitmap.createBitmap(
                drawable.intrinsicWidth,
                drawable.intrinsicHeight, Bitmap.Config.ARGB_8888
        )
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, canvas.width, canvas.height)
        drawable.draw(canvas)
        return bitmap
    }

    fun convertDpToPixel(
            dp: Float,
            context: Context
    ): Float {
        return Math.round(
                        TypedValue.applyDimension(
                                TypedValue.COMPLEX_UNIT_DIP, dp,
                                context.resources.displayMetrics
                        ) + 0.5f
                )
                .toFloat()
    }

    // Solution inspired by https://stackoverflow.com/questions/34936590/why-isnt-my-vector-drawable-scaling-as-expected
    fun useCompatVectorIfNeeded() {
        if (Build.VERSION.SDK_INT < 23) {
            try {
                @SuppressLint("RestrictedApi") val drawableManager = AppCompatDrawableManager.get()
                val inflateDelegateClass =
                        Class.forName("android.support.v7.widget.AppCompatDrawableManager\$InflateDelegate")
                val vdcInflateDelegateClass =
                        Class.forName("android.support.v7.widget.AppCompatDrawableManager\$VdcInflateDelegate")

                val constructor = vdcInflateDelegateClass.getDeclaredConstructor()
                constructor.isAccessible = true
                val vdcInflateDelegate = constructor.newInstance()

                val args = arrayOf(String::class.java, inflateDelegateClass)
                val addDelegate =
                        AppCompatDrawableManager::class.java.getDeclaredMethod("addDelegate", *args)
                addDelegate.isAccessible = true
                addDelegate.invoke(drawableManager, "vector", vdcInflateDelegate)
            } catch (e: ClassNotFoundException) {
                Log.e(TAG, "Failed to use reflection to enable proper vector scaling")
            } catch (e: NoSuchMethodException) {
                Log.e(TAG, "Failed to use reflection to enable proper vector scaling")
            } catch (e: InstantiationException) {
                Log.e(TAG, "Failed to use reflection to enable proper vector scaling")
            } catch (e: InvocationTargetException) {
                Log.e(TAG, "Failed to use reflection to enable proper vector scaling")
            } catch (e: IllegalAccessException) {
                Log.e(TAG, "Failed to use reflection to enable proper vector scaling")
            }

        }
    }

    fun getTintedDrawable(
            res: Resources, @DrawableRes drawableResId: Int,
            @ColorRes colorResId: Int
    ): Drawable {
        val drawable = res.getDrawable(drawableResId)
        val mutableDrawable = drawable.mutate()
        val color = res.getColor(colorResId)
        mutableDrawable.setTint(color)
        return mutableDrawable
    }

    fun getDrawableForMentionChipSpan(
            context: Context,
            id: String,
            label: CharSequence,
            conversationUser: User,
            type: String,
            @XmlRes chipResource: Int,
            emojiEditText: EditText?
    ): Drawable {
        val chip = ChipDrawable.createFromResource(context, chipResource)
        chip.text = EmojiCompat.get()
                .process(label)
        chip.ellipsize = TextUtils.TruncateAt.END

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val config = context.resources.configuration
            chip.layoutDirection = config.layoutDirection
        }

        val drawable: Int

        val isCall = "call" == type || "calls" == type

        val target = object : Target {
            override fun onSuccess(result: Drawable) {
                super.onSuccess(result)
                chip.chipIcon = result

                // A hack to refresh the chip icon
                emojiEditText?.post {
                    emojiEditText.setTextKeepState(
                            emojiEditText.text,
                            TextView.BufferType.SPANNABLE
                    )
                }
            }
        }

        if (!isCall) {
            if (chipResource == R.xml.chip_you) {
                drawable = R.drawable.mention_chip
            } else {
                drawable = R.drawable.accent_circle
            }

            Coil.load(context, drawable) {
                target(target)
            }
        } else {
            Coil.load(context, R.drawable.ic_people_group_white_24px_with_circle) {
                transformations(CircleCropTransformation())
                target(target)
            }
        }

        chip.setBounds(0, 0, chip.intrinsicWidth, chip.intrinsicHeight)

        if (!isCall) {
            var url = ApiUtils.getUrlForAvatarWithName(
                    conversationUser.baseUrl, id,
                    R.dimen.avatar_size_big
            )
            if ("guests" == type || "guest" == type) {
                url = ApiUtils.getUrlForAvatarWithNameForGuests(
                        conversationUser.baseUrl,
                        label.toString(), R.dimen.avatar_size_big
                )
            }

            val request = Images().getRequestForUrl(Coil.loader(), context, url, conversationUser, target, null, CircleCropTransformation())
            Coil.loader().load(request)
        }

        return chip
    }

    fun searchAndReplaceWithMentionSpan(
            context: Context,
            text: Spannable,
            id: String,
            label: String,
            type: String,
            conversationUser: User,
            @XmlRes chipXmlRes: Int
    ): Spannable {

        val spannableString = SpannableString(text)
        val stringText = text.toString()

        val m = Pattern.compile(
                        "@$label",
                        Pattern.CASE_INSENSITIVE or Pattern.LITERAL or Pattern.MULTILINE
                )
                .matcher(spannableString)

        val clickableSpan = object : ClickableSpan() {
            override fun onClick(widget: View) {
                EventBus.getDefault()
                        .post(UserMentionClickEvent(id))
            }
        }

        var lastStartIndex = -1
        var mentionChipSpan: Spans.MentionChipSpan
        while (m.find()) {
            val start = stringText.indexOf(m.group(), lastStartIndex)
            val end = start + m.group().length
            lastStartIndex = end
            /*mentionChipSpan = Spans.MentionChipSpan(
                    getDrawableForMentionChipSpan(
                            context,
                            id, label, conversationUser, chipXmlRes, null
                    ),
                    BetterImageSpan.ALIGN_CENTER, id,
                    label
            )*/
            //spannableString.setSpan(mentionChipSpan, start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            if ("user" == type && conversationUser.userId != id) {
                spannableString.setSpan(clickableSpan, start, end, Spannable.SPAN_INCLUSIVE_EXCLUSIVE)
            }
        }

        return spannableString
    }

    fun searchAndColor(
            text: Spannable,
            searchText: String, @ColorInt color: Int
    ): Spannable {

        val spannableString = SpannableString(text)
        val stringText = text.toString()
        if (TextUtils.isEmpty(text) || TextUtils.isEmpty(searchText)) {
            return spannableString
        }

        val m = Pattern.compile(
                        searchText,
                        Pattern.CASE_INSENSITIVE or Pattern.LITERAL or Pattern.MULTILINE
                )
                .matcher(spannableString)

        val textSize = NextcloudTalkApplication.sharedApplication!!
                .resources
                .getDimensionPixelSize(
                        R.dimen
                                .chat_text_size
                )

        var lastStartIndex = -1
        while (m.find()) {
            val start = stringText.indexOf(m.group(), lastStartIndex)
            val end = start + m.group().length
            lastStartIndex = end
            spannableString.setSpan(
                    ForegroundColorSpan(color), start, end,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            spannableString.setSpan(
                    StyleSpan(Typeface.BOLD), start, end,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            spannableString.setSpan(
                    AbsoluteSizeSpan(textSize), start, end,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }

        return spannableString
    }

    fun getMessageSelector(
            @ColorInt normalColor: Int, @ColorInt selectedColor: Int,
            @ColorInt pressedColor: Int, @DrawableRes shape: Int
    ): Drawable {

        val vectorDrawable = ContextCompat.getDrawable(
                NextcloudTalkApplication.sharedApplication!!
                        .applicationContext,
                shape
        )
        val drawable = DrawableCompat.wrap(vectorDrawable!!)
                .mutate()
        DrawableCompat.setTintList(
                drawable,
                ColorStateList(
                        arrayOf(
                                intArrayOf(android.R.attr.state_selected), intArrayOf(android.R.attr.state_pressed),
                                intArrayOf(-android.R.attr.state_pressed, -android.R.attr.state_selected)
                        ),
                        intArrayOf(selectedColor, pressedColor, normalColor)
                )
        )
        return drawable
    }
}
