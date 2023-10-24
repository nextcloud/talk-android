/*
 * Nextcloud Talk application
 *
 * @author Andy Scherzinger
 * @author Tim Krüger
 * Copyright (C) 2021 Tim Krüger <t@timkrueger.me>
 * Copyright (C) 2021 Andy Scherzinger <info@andy-scherzinger.de>
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
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */

package com.nextcloud.talk.adapters.messages;

import android.text.Spanned;
import android.util.Log;
import android.util.TypedValue;
import android.view.View;
import android.widget.ImageView;
import android.widget.ProgressBar;

import com.google.android.material.card.MaterialCardView;
import com.nextcloud.talk.R;
import com.nextcloud.talk.databinding.ItemCustomOutcomingPreviewMessageBinding;
import com.nextcloud.talk.databinding.ReactionsInsideMessageBinding;
import com.nextcloud.talk.models.json.chat.ChatMessage;
import com.nextcloud.talk.utils.TextMatchers;

import java.util.HashMap;
import java.util.Objects;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.emoji2.widget.EmojiTextView;

public class OutcomingPreviewMessageViewHolder extends PreviewMessageViewHolder {

    private final ItemCustomOutcomingPreviewMessageBinding binding;

    public OutcomingPreviewMessageViewHolder(View itemView) {
        super(itemView, null);
        binding = ItemCustomOutcomingPreviewMessageBinding.bind(itemView);
    }

    @Override
    public void onBind(@NonNull ChatMessage message) {
        super.onBind(message);
        if(!message.isVoiceMessage()
            && !Objects.equals(message.getMessage(), "{file}")
        ) {
            Spanned processedMessageText = null;
            binding.outgoingPreviewMessageBubble.setBackgroundResource(R.drawable.shape_grouped_outcoming_message);
            binding.outgoingPreviewMessageBubble.setOnClickListener(null);
            if (viewThemeUtils != null) {
                processedMessageText = messageUtils.enrichChatMessageText(
                    binding.messageCaption.getContext(),
                    message,
                    false,
                    viewThemeUtils);
                viewThemeUtils.talk.themeOutgoingMessageBubble(binding.outgoingPreviewMessageBubble, true, false);
            }

            if (processedMessageText != null) {
                processedMessageText = messageUtils.processMessageParameters(
                    binding.messageCaption.getContext(),
                    viewThemeUtils,
                    processedMessageText,
                    message,
                    itemView);
            }

            float textSize = 0;
            if (context != null) {
                textSize = context.getResources().getDimension(R.dimen.chat_text_size);
            }
            HashMap<String, HashMap<String, String>> messageParameters = message.getMessageParameters();
            if (
                (messageParameters == null || messageParameters.size() <= 0) &&
                    TextMatchers.isMessageWithSingleEmoticonOnly(message.getText())
            ) {
                textSize = (float)(textSize * IncomingTextMessageViewHolder.TEXT_SIZE_MULTIPLIER);
                itemView.setSelected(true);
            }
            binding.messageCaption.setVisibility(View.VISIBLE);
            binding.messageCaption.setTextSize(TypedValue.COMPLEX_UNIT_PX, textSize);
            binding.messageCaption.setText(processedMessageText);
        } else {
            binding.outgoingPreviewMessageBubble.setBackground(null);
            binding.messageCaption.setVisibility(View.GONE);
        }

        binding.messageText.setTextColor(ContextCompat.getColor(binding.messageText.getContext(),
                                                                R.color.no_emphasis_text));
        binding.messageTime.setTextColor(ContextCompat.getColor(binding.messageText.getContext(),
                                                                R.color.no_emphasis_text));
    }

    @NonNull
    @Override
    public EmojiTextView getMessageText() {
        return binding.messageText;
    }

    @Override
    public ProgressBar getProgressBar() {
        return binding.progressBar;
    }

    @NonNull
    @Override
    public View getPreviewContainer() {
        return binding.previewContainer;
    }

    @NonNull
    @Override
    public MaterialCardView getPreviewContactContainer() {
        return binding.contactContainer;
    }

    @NonNull
    @Override
    public ImageView getPreviewContactPhoto() {
        return binding.contactPhoto;
    }

    @NonNull
    @Override
    public EmojiTextView getPreviewContactName() {
        return binding.contactName;
    }

    @Override
    public ProgressBar getPreviewContactProgressBar() {
        return binding.contactProgressBar;
    }

    @Override
    public ReactionsInsideMessageBinding getReactionsBinding() { return binding.reactions; }

    @NonNull
    @Override
    public EmojiTextView getMessageCaption() { return binding.messageCaption; }
}
