/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2022 Andy Scherzinger <info@andy-scherzinger.de>
 * SPDX-FileCopyrightText: 2017-2018 Mario Danic <mario@lovelyhq.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.utils;

import android.text.Spannable;
import android.text.Spanned;
import androidx.annotation.Nullable;
import com.otaliastudios.autocomplete.AutocompletePolicy;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class CharPolicy implements AutocompletePolicy {

    private final char character;

    public CharPolicy(char character) {
        this.character = character;
    }

    @Nullable
    public static TextSpan getQueryRange(Spannable text) {
        QuerySpan[] span = text.getSpans(0, text.length(), QuerySpan.class);
        if (span == null || span.length == 0) {
            return null;
        } else  {
            QuerySpan sp = span[0];
            return new TextSpan(text.getSpanStart(sp), text.getSpanEnd(sp));
        }
    }

    private TextSpan checkText(Spannable text, int cursorPos) {
        if (text.length() == 0) {
            return null;
        }

        Pattern pattern = Pattern.compile("@+\\S*", Pattern.CASE_INSENSITIVE | Pattern.MULTILINE);
        Matcher matcher = pattern.matcher(text);

        while (matcher.find()) {
            if (cursorPos >= matcher.start() && cursorPos <= matcher.end() &&
                text.subSequence(matcher.start(), matcher.end()).charAt(0) == character) {
                return new TextSpan(matcher.start(), matcher.end());
            }
        }

        return null;
    }

    public static class TextSpan {
        int start;
        int end;

        public TextSpan(int start, int end) {
            this.start = start;
            this.end = end;
        }

        public int getStart() {
            return start;
        }

        public int getEnd() {
            return end;
        }
    }

    @Override
    public boolean shouldShowPopup(Spannable text, int cursorPos) {
        TextSpan show = checkText(text, cursorPos);
        if (show != null) {
            text.setSpan(new QuerySpan(), show.getStart(), show.getEnd(), Spanned.SPAN_INCLUSIVE_INCLUSIVE);
            return true;
        }
        return false;
    }

    @Override
    public boolean shouldDismissPopup(Spannable text, int cursorPos) {
        return checkText(text, cursorPos) == null;
    }

    @Override
    public CharSequence getQuery(Spannable text) {
        QuerySpan[] span = text.getSpans(0, text.length(), QuerySpan.class);
        if (span == null || span.length == 0) {
            // Should never happen.
            return "";
        }
        QuerySpan sp = span[0];
        return text.subSequence(text.getSpanStart(sp), text.getSpanEnd(sp));
    }

    @Override
    public void onDismiss(Spannable text) {
        // Remove any span added by shouldShow. Should be useless, but anyway.
        QuerySpan[] span = text.getSpans(0, text.length(), QuerySpan.class);
        for (QuerySpan s : span) {
            text.removeSpan(s);
        }
    }

    private static class QuerySpan {
    }
}