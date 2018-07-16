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
 *
 *  Modified by Mario Danic (mario@lovelyhq.com) - Copyright 2018
 */

package com.nextcloud.talk.utils.emoticons;

import android.content.Context;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.text.Spannable;
import android.text.TextUtils;

import com.kevalpatel2106.emoticongifkeyboard.R;
import com.kevalpatel2106.emoticongifkeyboard.emoticons.Emoticon;
import com.kevalpatel2106.emoticongifkeyboard.emoticons.EmoticonProvider;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Created by Keval Patel on 20/08/17.
 * Utils to find emoticons from the string.
 *
 * @author 'https://github.com/kevalpatel2106'
 */

public final class EmoticonUtils {
    /**
     * {@link Pattern} to find the supported emoticons unicodes.
     */
    private static Pattern sRegexPattern;

    /**
     * Private constructor.
     */
    private EmoticonUtils() {
        //Do nothing
    }

    /**
     * Replace the system emoticons with the provided custom emoticons drawable. This will find the
     * unicodes with supported emoticons in the provided text and  will replace the emoticons with
     * appropriate images.
     *
     * @param context          instance of caller.
     * @param text             Text to replace
     * @param emoticonProvider {@link EmoticonProvider} for emoticons images
     * @param emoticonSize     Size of the emoticons in dp
     * @return Modified text.
     */
    public static void replaceWithImages(@NonNull final Context context,
                                         @NonNull final Spannable text,
                                         @NonNull final EmoticonProvider emoticonProvider,
                                         final int emoticonSize) {

        final EmoticonSpan[] existingSpans = text.getSpans(0, text.length(), EmoticonSpan.class);
        final ArrayList<Integer> existingSpanPositions = new ArrayList<>(existingSpans.length);
        for (EmoticonSpan existingSpan : existingSpans)
            existingSpanPositions.add(text.getSpanStart(existingSpan));

        //Get location and unicode of all emoticons.
        final List<EmoticonRange> findAllEmojis = findAllEmoticons(context, text, emoticonProvider);

        //Replace all the emoticons with their relatives.
        for (int i = 0; i < findAllEmojis.size(); i++) {
            final EmoticonRange location = findAllEmojis.get(i);
            if (!existingSpanPositions.contains(location.mStartPos)) {
                text.setSpan(new EmoticonSpan(context, location.mEmoticon.getIcon(), emoticonSize),
                        location.mStartPos,
                        location.mEndPos,
                        Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
        }
    }

    /**
     * Find all the unicodes that represents emoticons and location of starting and ending point of that emoticon.
     *
     * @param context          Instance of caller.
     * @param text             Text to replace
     * @param emoticonProvider {@link EmoticonProvider} for emoticons images
     * @return List of {@link EmoticonRange}.
     * @see EmoticonRange
     */
    @NonNull
    private static List<EmoticonRange> findAllEmoticons(@NonNull final Context context,
                                                        @Nullable final CharSequence text,
                                                        @NonNull final EmoticonProvider emoticonProvider) {
        final List<EmoticonRange> result = new ArrayList<>();

        if (!TextUtils.isEmpty(text)) {
            final Matcher matcher = getRegex(context).matcher(text);
            while (matcher.find()) {
                String unicode = text.subSequence(matcher.start(), matcher.end()).toString();
                if (emoticonProvider.hasEmoticonIcon(unicode)) { //Check if the the emoticon has icon?
                    final Emoticon found = new Emoticon(unicode, emoticonProvider.getIcon(unicode));

                    //Add this emoticon to change list.
                    result.add(new EmoticonRange(matcher.start(), matcher.end(), found));
                }
            }
        }

        return result;
    }


    public static boolean isMessageWithSingleEmoticonOnly(@NonNull final Context context,
                                                          @Nullable final CharSequence text) {
        final List<EmoticonRange> result = new ArrayList<>();

        if (!TextUtils.isEmpty(text)) {

            // Regexp acquired from https://gist.github.com/sergeychilingaryan/94902985a636658496cb69c300bba05f
            String unicode10regexString = "(?:[\\u2700-\\u27bf]|" +

                    "(?:[\\ud83c\\udde6-\\ud83c\\uddff]){2}|" +
                    "[\\ud800\\udc00-\\uDBFF\\uDFFF]|[\\u2600-\\u26FF])[\\ufe0e\\ufe0f]?(?:[\\u0300-\\u036f\\ufe20-\\ufe23\\u20d0-\\u20f0]|[\\ud83c\\udffb-\\ud83c\\udfff])?" +

                    "(?:\\u200d(?:[^\\ud800-\\udfff]|" +

                    "(?:[\\ud83c\\udde6-\\ud83c\\uddff]){2}|" +
                    "[\\ud800\\udc00-\\uDBFF\\uDFFF]|[\\u2600-\\u26FF])[\\ufe0e\\ufe0f]?(?:[\\u0300-\\u036f\\ufe20-\\ufe23\\u20d0-\\u20f0]|[\\ud83c\\udffb-\\ud83c\\udfff])?)*|" +

                    "[\\u0023-\\u0039]\\ufe0f?\\u20e3|\\u3299|\\u3297|\\u303d|\\u3030|\\u24c2|[\\ud83c\\udd70-\\ud83c\\udd71]|[\\ud83c\\udd7e-\\ud83c\\udd7f]|\\ud83c\\udd8e|[\\ud83c\\udd91-\\ud83c\\udd9a]|[\\ud83c\\udde6-\\ud83c\\uddff]|[\\ud83c\\ude01-\\ud83c\\ude02]|\\ud83c\\ude1a|\\ud83c\\ude2f|[\\ud83c\\ude32-\\ud83c\\ude3a]|[\\ud83c\\ude50-\\ud83c\\ude51]|\\u203c|\\u2049|[\\u25aa-\\u25ab]|\\u25b6|\\u25c0|[\\u25fb-\\u25fe]|\\u00a9|\\u00ae|\\u2122|\\u2139|\\ud83c\\udc04|[\\u2600-\\u26FF]|\\u2b05|\\u2b06|\\u2b07|\\u2b1b|\\u2b1c|\\u2b50|\\u2b55|\\u231a|\\u231b|\\u2328|\\u23cf|[\\u23e9-\\u23f3]|[\\u23f8-\\u23fa]|\\ud83c\\udccf|\\u2934|\\u2935|[\\u2190-\\u21ff]";

            final Matcher matcher = Pattern.compile(unicode10regexString, Pattern.UNICODE_CASE).matcher(text);
            while (matcher.find()) {
                String unicode = text.subSequence(matcher.start(), matcher.end()).toString();
                // quick hack
                final Emoticon found = new Emoticon(unicode, R.drawable.emoji_food);
                //Add this emoticon to change list.
                result.add(new EmoticonRange(matcher.start(), matcher.end(), found));
            }
        } else {
            return false;
        }

        return result.size() == 1 && result.get(0).mStartPos == 0 && text.length() == result.get(0).mEndPos;
    }

    /**
     * Load the regex to parse unicode from the shared preference if {@link #sRegexPattern} is not
     * loaded.
     *
     * @param context Instance.
     * @return Regex to find emoticon unicode from string.
     */
    @NonNull
    private static Pattern getRegex(@NonNull final Context context) {
        if (sRegexPattern == null) {
            String regex = readTextFile(context, R.raw.regex);
            sRegexPattern = Pattern.compile(regex);
        }
        return sRegexPattern;
    }

    @NonNull
    private static String readTextFile(@NonNull Context context, int rowResource) {
        InputStream inputStream = context.getResources().openRawResource(rowResource); // getting json
        BufferedReader br = new BufferedReader(new InputStreamReader(inputStream));

        StringBuilder builder = new StringBuilder();
        try {
            String sCurrentLine;
            while ((sCurrentLine = br.readLine()) != null) builder.append(sCurrentLine);
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try {
                inputStream.close();
                br.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return builder.toString();
    }


    /**
     * Range of the emoticons unicode.
     */
    private static final class EmoticonRange {

        /**
         * Start portion of the emoticon in string.
         */
        final int mStartPos;

        /**
         * End portion of the emoticon in string.
         */
        final int mEndPos;

        /**
         * {@link Emoticon}.
         */
        final Emoticon mEmoticon;

        /**
         * Private constructor.
         *
         * @param start    Start portion of the emoticon in string.
         * @param end      End portion of the emoticon in string.
         * @param emoticon {@link Emoticon}
         */
        private EmoticonRange(final int start,
                              final int end,
                              @NonNull final Emoticon emoticon) {
            this.mStartPos = start;
            this.mEndPos = end;
            this.mEmoticon = emoticon;
        }

        @Override
        public boolean equals(final Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            final EmoticonRange that = (EmoticonRange) o;
            return mStartPos == that.mStartPos
                    && mEndPos == that.mEndPos
                    && mEmoticon.equals(that.mEmoticon);
        }

        @Override
        public int hashCode() {
            int result = mStartPos;
            result = 31 * result + mEndPos;
            result = 31 * result + mEmoticon.hashCode();
            return result;
        }
    }
}
