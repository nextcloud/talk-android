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
 *
 * Partly based on https://github.com/kevalpatel2106/EmoticonGIFKeyboard/blob/master/emoticongifkeyboard/src/main/java/com/kevalpatel2106/emoticongifkeyboard/internal/emoticon/EmoticonUtils.java
 */

package com.nextcloud.talk.utils;

import android.content.Context;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.text.TextUtils;
import android.util.Log;

import com.nextcloud.talk.R;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class EmojiDetection {

    private static final String TAG = "EmojiDetection";

    private static Pattern regexPattern;

    public static boolean isMessageWithSingleEmoticonOnly(@NonNull final Context context,
                                                          @Nullable final CharSequence text) {

        int startPosition = -1;
        int endPosition = -1;

        if (!TextUtils.isEmpty(text)) {
            final Matcher matcher = getRegex(context).matcher(text);
            while (matcher.find()) {
                if (startPosition == -1 && endPosition == -1) {
                    startPosition = matcher.start();
                    endPosition = matcher.end();
                } else {
                    return false;
                }
            }
        } else {
            return false;
        }

        return startPosition == 0 && text.length() == endPosition;
    }

    @NonNull
    private static Pattern getRegex(@NonNull final Context context) {
        if (regexPattern == null) {
            String regex = readTextFile(context, R.raw.regex);
            regexPattern = Pattern.compile(regex);
        }

        return regexPattern;
    }

    @NonNull
    private static String readTextFile(@NonNull Context context, int rowResource) {
        InputStream inputStream = context.getResources().openRawResource(rowResource);
        BufferedReader br = new BufferedReader(new InputStreamReader(inputStream));

        StringBuilder builder = new StringBuilder();
        try {
            String sCurrentLine;
            while ((sCurrentLine = br.readLine()) != null) builder.append(sCurrentLine);
        } catch (IOException e) {
            Log.e(TAG, e.getLocalizedMessage());
        } finally {
            try {
                inputStream.close();
                br.close();
            } catch (IOException e) {
                Log.e(TAG, e.getLocalizedMessage());
            }
        }

        return builder.toString();
    }
}
