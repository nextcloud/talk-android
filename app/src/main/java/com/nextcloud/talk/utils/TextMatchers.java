/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2017-2018 Mario Danic <mario@lovelyhq.com>
 * SPDX-FileCopyrightText: 2017 Keval Patel
 * SPDX-License-Identifier: GPL-3.0-or-later
 *
 * Partly based on https://github.com/kevalpatel2106/EmoticonGIFKeyboard/blob/master/emoticongifkeyboard/src/main/java/com/kevalpatel2106/emoticongifkeyboard/internal/emoticon/EmoticonUtils.java
 */
package com.nextcloud.talk.utils;

import android.text.Spanned;

import androidx.annotation.Nullable;
import androidx.emoji2.text.EmojiCompat;
import androidx.emoji2.text.EmojiSpan;

public final class TextMatchers {

    public static boolean isMessageWithSingleEmoticonOnly(@Nullable final String text) {
        if (text == null) {
            return false;
        }

        final String trimmed = text.trim();
        if (trimmed.isEmpty()) {
            return false;
        }

        final EmojiCompat emojiCompat = EmojiCompat.get();
        if (emojiCompat.getLoadState() != EmojiCompat.LOAD_STATE_SUCCEEDED) {
            return false;
        }

        final CharSequence processed = emojiCompat.process(trimmed);
        if (!(processed instanceof Spanned)) {
            return false;
        }

        final Spanned spanned = (Spanned) processed;
        final EmojiSpan[] spans = spanned.getSpans(0, spanned.length(), EmojiSpan.class);
        return spans.length == 1
            && spanned.getSpanStart(spans[0]) == 0
            && spanned.getSpanEnd(spans[0]) == trimmed.length();
    }
}
