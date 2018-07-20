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

package com.nextcloud.talk.utils;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.Resources;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.support.annotation.ColorInt;
import android.support.annotation.ColorRes;
import android.support.annotation.DrawableRes;
import android.support.v4.content.ContextCompat;
import android.support.v4.graphics.drawable.DrawableCompat;
import android.support.v7.widget.AppCompatDrawableManager;
import android.text.Spannable;
import android.text.TextUtils;
import android.text.style.AbsoluteSizeSpan;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.util.DisplayMetrics;
import android.util.Log;

import com.nextcloud.talk.R;
import com.nextcloud.talk.application.NextcloudTalkApplication;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class DisplayUtils {

    private static final String TAG = "DisplayUtils";

    public static float convertDpToPixel(float dp, Context context) {
        Resources resources = context.getResources();
        DisplayMetrics metrics = resources.getDisplayMetrics();
        float px = dp * ((float) metrics.densityDpi / DisplayMetrics.DENSITY_DEFAULT);
        return px;
    }

    // Solution inspired by https://stackoverflow.com/questions/34936590/why-isnt-my-vector-drawable-scaling-as-expected
    public static void useCompatVectorIfNeeded() {
        if (Build.VERSION.SDK_INT < 23) {
            try {
                @SuppressLint("RestrictedApi") AppCompatDrawableManager drawableManager = AppCompatDrawableManager.get();
                Class<?> inflateDelegateClass = Class.forName("android.support.v7.widget.AppCompatDrawableManager$InflateDelegate");
                Class<?> vdcInflateDelegateClass = Class.forName("android.support.v7.widget.AppCompatDrawableManager$VdcInflateDelegate");

                Constructor<?> constructor = vdcInflateDelegateClass.getDeclaredConstructor();
                constructor.setAccessible(true);
                Object vdcInflateDelegate = constructor.newInstance();

                Class<?> args[] = {String.class, inflateDelegateClass};
                Method addDelegate = AppCompatDrawableManager.class.getDeclaredMethod("addDelegate", args);
                addDelegate.setAccessible(true);
                addDelegate.invoke(drawableManager, "vector", vdcInflateDelegate);
            } catch (ClassNotFoundException | NoSuchMethodException | InstantiationException |
                    InvocationTargetException | IllegalAccessException e) {
                Log.e(TAG, "Failed to use reflection to enable proper vector scaling");
            }
        }
    }

    public static Drawable getTintedDrawable(Resources res, @DrawableRes int drawableResId, @ColorRes int colorResId) {
        Drawable drawable = res.getDrawable(drawableResId);
        int color = res.getColor(colorResId);
        drawable.setTint(color);
        return drawable;
    }


    public static Spannable searchAndColor(String text, Spannable spannable, String searchText, @ColorInt int color) {

        if (TextUtils.isEmpty(text) || TextUtils.isEmpty(searchText)) {
            return spannable;
        }

        Matcher m = Pattern.compile(searchText, Pattern.CASE_INSENSITIVE | Pattern.LITERAL)
                .matcher(text);


        int textSize = NextcloudTalkApplication.getSharedApplication().getResources().getDimensionPixelSize(R.dimen
                .chat_text_size);
        while (m.find()) {
            int start = text.indexOf(m.group());
            int end = text.indexOf(m.group()) + m.group().length();
            spannable.setSpan(new ForegroundColorSpan(color), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            spannable.setSpan(new StyleSpan(Typeface.BOLD), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            spannable.setSpan(new AbsoluteSizeSpan(textSize) , start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        }

        return spannable;
    }

    public static Drawable getMessageSelector(@ColorInt int normalColor, @ColorInt int selectedColor,
                                        @ColorInt int pressedColor, @DrawableRes int shape) {

        Drawable vectorDrawable = ContextCompat.getDrawable(NextcloudTalkApplication.getSharedApplication()
                .getApplicationContext(),
                shape);
        Drawable drawable = DrawableCompat.wrap(vectorDrawable).mutate();
        DrawableCompat.setTintList(
                drawable,
                new ColorStateList(
                        new int[][]{
                                new int[]{android.R.attr.state_selected},
                                new int[]{android.R.attr.state_pressed},
                                new int[]{-android.R.attr.state_pressed, -android.R.attr.state_selected}
                        },
                        new int[]{selectedColor, pressedColor, normalColor}
                ));
        return drawable;
    }

}
