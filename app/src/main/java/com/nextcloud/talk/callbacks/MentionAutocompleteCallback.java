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

import androidx.emoji2.text.EmojiCompat;
import androidx.emoji2.text.EmojiSpan;

import com.nextcloud.talk.R;
import com.nextcloud.talk.data.user.model.User;
import com.nextcloud.talk.models.json.mention.Mention;
import com.nextcloud.talk.ui.theme.ViewThemeUtils;
import com.nextcloud.talk.utils.DisplayUtils;
import com.nextcloud.talk.utils.CharPolicy;
import com.nextcloud.talk.utils.text.Spans;
import com.otaliastudios.autocomplete.AutocompleteCallback;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
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
        final EmojiCompat emojiCompat = EmojiCompat.get();
        if (emojiCompat.getLoadState() == EmojiCompat.LOAD_STATE_SUCCEEDED) {
            final CharSequence processed = emojiCompat.process(replacement);
            if (processed instanceof Spanned) {
                final Spanned spannedReplacement = (Spanned) processed;
                final EmojiSpan[] emojiSpans =
                    spannedReplacement.getSpans(0, spannedReplacement.length(), EmojiSpan.class);
                final List<EmojiSpan> sortedSpans = new ArrayList<>(Arrays.asList(emojiSpans));
                sortedSpans.sort((a, b) -> spannedReplacement.getSpanStart(b) - spannedReplacement.getSpanStart(a));
                for (EmojiSpan emojiSpan : sortedSpans) {
                    replacementStringBuilder.delete(
                        spannedReplacement.getSpanStart(emojiSpan),
                        spannedReplacement.getSpanEnd(emojiSpan));
                }
            }
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
                         Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);


        return true;
    }

    @Override
    public void onPopupVisibilityChanged(boolean shown) {

    }
}
