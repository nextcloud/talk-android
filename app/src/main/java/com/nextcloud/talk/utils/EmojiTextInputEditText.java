/*
 * Nextcloud Talk application
 *
 * @author Mario Danic
 * Copyright (C) 2017-2019 Mario Danic <mario@lovelyhq.com>
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
import android.util.AttributeSet;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;

import androidx.annotation.NonNull;
import androidx.emoji2.viewsintegration.EmojiEditTextHelper;

import com.google.android.material.textfield.TextInputEditText;

public class EmojiTextInputEditText extends TextInputEditText {
    private EmojiEditTextHelper emojiEditTextHelper;

    public EmojiTextInputEditText(Context context) {
        super(context);
        init();
    }

    public EmojiTextInputEditText(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public EmojiTextInputEditText(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        super.setKeyListener(getEmojiEditTextHelper().getKeyListener(getKeyListener()));
    }

    @Override
    public void setKeyListener(android.text.method.KeyListener keyListener) {
        super.setKeyListener(getEmojiEditTextHelper().getKeyListener(keyListener));
    }

    @Override
    public InputConnection onCreateInputConnection(@NonNull EditorInfo outAttrs) {
        InputConnection inputConnection = super.onCreateInputConnection(outAttrs);
        return getEmojiEditTextHelper().onCreateInputConnection(inputConnection, outAttrs);
    }

    private EmojiEditTextHelper getEmojiEditTextHelper() {
       if (emojiEditTextHelper == null) {
           emojiEditTextHelper = new EmojiEditTextHelper(this);
       }

       return emojiEditTextHelper;
    }
}
