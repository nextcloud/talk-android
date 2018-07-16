/*
 * Copyright 2017 Keval Patel.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package com.nextcloud.talk.utils.emoticons;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.drawable.Drawable;
import android.support.v7.content.res.AppCompatResources;
import android.text.style.DynamicDrawableSpan;
import android.text.style.ImageSpan;

/**
 * Created by Keval Patel on 20/08/17.
 * The {@link ImageSpan} to display custom emoticon icon based on the unicode.
 *
 * @author 'https://github.com/kevalpatel2106'
 * @see <a href='https://github.com/rockerhieu/emojicon/blob/master/library/src/main/java/io/github/rockerhieu/emojicon/EmojiconSpan.java>EmojiconSpan.java</a>
 */

final class EmoticonSpan extends DynamicDrawableSpan {
    private final float mEmoticonSize;
    private final Context mContext;
    private final int mEmoticonIcon;
    private Drawable mDeferredDrawable;

    EmoticonSpan(final Context context, final int emoticonIcon, final float size) {
        this.mContext = context;
        this.mEmoticonIcon = emoticonIcon;
        this.mEmoticonSize = size;
    }

    @SuppressWarnings("ConstantConditions")
    @Override
    public Drawable getDrawable() {
        if (mDeferredDrawable == null) {
            mDeferredDrawable = AppCompatResources.getDrawable(mContext, mEmoticonIcon);
            mDeferredDrawable.setBounds(0, 0, (int) mEmoticonSize, (int) mEmoticonSize);
        }
        return mDeferredDrawable;
    }

    @Override
    public int getSize(final Paint paint, final CharSequence text, final int start,
                       final int end, final Paint.FontMetricsInt fontMetrics) {
        if (fontMetrics != null) {
            final Paint.FontMetrics paintFontMetrics = paint.getFontMetrics();
            final float fontHeight = paintFontMetrics.descent - paintFontMetrics.ascent;
            final float centerY = paintFontMetrics.ascent + fontHeight / 2;

            fontMetrics.ascent = (int) (centerY - mEmoticonSize / 2);
            fontMetrics.top = fontMetrics.ascent;
            fontMetrics.bottom = (int) (centerY + mEmoticonSize / 2);
            fontMetrics.descent = fontMetrics.bottom;
        }

        return (int) mEmoticonSize;
    }

    @Override
    public void draw(final Canvas canvas, final CharSequence text, final int start,
                     final int end, final float x, final int top, final int y,
                     final int bottom, final Paint paint) {
        final Drawable drawable = getDrawable();
        final Paint.FontMetrics paintFontMetrics = paint.getFontMetrics();
        final float fontHeight = paintFontMetrics.descent - paintFontMetrics.ascent;
        final float centerY = y + paintFontMetrics.descent - fontHeight / 2;
        final float transitionY = centerY - mEmoticonSize / 2;

        canvas.save();
        canvas.translate(x, transitionY);
        drawable.draw(canvas);
        canvas.restore();
    }
}
