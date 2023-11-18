/*
 * Nextcloud Talk application
 *
 * @author Mario Danic
 * @author Tim Krüger
 * @author Andy Scherzinger
 * @author Samanwith KSN
 * Copyright (C) 2023 Samanwith KSN <lcb2021048@iiitl.com>
 * Copyright (C) 2022 Andy Scherzinger <info@andy-scherzinger.de>
 * Copyright (C) 2021 Tim Krüger <t@timkrueger.me>
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
 *
 * Partly based on https://github.com/kevalpatel2106/EmoticonGIFKeyboard/blob/master/emoticongifkeyboard/src/main/java/com/kevalpatel2106/emoticongifkeyboard/internal/emoticon/EmoticonUtils.java
 */

package com.nextcloud.talk.utils;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import androidx.annotation.Nullable;

public final class TextMatchers {

    public static boolean isMessageWithSingleEmoticonOnly(@Nullable final String text) {
        if (text == null || text.isEmpty()) {
            return false;
        }
        String emojiRegex = "([\\p{So}\\p{Sk}])";
        Pattern pattern = Pattern.compile(emojiRegex);
        Matcher matcher = pattern.matcher(text);

        int emojiCount = 0;
        while (matcher.find()) {
            emojiCount++;
        }
        return emojiCount == 1;
    }
}
