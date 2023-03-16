/*
 * Nextcloud Talk application
 *
 * @author Mario Danic
 * @author Andy Scherzinger
 * @author Tim Krüger
 * Copyright (C) 2022 Tim Krüger <t@timkrueger.me>
 * Copyright (C) 2021 Andy Scherzinger <info@andy-scherzinger.de>
 * Copyright (C) 2017-2020 Mario Danic <mario@lovelyhq.com>
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

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextPaint;
import android.text.TextUtils;
import android.text.format.DateUtils;
import android.text.method.LinkMovementMethod;
import android.text.style.AbsoluteSizeSpan;
import android.text.style.ClickableSpan;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.util.TypedValue;
import android.view.View;
import android.view.Window;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;

import com.google.android.material.chip.ChipDrawable;
import com.nextcloud.talk.R;
import com.nextcloud.talk.application.NextcloudTalkApplication;
import com.nextcloud.talk.data.user.model.User;
import com.nextcloud.talk.events.UserMentionClickEvent;
import com.nextcloud.talk.extensions.ImageViewExtensionsKt;
import com.nextcloud.talk.ui.theme.ViewThemeUtils;
import com.nextcloud.talk.utils.text.Spans;

import org.greenrobot.eventbus.EventBus;

import java.text.DateFormat;
import java.util.Date;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import androidx.annotation.ColorInt;
import androidx.annotation.ColorRes;
import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.annotation.XmlRes;
import androidx.core.content.ContextCompat;
import androidx.core.content.res.ResourcesCompat;
import androidx.core.graphics.ColorUtils;
import androidx.core.graphics.drawable.DrawableCompat;
import androidx.emoji2.text.EmojiCompat;
import coil.Coil;
import coil.request.ImageRequest;
import coil.target.Target;
import coil.transform.CircleCropTransformation;
import third.parties.fresco.BetterImageSpan;

import static com.nextcloud.talk.utils.FileSortOrder.sort_a_to_z_id;
import static com.nextcloud.talk.utils.FileSortOrder.sort_big_to_small_id;
import static com.nextcloud.talk.utils.FileSortOrder.sort_new_to_old_id;
import static com.nextcloud.talk.utils.FileSortOrder.sort_old_to_new_id;
import static com.nextcloud.talk.utils.FileSortOrder.sort_small_to_big_id;
import static com.nextcloud.talk.utils.FileSortOrder.sort_z_to_a_id;

public class DisplayUtils {

    private static final String TAG = "DisplayUtils";
    private static final int INDEX_LUMINATION = 2;
    private static final double MAX_LIGHTNESS = 0.92;

    private static final String TWITTER_HANDLE_PREFIX = "@";
    private static final String HTTP_PROTOCOL = "http://";
    private static final String HTTPS_PROTOCOL = "https://";

    private static final int DATE_TIME_PARTS_SIZE = 2;

    public static void setClickableString(String string, String url, TextView textView) {
        SpannableString spannableString = new SpannableString(string);
        spannableString.setSpan(new ClickableSpan() {
            @Override
            public void onClick(@NonNull View widget) {
                Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                browserIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                NextcloudTalkApplication
                    .Companion
                    .getSharedApplication()
                    .getApplicationContext()
                    .startActivity(browserIntent);
            }

            @Override
            public void updateDrawState(@NonNull TextPaint ds) {
                super.updateDrawState(ds);
                ds.setUnderlineText(false);
            }
        }, 0, string.length(), Spanned.SPAN_INCLUSIVE_EXCLUSIVE);
        textView.setText(spannableString);
        textView.setMovementMethod(LinkMovementMethod.getInstance());
    }

    public static Bitmap getBitmap(Drawable drawable) {
        Bitmap bitmap = Bitmap.createBitmap(drawable.getIntrinsicWidth(),
                                            drawable.getIntrinsicHeight(),
                                            Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        drawable.setBounds(0, 0, canvas.getWidth(), canvas.getHeight());
        drawable.draw(canvas);
        return bitmap;
    }

    public static float convertDpToPixel(float dp, Context context) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp,
                                                    context.getResources().getDisplayMetrics()) + 0.5f);
    }

    public static float convertPixelToDp(float px, Context context) {
        return px / context.getResources().getDisplayMetrics().density;
    }

    public static Drawable getTintedDrawable(Resources res, @DrawableRes int drawableResId, @ColorRes int colorResId) {
        Drawable drawable = ResourcesCompat.getDrawable(res, drawableResId, null);

        int color = res.getColor(colorResId);
        if (drawable != null) {
            drawable.setTint(color);
        }
        return drawable;
    }

    public static Drawable getDrawableForMentionChipSpan(Context context,
                                                         String id,
                                                         CharSequence label,
                                                         User conversationUser,
                                                         String type,
                                                         @XmlRes int chipResource,
                                                         @Nullable EditText emojiEditText,
                                                         ViewThemeUtils viewThemeUtils) {
        ChipDrawable chip = ChipDrawable.createFromResource(context, chipResource);
        chip.setText(EmojiCompat.get().process(label));
        chip.setEllipsize(TextUtils.TruncateAt.MIDDLE);

        if (chipResource == R.xml.chip_you) {
            viewThemeUtils.material.colorChipDrawable(context, chip);
        }

        Configuration config = context.getResources().getConfiguration();
        chip.setLayoutDirection(config.getLayoutDirection());

        int drawable;

        boolean isCall = "call".equals(type) || "calls".equals(type);

        if (!isCall) {
            if (chipResource == R.xml.chip_you) {
                drawable = R.drawable.mention_chip;
            } else {
                drawable = R.drawable.accent_circle;
            }

            chip.setChipIconResource(drawable);
        } else {
            chip.setChipIconResource(R.drawable.ic_circular_group);
        }

        chip.setBounds(0, 0, chip.getIntrinsicWidth(), chip.getIntrinsicHeight());

        if (!isCall) {
            String url = ApiUtils.getUrlForAvatar(conversationUser.getBaseUrl(), id, true);
            if ("guests".equals(type) || "guest".equals(type)) {
                url = ApiUtils.getUrlForGuestAvatar(
                    conversationUser.getBaseUrl(),
                    String.valueOf(label), true);
            }

            ImageRequest imageRequest = new ImageRequest.Builder(context)
                .data(url)
                .crossfade(true)
                .transformations(new CircleCropTransformation())
                .target(new Target() {
                    @Override
                    public void onStart(@Nullable Drawable drawable) {

                    }

                    @Override
                    public void onError(@Nullable Drawable drawable) {

                    }

                    @Override
                    public void onSuccess(@NonNull Drawable drawable) {
                        chip.setChipIcon(drawable);

                        // A hack to refresh the chip icon
                        if (emojiEditText != null) {
                            emojiEditText.post(() -> emojiEditText.setTextKeepState(
                                emojiEditText.getText(),
                                TextView.BufferType.SPANNABLE));
                        }
                    }
                })
                .build();

            Coil.imageLoader(context).enqueue(imageRequest);
        }

        return chip;
    }

    public static Spannable searchAndReplaceWithMentionSpan(String key, Context context, Spannable text,
                                                            String id, String label, String type,
                                                            User conversationUser,
                                                            @XmlRes int chipXmlRes,
                                                            ViewThemeUtils viewThemeUtils) {

        Spannable spannableString = new SpannableString(text);
        String stringText = text.toString();

        String keyWithBrackets = "{" + key + "}";
        Matcher m = Pattern.compile(keyWithBrackets, Pattern.CASE_INSENSITIVE | Pattern.LITERAL | Pattern.MULTILINE)
            .matcher(spannableString);

        ClickableSpan clickableSpan = new ClickableSpan() {
            @Override
            public void onClick(@NonNull View widget) {
                EventBus.getDefault().post(new UserMentionClickEvent(id));
            }
        };

        int lastStartIndex = -1;
        Spans.MentionChipSpan mentionChipSpan;
        while (m.find()) {
            int start = stringText.indexOf(m.group(), lastStartIndex);
            int end = start + m.group().length();
            lastStartIndex = end;

            Drawable drawableForChip = DisplayUtils.getDrawableForMentionChipSpan(context,
                                                       id,
                                                       label,
                                                       conversationUser,
                                                       type,
                                                       chipXmlRes,
                                                       null,
                                                       viewThemeUtils);

            mentionChipSpan = new Spans.MentionChipSpan(drawableForChip,
                                                        BetterImageSpan.ALIGN_CENTER,
                                                        id,
                                                        label);

            spannableString.setSpan(mentionChipSpan, start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);

            if (chipXmlRes == R.xml.chip_you) {
                spannableString.setSpan(
                    new ForegroundColorSpan(viewThemeUtils.getScheme(context).getOnPrimary()),
                    start,
                    end,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
            if ("user".equals(type) && !conversationUser.getUserId().equals(id)) {
                spannableString.setSpan(clickableSpan, start, end, Spannable.SPAN_INCLUSIVE_EXCLUSIVE);
            }
        }

        return spannableString;
    }

    public static Spannable searchAndColor(Spannable text, String searchText, @ColorInt int color) {

        Spannable spannableString = new SpannableString(text);
        String stringText = text.toString();
        if (TextUtils.isEmpty(text) || TextUtils.isEmpty(searchText)) {
            return spannableString;
        }

        Matcher m = Pattern.compile(searchText,
                                    Pattern.CASE_INSENSITIVE | Pattern.LITERAL | Pattern.MULTILINE)
            .matcher(spannableString);


        int textSize = NextcloudTalkApplication.Companion.getSharedApplication().getResources().getDimensionPixelSize(R.dimen
                                                                                                                          .chat_text_size);

        int lastStartIndex = -1;
        while (m.find()) {
            int start = stringText.indexOf(m.group(), lastStartIndex);
            int end = start + m.group().length();
            lastStartIndex = end;
            spannableString.setSpan(new ForegroundColorSpan(color), start, end,
                                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            spannableString.setSpan(new StyleSpan(Typeface.BOLD), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            spannableString.setSpan(new AbsoluteSizeSpan(textSize), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        }

        return spannableString;
    }

    public static Drawable getMessageSelector(@ColorInt int normalColor,
                                              @ColorInt int selectedColor,
                                              @ColorInt int pressedColor,
                                              @DrawableRes int shape) {

        Drawable vectorDrawable = ContextCompat.getDrawable(NextcloudTalkApplication.Companion.getSharedApplication()
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

    /**
     * Sets the color of the status bar to {@code color}.
     *
     * @param activity activity
     * @param color    the color
     */
    public static void applyColorToStatusBar(Activity activity, @ColorInt int color) {
        Window window = activity.getWindow();
        boolean isLightTheme = lightTheme(color);
        if (window != null) {

            View decor = window.getDecorView();
            if (isLightTheme) {
                int systemUiFlagLightStatusBar;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    systemUiFlagLightStatusBar = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR |
                        View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
                } else {
                    systemUiFlagLightStatusBar = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
                }
                decor.setSystemUiVisibility(systemUiFlagLightStatusBar);
            } else {
                decor.setSystemUiVisibility(0);
            }
            window.setStatusBarColor(color);
        }
    }

    /**
     * Tests if light color is set
     *
     * @param color the color
     * @return true if primaryColor is lighter than MAX_LIGHTNESS
     */
    @SuppressWarnings("CLI_CONSTANT_LIST_INDEX")
    public static boolean lightTheme(int color) {
        float[] hsl = colorToHSL(color);

        // spotbugs dislikes fixed index access
        // which is enforced by having such an
        // array from Android-API itself
        return hsl[INDEX_LUMINATION] >= MAX_LIGHTNESS;
    }

    private static float[] colorToHSL(int color) {
        float[] hsl = new float[3];
        ColorUtils.RGBToHSL(Color.red(color), Color.green(color), Color.blue(color), hsl);

        return hsl;
    }

    public static void applyColorToNavigationBar(Window window, @ColorInt int color) {
        window.setNavigationBarColor(color);
    }

    /**
     * beautifies a given URL by removing any http/https protocol prefix.
     *
     * @param url to be beautified url
     * @return beautified url
     */
    public static String beautifyURL(@Nullable String url) {
        if (TextUtils.isEmpty(url)) {
            return "";
        }

        if (url.length() >= 7 && HTTP_PROTOCOL.equalsIgnoreCase(url.substring(0, 7))) {
            return url.substring(HTTP_PROTOCOL.length()).trim();
        }

        if (url.length() >= 8 && HTTPS_PROTOCOL.equalsIgnoreCase(url.substring(0, 8))) {
            return url.substring(HTTPS_PROTOCOL.length()).trim();
        }

        return url.trim();
    }

    /**
     * beautifies a given twitter handle by prefixing it with an @ in case it is missing.
     *
     * @param handle to be beautified twitter handle
     * @return beautified twitter handle
     */
    public static String beautifyTwitterHandle(@Nullable String handle) {
        if (handle != null) {
            String trimmedHandle = handle.trim();

            if (TextUtils.isEmpty(trimmedHandle)) {
                return "";
            }

            if (trimmedHandle.startsWith(TWITTER_HANDLE_PREFIX)) {
                return trimmedHandle;
            } else {
                return TWITTER_HANDLE_PREFIX + trimmedHandle;
            }
        } else {
            return "";
        }
    }

    public static void loadAvatarImage(User user, ImageView avatarImageView, boolean deleteCache) {
        String avatarId;
        if (!TextUtils.isEmpty(user.getUserId())) {
            avatarId = user.getUserId();
        } else {
            avatarId = user.getUsername();
        }

        if (avatarId != null) {
            if (deleteCache) {
                ImageViewExtensionsKt.replaceAvatar(avatarImageView, user, avatarId, true);
            } else {
                ImageViewExtensionsKt.loadAvatar(avatarImageView, user, avatarId, true);
            }
        }
    }

    public static @StringRes
    int getSortOrderStringId(FileSortOrder sortOrder) {
        switch (sortOrder.getName()) {
            case sort_z_to_a_id:
                return R.string.menu_item_sort_by_name_z_a;
            case sort_new_to_old_id:
                return R.string.menu_item_sort_by_date_newest_first;
            case sort_old_to_new_id:
                return R.string.menu_item_sort_by_date_oldest_first;
            case sort_big_to_small_id:
                return R.string.menu_item_sort_by_size_biggest_first;
            case sort_small_to_big_id:
                return R.string.menu_item_sort_by_size_smallest_first;
            case sort_a_to_z_id:
            default:
                return R.string.menu_item_sort_by_name_a_z;
        }
    }

    /**
     * calculates the relative time string based on the given modification timestamp.
     *
     * @param context               the app's context
     * @param modificationTimestamp the UNIX timestamp of the file modification time in milliseconds.
     * @return a relative time string
     */

    public static CharSequence getRelativeTimestamp(Context context, long modificationTimestamp, boolean showFuture) {
        return getRelativeDateTimeString(context,
                                         modificationTimestamp,
                                         DateUtils.SECOND_IN_MILLIS,
                                         DateUtils.WEEK_IN_MILLIS,
                                         0,
                                         showFuture);
    }

    public static CharSequence getRelativeDateTimeString(Context c,
                                                         long time,
                                                         long minResolution,
                                                         long transitionResolution,
                                                         int flags,
                                                         boolean showFuture) {

        CharSequence dateString = "";

        // in Future
        if (!showFuture && time > System.currentTimeMillis()) {
            return DisplayUtils.unixTimeToHumanReadable(time);
        }
        // < 60 seconds -> seconds ago
        long diff = System.currentTimeMillis() - time;
        if (diff > 0 && diff < 60 * 1000 && minResolution == DateUtils.SECOND_IN_MILLIS) {
            return c.getString(R.string.secondsAgo);
        } else {
            dateString = DateUtils.getRelativeDateTimeString(c, time, minResolution, transitionResolution, flags);
        }

        String[] parts = dateString.toString().split(",");
        if (parts.length == DATE_TIME_PARTS_SIZE) {
            if (parts[1].contains(":") && !parts[0].contains(":")) {
                return parts[0];
            } else if (parts[0].contains(":") && !parts[1].contains(":")) {
                return parts[1];
            }
        }
        // dateString contains unexpected format. fallback: use relative date time string from android api as is.
        return dateString.toString();
    }

    /**
     * Converts Unix time to human readable format
     *
     * @param milliseconds that have passed since 01/01/1970
     * @return The human readable time for the users locale
     */
    public static String unixTimeToHumanReadable(long milliseconds) {
        Date date = new Date(milliseconds);
        DateFormat df = DateFormat.getDateTimeInstance();
        return df.format(date);
    }
}
