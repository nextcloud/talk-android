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

package com.nextcloud.talk.callbacks;

import android.graphics.Typeface;
import android.text.Editable;
import android.text.Spanned;
import android.text.style.StyleSpan;
import android.text.style.TypefaceSpan;
import com.nextcloud.talk.R;
import com.nextcloud.talk.models.json.mention.Mention;
import com.nextcloud.talk.utils.MagicCharPolicy;
import com.nextcloud.talk.utils.text.Spans;
import com.otaliastudios.autocomplete.AutocompleteCallback;

public class MentionAutocompleteCallback implements AutocompleteCallback<Mention> {
    @Override
    public boolean onPopupItemClicked(Editable editable, Mention item) {
        int[] range = MagicCharPolicy.getQueryRange(editable);
        if (range == null) return false;
        int start = range[0];
        int end = range[1];
        String replacement = item.getLabel();
        editable.replace(start, end, replacement + " ");
        Spans.MentionSpan mentionSpan = new Spans.MentionSpan(Typeface.BOLD, item.getId(), item.getLabel());
        editable.setSpan(mentionSpan, 0, replacement.length(), Spanned.SPAN_INCLUSIVE_EXCLUSIVE);
        return true;
    }

    @Override
    public void onPopupVisibilityChanged(boolean shown) {

    }
}
