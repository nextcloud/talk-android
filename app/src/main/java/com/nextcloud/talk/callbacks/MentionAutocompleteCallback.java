/*
 * Nextcloud Talk application
 *
 * @author Mario Danic
 * @author Andy Scherzinger
 * Copyright (C) 2022 Andy Scherzinger <info@andy-scherzinger.de>
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
import android.widget.EditText;

import third.parties.fresco.BetterImageSpan;
import com.nextcloud.talk.R;
import com.nextcloud.talk.data.user.model.User;
import com.nextcloud.talk.models.json.mention.Mention;
import com.nextcloud.talk.ui.theme.ViewThemeUtils;
import com.nextcloud.talk.utils.DisplayUtils;
import com.nextcloud.talk.utils.MagicCharPolicy;
import com.nextcloud.talk.utils.text.Spans;
import com.otaliastudios.autocomplete.AutocompleteCallback;
import com.vanniktech.emoji.EmojiRange;
import com.vanniktech.emoji.Emojis;

import kotlin.OptIn;

public class MentionAutocompleteCallback implements AutocompleteCallback<Mention> {
    private final ViewThemeUtils viewThemeUtils;
    private Context context;
    private User conversationUser;
    private EditText editText;

    public MentionAutocompleteCallback(Context context,
                                       User conversationUser,
                                       EditText editText,
                                       ViewThemeUtils viewThemeUtils) {
        this.context = context;
        this.conversationUser = conversationUser;
        this.editText = editText;
        this.viewThemeUtils = viewThemeUtils;
    }

    @OptIn(markerClass = kotlin.ExperimentalStdlibApi.class)
    @Override
    public boolean onPopupItemClicked(Editable editable, Mention item) {
        int[] range = MagicCharPolicy.getQueryRange(editable);
        if (range == null) {
            return false;
        }
        int start = range[0];
        int end = range[1];
        String replacement = item.getLabel();

        StringBuilder replacementStringBuilder = new StringBuilder(item.getLabel());
        for (EmojiRange emojiRange : Emojis.emojis(replacement)) {
            replacementStringBuilder.delete(emojiRange.range.getStart(), emojiRange.range.getEndInclusive());
        }

        editable.replace(start, end, replacementStringBuilder + " ");
        Spans.MentionChipSpan mentionChipSpan =
            new Spans.MentionChipSpan(DisplayUtils.getDrawableForMentionChipSpan(context,
                                                                                 item.getId(),
                                                                                 item.getLabel(),
                                                                                 conversationUser,
                                                                                 item.getSource(),
                                                                                 R.xml.chip_you,
                                                                                 editText,
                                                                                 viewThemeUtils),
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
