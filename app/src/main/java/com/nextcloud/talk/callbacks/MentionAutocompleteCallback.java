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

import android.content.Context;
import android.text.Editable;
import android.text.Spanned;
import android.text.style.DynamicDrawableSpan;
import com.facebook.widget.text.span.BetterImageSpan;
import com.nextcloud.talk.R;
import com.nextcloud.talk.models.database.UserEntity;
import com.nextcloud.talk.models.json.mention.Mention;
import com.nextcloud.talk.utils.DisplayUtils;
import com.nextcloud.talk.utils.MagicCharPolicy;
import com.nextcloud.talk.utils.text.Spans;
import com.otaliastudios.autocomplete.AutocompleteCallback;
import com.vanniktech.emoji.EmojiEditText;

public class MentionAutocompleteCallback implements AutocompleteCallback<Mention> {
    private Context context;
    private UserEntity conversationUser;
    private EmojiEditText emojiEditText;

    public MentionAutocompleteCallback(Context context, UserEntity conversationUser,
                                       EmojiEditText emojiEditText) {
        this.context = context;
        this.conversationUser = conversationUser;
        this.emojiEditText = emojiEditText;
    }

    @Override
    public boolean onPopupItemClicked(Editable editable, Mention item) {
        int[] range = MagicCharPolicy.getQueryRange(editable);
        if (range == null) return false;
        int start = range[0];
        int end = range[1];
        String replacement = item.getLabel();
        editable.replace(start, end, replacement + " ");
        Spans.MentionChipSpan mentionChipSpan =
                new Spans.MentionChipSpan(DisplayUtils.getDrawableForMentionChipSpan(context,
                        item.getId(), item.getLabel(), conversationUser, item.getSource(),
                        R.xml.chip_text_entry, emojiEditText),
                        BetterImageSpan.ALIGN_CENTER,
                        item.getId(), item.getLabel());
        editable.setSpan(mentionChipSpan, start, start + item.getLabel().length(),
                Spanned.SPAN_INCLUSIVE_EXCLUSIVE);
        return true;
    }

    @Override
    public void onPopupVisibilityChanged(boolean shown) {

    }
}
