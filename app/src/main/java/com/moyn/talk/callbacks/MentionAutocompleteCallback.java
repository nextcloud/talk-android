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

package com.moyn.talk.callbacks;

import android.content.Context;
import android.text.Editable;
import android.text.Spanned;
import android.widget.EditText;

import com.facebook.widget.text.span.BetterImageSpan;
import com.moyn.talk.R;
import com.moyn.talk.models.database.UserEntity;
import com.moyn.talk.models.json.mention.Mention;
import com.moyn.talk.utils.DisplayUtils;
import com.moyn.talk.utils.MagicCharPolicy;
import com.moyn.talk.utils.text.Spans;
import com.otaliastudios.autocomplete.AutocompleteCallback;
import com.vanniktech.emoji.EmojiRange;
import com.vanniktech.emoji.EmojiUtils;

public class MentionAutocompleteCallback implements AutocompleteCallback<Mention> {
    private Context context;
    private UserEntity conversationUser;
    private EditText editText;

    public MentionAutocompleteCallback(Context context, UserEntity conversationUser,
                                       EditText editText) {
        this.context = context;
        this.conversationUser = conversationUser;
        this.editText = editText;
    }

    @Override
    public boolean onPopupItemClicked(Editable editable, Mention item) {
        int[] range = MagicCharPolicy.getQueryRange(editable);
        if (range == null) return false;
        int start = range[0];
        int end = range[1];
        String replacement = item.getLabel();

        StringBuilder replacementStringBuilder = new StringBuilder(item.getLabel());
        for(EmojiRange emojiRange : EmojiUtils.emojis(replacement)) {
            replacementStringBuilder.delete(emojiRange.start, emojiRange.end);
        }

        editable.replace(start, end, replacementStringBuilder.toString() + " ");
        Spans.MentionChipSpan mentionChipSpan =
                new Spans.MentionChipSpan(DisplayUtils.getDrawableForMentionChipSpan(context,
                        item.getId(), item.getLabel(), conversationUser, item.getSource(),
                        R.xml.chip_you, editText),
                        BetterImageSpan.ALIGN_CENTER,
                        item.getId(), item.getLabel());
        editable.setSpan(mentionChipSpan, start, start + replacementStringBuilder.toString().length(),
                Spanned.SPAN_INCLUSIVE_INCLUSIVE);
        return true;
    }

    @Override
    public void onPopupVisibilityChanged(boolean shown) {

    }
}
