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
            final Matcher matcher = getRegex(context).matcher(text);
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
