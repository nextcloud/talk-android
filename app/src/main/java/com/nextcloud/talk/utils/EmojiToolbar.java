/*
 * Nextcloud Talk application
 *
 * @author Mario Danic
 * Copyright (C) 2017-2018 Mario Danic <mario@lovelyhq.com>
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

import android.content.Context;
import android.text.TextUtils;
import android.util.AttributeSet;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.Toolbar;
import com.nextcloud.talk.R;
import com.vanniktech.emoji.EmojiTextView;

public class EmojiToolbar extends Toolbar {
    private EmojiTextView emojiTitleTextView;

    public EmojiToolbar(Context context) {
        super(context);
        initEmojiTextView(context, null);

    }

    public EmojiToolbar(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        initEmojiTextView(context, attrs);
    }

    public EmojiToolbar(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        initEmojiTextView(context, attrs);
    }

    private void initEmojiTextView(Context context, @Nullable AttributeSet attrs) {
        if (emojiTitleTextView == null) {
            emojiTitleTextView = new EmojiTextView(context, attrs);
            emojiTitleTextView.setSingleLine(true);
            emojiTitleTextView.setEllipsize(TextUtils.TruncateAt.END);
            emojiTitleTextView.setTextAppearance(getContext(), R.style.TextAppearance_AppCompat_Widget_ActionBar_Title);
            addView(emojiTitleTextView);
        }
    }

    @Override
    public void setTitle(CharSequence title) {
        emojiTitleTextView.setText(title);
    }

    @Override
    public void setTitle(int titleRes) {
        emojiTitleTextView.setText(titleRes);
    }
}
