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

import com.vanniktech.emoji.EmojiInformation;
import com.vanniktech.emoji.Emojis;

import androidx.annotation.Nullable;

public final class TextMatchers {

    public static boolean isMessageWithSingleEmoticonOnly(@Nullable final String text) {
        final EmojiInformation emojiInformation = Emojis.emojiInformation(text);
        return (emojiInformation.isOnlyEmojis && emojiInformation.emojis.size() == 1);
    }
}
