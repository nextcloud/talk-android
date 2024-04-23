/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2022 Andy Scherzinger <info@andy-scherzinger.de>
 * SPDX-FileCopyrightText: 2017-2018 Mario Danic <mario@lovelyhq.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.callbacks;

import android.content.Context;
import android.text.Editable;
import android.text.Spanned;
import android.widget.EditText;

import com.nextcloud.talk.R;
import com.nextcloud.talk.data.user.model.User;
import com.nextcloud.talk.models.json.mention.Mention;
import com.nextcloud.talk.ui.theme.ViewThemeUtils;
import com.nextcloud.talk.utils.DisplayUtils;
import com.nextcloud.talk.utils.CharPolicy;
import com.nextcloud.talk.utils.text.Spans;
import com.otaliastudios.autocomplete.AutocompleteCallback;
import com.vanniktech.emoji.EmojiRange;
import com.vanniktech.emoji.Emojis;

import java.util.Objects;

import kotlin.OptIn;
import third.parties.fresco.BetterImageSpan;

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
        CharPolicy.TextSpan range = CharPolicy.getQueryRange(editable);
        if (range == null) {
            return false;
        }
        String replacement = item.getLabel();

        StringBuilder replacementStringBuilder = new StringBuilder(Objects.requireNonNull(item.getLabel()));
        for (EmojiRange emojiRange : Emojis.emojis(replacement)) {
            replacementStringBuilder.delete(emojiRange.range.getStart(), emojiRange.range.getEndInclusive());
        }

        String charSequence = " ";
        editable.replace(range.getStart(), range.getEnd(), charSequence + replacementStringBuilder + " ");
        String id;
        if (item.getMentionId() != null) id = item.getMentionId(); else id = item.getId();
        Spans.MentionChipSpan mentionChipSpan =
            new Spans.MentionChipSpan(DisplayUtils.getDrawableForMentionChipSpan(context,
                                                                                 item.getId(),
                                                                                 item.getRoomToken(),
                                                                                 item.getLabel(),
                                                                                 conversationUser,
                                                                                 item.getSource(),
                                                                                 R.xml.chip_you,
                                                                                 editText,
                                                                                 viewThemeUtils,
                                                                                 "federated_users".equals(item.getSource())),
                                      BetterImageSpan.ALIGN_CENTER,
                                      id, item.getLabel());
        editable.setSpan(mentionChipSpan,
                         range.getStart() + charSequence.length(),
                         range.getStart() + replacementStringBuilder.length() + charSequence.length(),
                         Spanned.SPAN_INCLUSIVE_INCLUSIVE);


        return true;
    }

    @Override
    public void onPopupVisibilityChanged(boolean shown) {

    }
}
