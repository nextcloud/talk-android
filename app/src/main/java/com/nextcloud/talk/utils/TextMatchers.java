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
