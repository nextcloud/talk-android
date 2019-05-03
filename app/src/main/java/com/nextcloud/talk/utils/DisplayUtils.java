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
import android.content.Intent;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.drawable.Animatable;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.VectorDrawable;
import android.net.Uri;
import android.os.Build;
import android.text.*;
import android.text.method.LinkMovementMethod;
import android.text.style.*;
import android.util.Log;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.*;
import androidx.appcompat.widget.AppCompatDrawableManager;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.drawable.DrawableCompat;
import androidx.emoji.text.EmojiSpan;
import com.facebook.common.executors.UiThreadImmediateExecutorService;
import com.facebook.common.references.CloseableReference;
import com.facebook.datasource.DataSource;
import com.facebook.drawee.backends.pipeline.Fresco;
import com.facebook.drawee.controller.ControllerListener;
import com.facebook.drawee.view.SimpleDraweeView;
import com.facebook.imagepipeline.common.RotationOptions;
import com.facebook.imagepipeline.core.ImagePipeline;
import com.facebook.imagepipeline.datasource.BaseBitmapDataSubscriber;
import com.facebook.imagepipeline.image.CloseableImage;
import com.facebook.imagepipeline.image.ImageInfo;
import com.facebook.imagepipeline.postprocessors.RoundAsCirclePostprocessor;
import com.facebook.imagepipeline.postprocessors.RoundPostprocessor;
import com.facebook.imagepipeline.request.ImageRequest;
import com.facebook.imagepipeline.request.ImageRequestBuilder;
import com.facebook.widget.text.span.BetterImageSpan;
import com.google.android.material.chip.ChipDrawable;
import com.nextcloud.talk.R;
import com.nextcloud.talk.application.NextcloudTalkApplication;
import com.nextcloud.talk.events.UserMentionClickEvent;
import com.nextcloud.talk.models.database.UserEntity;
import com.nextcloud.talk.utils.text.Spans;
import com.vanniktech.emoji.EmojiEditText;
import com.vanniktech.emoji.EmojiTextView;
import org.greenrobot.eventbus.EventBus;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class DisplayUtils {

    private static final String TAG = "DisplayUtils";

    public static void setClickableString(String string, String url, TextView textView) {
        SpannableString spannableString = new SpannableString(string);
        spannableString.setSpan(new ClickableSpan() {
            @Override
            public void onClick(@Nonnull View widget) {
                Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                browserIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                NextcloudTalkApplication.getSharedApplication().getApplicationContext().startActivity(browserIntent);
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

    private static void updateViewSize(@Nullable ImageInfo imageInfo, SimpleDraweeView draweeView) {
        if (imageInfo != null) {
            int maxSize = draweeView.getContext().getResources().getDimensionPixelSize(R.dimen.maximum_file_preview_size);
            draweeView.getLayoutParams().width = imageInfo.getWidth() > maxSize ? maxSize : imageInfo.getWidth();
            draweeView.getLayoutParams().height = ViewGroup.LayoutParams.WRAP_CONTENT;
            draweeView.setAspectRatio((float) imageInfo.getWidth() / imageInfo.getHeight());
            draweeView.requestLayout();
        }
    }

    public static Drawable getRoundedDrawable(Drawable drawable) {
        Bitmap bitmap = getBitmap(drawable);
        new RoundAsCirclePostprocessor(true).process(bitmap);
        return new BitmapDrawable(bitmap);
    }

    public static Bitmap getRoundedBitmapFromVectorDrawableResource(Resources resources, int resource) {
        VectorDrawable vectorDrawable = (VectorDrawable) resources.getDrawable(resource);
        Bitmap bitmap = getBitmap(vectorDrawable);
        new RoundPostprocessor(true).process(bitmap);
        return bitmap;
    }

    public static Drawable getRoundedBitmapDrawableFromVectorDrawableResource(Resources resources, int resource) {
        return new BitmapDrawable(getRoundedBitmapFromVectorDrawableResource(resources, resource));
    }

    public static float getDefaultEmojiFontSize(EmojiTextView emojiTextView) {
        final Paint.FontMetrics fontMetrics = emojiTextView.getPaint().getFontMetrics();
        return fontMetrics.descent - fontMetrics.ascent;
    }

    private static Bitmap getBitmap(Drawable drawable) {
        Bitmap bitmap = Bitmap.createBitmap(drawable.getIntrinsicWidth(),
                drawable.getIntrinsicHeight(), Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        drawable.setBounds(0, 0, canvas.getWidth(), canvas.getHeight());
        drawable.draw(canvas);
        return bitmap;
    }

    public static ImageRequest getImageRequestForUrl(String url, @Nullable UserEntity userEntity) {
        Map<String, String> headers = new HashMap<>();
        if (userEntity != null && url.startsWith(userEntity.getBaseUrl()) && url.contains("index.php/core/preview?fileId=")) {
            headers.put("Authorization", ApiUtils.getCredentials(userEntity.getUsername(),
                    userEntity.getToken()));
        }

        return ImageRequestBuilder.newBuilderWithSource(Uri.parse(url))
                .setProgressiveRenderingEnabled(true)
                .setRotationOptions(RotationOptions.autoRotate())
                .disableDiskCache()
                .setHeaders(headers)
                .build();
    }

    public static ControllerListener getImageControllerListener(SimpleDraweeView draweeView) {
        return new ControllerListener() {
            @Override
            public void onSubmit(String id, Object callerContext) {

            }

            @Override
            public void onFinalImageSet(String id, @javax.annotation.Nullable Object imageInfo, @javax.annotation.Nullable Animatable animatable) {
                updateViewSize((ImageInfo) imageInfo, draweeView);
            }

            @Override
            public void onIntermediateImageSet(String id, @javax.annotation.Nullable Object imageInfo) {
                updateViewSize((ImageInfo) imageInfo, draweeView);
            }

            @Override
            public void onIntermediateImageFailed(String id, Throwable throwable) {

            }

            @Override
            public void onFailure(String id, Throwable throwable) {

            }

            @Override
            public void onRelease(String id) {

            }
        };
    }

    public static float convertDpToPixel(float dp, Context context) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp,
                context.getResources().getDisplayMetrics()) + 0.5f);
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


    public static Drawable getDrawableForMentionChipSpan(Context context, String id, String label,
                                                         UserEntity conversationUser, String type,
                                                         @XmlRes int chipResource,
                                                         @Nullable EmojiEditText emojiEditText) {
        ChipDrawable chip = ChipDrawable.createFromResource(context, chipResource);
        chip.setText(label);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Configuration config = context.getResources().getConfiguration();
            chip.setLayoutDirection(config.getLayoutDirection());
        }

        int drawable;

        boolean isCall = "call".equals(type) || "calls".equals(type);

        if (!isCall) {
            if (chipResource == R.xml.chip_outgoing_others || chipResource == R.xml.chip_accent_background) {
                drawable = R.drawable.white_circle;
            } else {
                drawable = R.drawable.accent_circle;
            }

            chip.setChipIcon(context.getDrawable(drawable));
        } else {
            chip.setChipIcon(getRoundedDrawable(context.getDrawable(R.drawable.ic_people_group_white_24px)));
        }

        chip.setBounds(0, 0, chip.getIntrinsicWidth(), chip.getIntrinsicHeight());

        if (!isCall) {
            ImageRequest imageRequest =
                    getImageRequestForUrl(ApiUtils.getUrlForAvatarWithName(conversationUser.getBaseUrl(), id, R.dimen.avatar_size_big), null);
            ImagePipeline imagePipeline = Fresco.getImagePipeline();
            DataSource<CloseableReference<CloseableImage>> dataSource = imagePipeline.fetchDecodedImage(imageRequest, context);

            dataSource.subscribe(
                    new BaseBitmapDataSubscriber() {
                        @Override
                        protected void onNewResultImpl(Bitmap bitmap) {
                            if (bitmap != null) {
                                chip.setChipIcon(getRoundedDrawable(new BitmapDrawable(bitmap)));

                                // A hack to refresh the chip icon
                                if (emojiEditText != null) {
                                    emojiEditText.post(() -> emojiEditText.setTextKeepState(emojiEditText.getText(), TextView.BufferType.SPANNABLE));
                                }
                            }
                        }

                        @Override
                        protected void onFailureImpl(DataSource<CloseableReference<CloseableImage>> dataSource) {
                        }
                    },
                    UiThreadImmediateExecutorService.getInstance());
        }

        return chip;
    }


    public static Spannable searchAndReplaceWithMentionSpan(Context context, Spannable text,
                                                            String id, String label, String type,
                                                            UserEntity conversationUser,
                                                            @XmlRes int chipXmlRes) {

        Spannable spannableString = new SpannableString(text);
        String stringText = text.toString();

        Matcher m = Pattern.compile("@" + label,
                Pattern.CASE_INSENSITIVE | Pattern.LITERAL | Pattern.MULTILINE)
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
            mentionChipSpan = new Spans.MentionChipSpan(DisplayUtils.getDrawableForMentionChipSpan(context,
                    id, label, conversationUser, type, chipXmlRes, null),
                    BetterImageSpan.ALIGN_CENTER, id,
                    label);
            spannableString.setSpan(mentionChipSpan, start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
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


        int textSize = NextcloudTalkApplication.getSharedApplication().getResources().getDimensionPixelSize(R.dimen
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
