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

import android.text.Spannable;
import android.text.Spanned;
import androidx.annotation.Nullable;
import com.otaliastudios.autocomplete.AutocompletePolicy;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MagicCharPolicy implements AutocompletePolicy {

    private final char character;

    public MagicCharPolicy(char character) {
        this.character = character;
    }

    @Nullable
    public static int[] getQueryRange(Spannable text) {
        QuerySpan[] span = text.getSpans(0, text.length(), QuerySpan.class);
        if (span == null || span.length == 0) return null;
        if (span.length > 1) {
            // Do absolutely nothing
        }
        QuerySpan sp = span[0];
        return new int[]{text.getSpanStart(sp), text.getSpanEnd(sp)};
    }

    private int[] checkText(Spannable text, int cursorPos) {
        if (text.length() == 0) {
            return null;
        }

        int[] span = new int[2];
        Pattern pattern = Pattern.compile("@+\\S*", Pattern.CASE_INSENSITIVE | Pattern.MULTILINE);
        Matcher matcher = pattern.matcher(text);

        while (matcher.find()) {
            if (cursorPos >= matcher.start() && cursorPos <= matcher.end()) {
                span[0] = matcher.start();
                span[1] = matcher.end();
                if (text.subSequence(matcher.start(), matcher.end()).charAt(0) == character) {
                    return span;
                }
            }
        }

        return null;
    }

    @Override
    public boolean shouldShowPopup(Spannable text, int cursorPos) {
        int[] show = checkText(text, cursorPos);
        if (show != null) {
            text.setSpan(new QuerySpan(), show[0], show[1], Spanned.SPAN_INCLUSIVE_INCLUSIVE);
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